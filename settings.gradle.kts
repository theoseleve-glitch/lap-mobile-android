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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "lap-mobile-android"

include(":app")

// Shared schema types live outside this repo at ../lap-schemas/kotlin; we
// include them as a local Gradle module instead of vendoring generated code
// (see lap-mobile-android/CLAUDE.md "No duplicate Pydantic-equivalent types").
include(":schemas")
project(":schemas").projectDir = file("../lap-schemas/kotlin")
