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
    // Resolves (and in CI downloads) the JDK 25 toolchain the desktop target compiles on.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Where"
include(":app")
include(":shared")
include(":desktop")

// dependencyUpdates fails with parallel execution enabled
if (gradle.startParameter.taskNames.any { it.equals("dependencyUpdates", ignoreCase = true) }) {
    gradle.startParameter.isParallelProjectExecutionEnabled = false
}
