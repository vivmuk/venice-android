pluginManagement {
    repositories {
        // Local pre-downloaded repo (offline builds in the sandbox; harmless
        // elsewhere since Gradle skips a missing local dir).
        maven { url = uri("local-repo") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("local-repo") }
        google()
        mavenCentral()
    }
}

rootProject.name = "Venice"
include(":app")
