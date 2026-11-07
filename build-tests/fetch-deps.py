#!/usr/bin/env python3
"""Pre-download the Venice Android app's full Gradle dependency closure with curl
(which works through the rotating egress proxy) into a local Maven repo, so the
Gradle build can run fully OFFLINE and never depend on Gradle's HttpClient or a
long-lived proxy password.

Usage: python3 fetch-deps.py
Writes to: ~/workspace/venice-android/local-repo/ (Maven layout)

Seeds: the 4 buildscript plugins (+ their Plugin Portal markers) and every
dependency declared in app/build.gradle.kts (BOM versions resolved from the
Compose BOM POM). Recurses through POM parents, BOM imports and
compile/runtime/provided (non-optional, non-test) dependencies.
"""
import os, re, sys, subprocess, xml.etree.ElementTree as ET
from pathlib import Path

PROJECT = Path.home() / "workspace" / "venice-android"
REPO = PROJECT / "local-repo"
REPOS = [
    ("https://dl.google.com/dl/android/maven2", "google"),
    ("https://repo.maven.apache.org/maven2", "central"),
    ("https://plugins.gradle.org/m2", "portal"),
]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

def curl_get(url, out_path=None, max_time=60):
    cmd = ["curl", "-sS", "-L", "--fail", "--max-time", str(max_time), url]
    if out_path:
        cmd += ["-o", str(out_path)]
        r = subprocess.run(cmd, capture_output=True, text=True)
        return r.returncode == 0
    r = subprocess.run(cmd, capture_output=True, text=True)
    return r.stdout if r.returncode == 0 else None

def group_path(g): return g.replace(".", "/")

def artifact_dir(g, a, v): return REPO / group_path(g) / a / v

def pom_path(g, a, v): return artifact_dir(g, a, v) / f"{a}-{v}.pom"

def fetch_pom(g, a, v, repos=None, max_time=60):
    p = pom_path(g, a, v)
    if p.exists() and p.stat().st_size > 0:
        return p
    p.parent.mkdir(parents=True, exist_ok=True)
    for base, name in (repos or REPOS):
        url = f"{base}/{group_path(g)}/{a}/{v}/{a}-{v}.pom"
        if curl_get(url, p, max_time=max_time):
            print(f"  pom {g}:{a}:{v} <- {name}")
            return p
    p.unlink(missing_ok=True)
    if repos is None:
        print(f"  !! POM NOT FOUND {g}:{a}:{v}")
    return None

def fetch_artifact(g, a, v, packaging, repos=None, max_time=60):
    d = artifact_dir(g, a, v)
    d.mkdir(parents=True, exist_ok=True)
    exts = []
    if packaging == "aar": exts = ["aar", "jar"]
    elif packaging == "jar": exts = ["jar"]
    elif packaging == "pom": exts = []
    else: exts = ["jar", "aar"]
    for ext in exts:
        f = d / f"{a}-{v}.{ext}"
        if f.exists() and f.stat().st_size > 0:
            return f
        for base, name in (repos or REPOS):
            url = f"{base}/{group_path(g)}/{a}/{v}/{a}-{v}.{ext}"
            if curl_get(url, f, max_time=max_time):
                print(f"  {ext} {g}:{a}:{v} ({f.stat().st_size//1024}KB) <- {name}")
                return f
        f.unlink(missing_ok=True)
    if packaging not in ("pom",):
        print(f"  !! ARTIFACT NOT FOUND {g}:{a}:{v} (packaging={packaging})")
    return None

def resolve_props(text, props):
    def repl(m):
        key = m.group(1)
        return props.get(key, m.group(0))
    for _ in range(5):
        new = re.sub(r"\$\{([^}]+)\}", repl, text)
        if new == text: break
        text = new
    return text

