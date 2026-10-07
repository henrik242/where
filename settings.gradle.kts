pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("io.github.ben-manes.versions.settings") version "0.64.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Interim forked maplibre-compose (0.19.0 + connectivity override #1735), published to this
        // fork's GitHub Packages as 0.19.0-where1 until the upstream release ships. See OfflineMapGate
        // / MapConnectivity. Needs a GitHub token with read:packages, from gpr.user/gpr.key in
        // ~/.gradle/gradle.properties or the GITHUB_ACTOR/GITHUB_TOKEN env. Scoped to this one version.
        maven {
            name = "WhereForkPackages"
            url = uri("https://maven.pkg.github.com/henrik242/maplibre-compose")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("MAPLIBRE_FORK_USER") ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("MAPLIBRE_FORK_TOKEN") ?: System.getenv("GITHUB_TOKEN")
            }
            content { includeVersionByRegex("org\\.maplibre\\.compose", ".*", "0\\.19\\.0-where1") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "Where"
include(":app")
include(":shared")

// dependencyUpdates fails with parallel execution enabled
if (gradle.startParameter.taskNames.any { it.equals("dependencyUpdates", ignoreCase = true) }) {
    gradle.startParameter.isParallelProjectExecutionEnabled = false
}
