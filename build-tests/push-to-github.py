#!/usr/bin/env python3
"""Push the Venice Android app source to GitHub (vivmuk/venice-android) as a
single commit via the Git Data API, using the stored GitHub credential through
the connector surrogate (the token value never reaches this script's output).

Usage: python3 push-to-github.py
Reads the staged file list from `git diff --cached --name-only` in the repo dir.
"""
import base64, json, os, subprocess, sys, urllib.request, urllib.error

sys.path.insert(0, "/opt/muse/skills/custom-connector-setup/bin")
from dynamic_credentials import add_surrogate_to_request

OWNER, REPO, BRANCH = "vivmuk", "venice-android", "main"
PROJECT = os.path.expanduser("~/workspace/venice-android")
API = "https://api.github.com"

def api(method, path, body=None):
    url = API + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Accept", "application/vnd.github+json")
    if data:
        req.add_header("Content-Type", "application/json")
    add_surrogate_to_request(req, "custom.github", entry_name="access_token",
                             allowed_hosts=["api.github.com"])
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        print(f"{method} {path} -> HTTP {e.code}: {e.read().decode()[:800]}")
        raise

def main():
    os.chdir(PROJECT)
    files = subprocess.run(["git", "ls-files"],
                           capture_output=True, text=True, check=True).stdout.split()
    files = [f for f in files if os.path.isfile(f)]
    print(f"pushing {len(files)} files to {OWNER}/{REPO}@{BRANCH}")
    ref = api("GET", f"/repos/{OWNER}/{REPO}/git/ref/heads/{BRANCH}")
    base_sha = ref["object"]["sha"]
    base_commit = api("GET", f"/repos/{OWNER}/{REPO}/git/commits/{base_sha}")
    base_tree = base_commit["tree"]["sha"]
    print("base commit:", base_sha[:8], "tree:", base_tree[:8])
    tree_entries = []
    for f in files:
        with open(f, "rb") as fh:
            content = base64.b64encode(fh.read()).decode()
        blob = api("POST", f"/repos/{OWNER}/{REPO}/git/blobs",
                   {"content": content, "encoding": "base64"})
        tree_entries.append({"path": f, "mode": "100644", "type": "blob",
                             "sha": blob["sha"]})
        print(f"  blob {f} ({blob['sha'][:8]})")
    tree = api("POST", f"/repos/{OWNER}/{REPO}/git/trees",
               {"base_tree": base_tree, "tree": tree_entries})
    print("tree:", tree["sha"][:8])
    commit = api("POST", f"/repos/{OWNER}/{REPO}/git/commits",
                 {"message": "Venice Android app: ChatGPT-style Venice client (chat, images, voice)\n\n- Kotlin + Jetpack Compose (Material 3), minSdk 26 / targetSdk 34\n- Chat with streaming SSE, image generation, voice (STT/TTS)\n- API key entered in Settings, stored in EncryptedSharedPreferences\n- Room persistence, Coil image loading\n- Offline-build support: local Maven repo + fetch-deps.py (build-tests/)\n- Verified: assembleDebug builds, app-debug.apk (18MB, ai.venice.app 1.0.0)",
                  "tree": tree["sha"], "parents": [base_sha]})
    print("commit:", commit["sha"][:8])
    updated = api("PATCH", f"/repos/{OWNER}/{REPO}/git/refs/heads/{BRANCH}",
                  {"sha": commit["sha"]})
    print("main now at:", updated["object"]["sha"][:8])
    print("PUSHED: https://github.com/vivmuk/venice-android/commit/" + commit["sha"])

if __name__ == "__main__":
    main()
