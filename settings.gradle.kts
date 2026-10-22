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

rootProject.name = "Remora"

// Active modules for MVP development
include(":core:model")
// Postponed modules - uncomment when directory is created:
// include(":app")
// include(":core:database")
// include(":core:ai")
// include(":feature:capture")
// include(":feature:ui")