def parse_pom(path):
    tree = ET.parse(path)
    root = tree.getroot()
    def tag(el, name):
        e = el.find(f"m:{name}", NS)
        return e.text.strip() if e is not None and e.text else None
    props = {}
    parent = root.find("m:parent", NS)
    parent_coord = None
    if parent is not None:
        parent_coord = (tag(parent, "groupId"), tag(parent, "artifactId"), tag(parent, "version"))
    props_el = root.find("m:properties", NS)
    if props_el is not None:
        for c in props_el:
            if c.text:
                props[c.tag.split("}")[-1]] = c.text.strip()
    # group/artifact/version may be inherited from parent
    g = tag(root, "groupId") or (parent_coord[0] if parent_coord else None)
    a = tag(root, "artifactId")
    v = tag(root, "version") or (parent_coord[2] if parent_coord else None)
    packaging = tag(root, "packaging") or "jar"
    return {"group": g, "artifact": a, "version": v, "packaging": packaging,
            "parent": parent_coord, "props": props, "root": root}

def collect_props(coord, seen=None):
    """Merge properties from a POM and its ancestors (child wins)."""
    seen = seen or set()
    g, a, v = coord
    if coord in seen: return {}
    seen.add(coord)
    p = fetch_pom(g, a, v)
    if p is None: return {}
    info = parse_pom(p)
    merged = {}
    if info["parent"]:
        merged.update(collect_props(info["parent"], seen))
    merged.update(info["props"])
    merged["project.groupId"] = info["group"] or g
    merged["project.artifactId"] = info["artifact"] or a
    merged["project.version"] = info["version"] or v
    return merged

visited = set()
missing = []

def process(coord, depth=0, quiet=False, repos=None, max_time=60):
    g, a, v = coord
    if not all(coord) or coord in visited:
        return
    visited.add(coord)
    p = fetch_pom(g, a, v, repos=repos, max_time=max_time)
    if p is None:
        if not quiet:
            missing.append(coord)
        return
    info = parse_pom(p)
    props = collect_props(coord)
    # parent POM first (its deps may matter)
    if info["parent"]:
        process(info["parent"], depth + 1)
    # BOM imports + managed versions
    dm = info["root"].find("m:dependencyManagement/m:dependencies", NS)
    managed = {}
    if dm is not None:
        for d in dm.findall("m:dependency", NS):
            dg = d.findtext("m:groupId", namespaces=NS)
            da = d.findtext("m:artifactId", namespaces=NS)
            dv = d.findtext("m:version", namespaces=NS)
            scope = d.findtext("m:scope", namespaces=NS)
            dtype = d.findtext("m:type", namespaces=NS)
            if dv: dv = resolve_props(dv.strip(), props)
            if dg and da:
                managed[(dg.strip(), da.strip())] = dv
            if scope == "import" and dtype == "pom" and dv and dv != "${project.version}":
                process((dg.strip(), da.strip(), dv), depth + 1)
    # dependencies
    deps = info["root"].find("m:dependencies", NS)
    if deps is not None:
        for d in deps.findall("m:dependency", NS):
            dg = d.findtext("m:groupId", namespaces=NS)
            da = d.findtext("m:artifactId", namespaces=NS)
            dv = d.findtext("m:version", namespaces=NS)
            scope = d.findtext("m:scope", namespaces=NS) or "compile"
            optional = d.findtext("m:optional", namespaces=NS) == "true"
            if not dg or not da: continue
            dg = resolve_props(dg.strip(), props)
            da = resolve_props(da.strip(), props)
            if scope in ("test",) or optional: continue
            if dv:
                dv = resolve_props(dv.strip(), props)
            else:
                dv = managed.get((dg, da))
            # bracketed exact version like [1.6.0] means exactly that version
            if dv and re.fullmatch(r"\[([^\],]+)\]", dv.strip()):
                dv = re.match(r"\[([^\],]+)\]", dv.strip()).group(1)
            if not dv or dv.startswith("${") or "[" in dv or "(" in dv:
                # unresolvable version (range or unknown property) - record and skip
                print(f"  ?? skip {dg}:{da} version={dv!r}")
                continue
            process((dg, da, dv), depth + 1)
    fetch_artifact(g, a, v, info["packaging"], repos=repos, max_time=max_time)
    # Android libraries declare their real dependency graph in the -android
    # variant POM (the main POM is often near-empty). Follow it when present.
    # Only AndroidX/Google artifacts have -android variants, and only Google
    # Maven hosts them — a single quick probe, never the slow 3-repo sweep.
    if info["packaging"] == "aar" and (g.startswith("androidx.") or g.startswith("com.android")):
        process((g, a + "-android", v), depth + 1, quiet=True,
                repos=[REPOS[0]], max_time=15)

