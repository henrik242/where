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
    id("io.github.ben-manes.versions.settings") version "0.61.0"
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

// dependencyUpdates fails with parallel execution enabled
if (gradle.startParameter.taskNames.any { it.equals("dependencyUpdates", ignoreCase = true) }) {
    gradle.startParameter.isParallelProjectExecutionEnabled = false
}