def main():
    seeds = [
        # plugin markers (Plugin Portal) + plugin artifacts
        ("com.android.application", "com.android.application.gradle.plugin", "8.2.2"),
        ("org.jetbrains.kotlin.android", "org.jetbrains.kotlin.android.gradle.plugin", "1.9.22"),
        ("org.jetbrains.kotlin.plugin.serialization", "org.jetbrains.kotlin.plugin.serialization.gradle.plugin", "1.9.22"),
        ("com.google.devtools.ksp", "com.google.devtools.ksp.gradle.plugin", "1.9.22-1.0.17"),
        ("com.android.tools.build", "gradle", "8.2.2"),
        ("org.jetbrains.kotlin", "kotlin-gradle-plugin", "1.9.22"),
        ("org.jetbrains.kotlin", "kotlin-serialization", "1.9.22"),
        ("com.google.devtools.ksp", "symbol-processing-gradle-plugin", "1.9.22-1.0.17"),
        # Compose BOM (platform) - its POM pins all compose versions
        ("androidx.compose", "compose-bom", "2024.02.01"),
        # app dependencies (exact versions from app/build.gradle.kts)
        ("androidx.core", "core-ktx", "1.12.0"),
        ("androidx.lifecycle", "lifecycle-runtime-ktx", "2.7.0"),
        ("androidx.lifecycle", "lifecycle-runtime-compose", "2.7.0"),
        ("androidx.lifecycle", "lifecycle-viewmodel-compose", "2.7.0"),
        ("androidx.activity", "activity-compose", "1.8.2"),
        ("androidx.room", "room-runtime", "2.6.1"),
        ("androidx.room", "room-ktx", "2.6.1"),
        ("androidx.room", "room-compiler", "2.6.1"),
        ("androidx.security", "security-crypto", "1.1.0-alpha06"),
        ("com.squareup.okhttp3", "okhttp", "4.12.0"),
        ("com.squareup.okhttp3", "okhttp-sse", "4.12.0"),
        ("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.6.3"),
        ("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.7.3"),
        ("io.coil-kt", "coil-compose", "2.6.0"),
        # compose libs (versions pinned by the BOM, resolved via its POM)
        ("androidx.compose.ui", "ui", None),
        ("androidx.compose.ui", "ui-graphics", None),
        ("androidx.compose.ui", "ui-tooling-preview", None),
        ("androidx.compose.ui", "ui-tooling", None),
        ("androidx.compose.material3", "material3", None),
        ("androidx.compose.material", "material-icons-extended", None),
        ("androidx.compose.animation", "animation", None),
    ]
    # resolve BOM-pinned compose versions first
    bom_props = collect_props(("androidx.compose", "compose-bom", "2024.02.01"))
    bom_pom = fetch_pom("androidx.compose", "compose-bom", "2024.02.01")
    bom_managed = {}
    if bom_pom:
        info = parse_pom(bom_pom)
        dm = info["root"].find("m:dependencyManagement/m:dependencies", NS)
        if dm is not None:
            for d in dm.findall("m:dependency", NS):
                dg = d.findtext("m:groupId", namespaces=NS); da = d.findtext("m:artifactId", namespaces=NS)
                dv = d.findtext("m:version", namespaces=NS)
                if dg and da and dv:
                    bom_managed[(dg.strip(), da.strip())] = resolve_props(dv.strip(), bom_props)
    resolved = []
    for g, a, v in seeds:
        if v is None:
            v = bom_managed.get((g, a))
            if not v:
                print(f"!! no BOM version for {g}:{a}, skipping"); continue
            print(f"  BOM pins {g}:{a} = {v}")
        resolved.append((g, a, v))
    for coord in resolved:
        print(f"== {coord[0]}:{coord[1]}:{coord[2]}")
        process(coord)
    print(f"\nDONE: {len(visited)} artifacts visited, {len(missing)} missing POMs")
    for m in missing: print("  MISSING:", m)

if __name__ == "__main__":
    main()
