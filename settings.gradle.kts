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
        // Reown needs four groups that only JitPack hosts. The content filter keeps every
        // other coordinate coming from Google and Maven Central.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.alexzhirkevich")
                includeGroup("com.walletconnect.Scarlet")
                includeGroup("com.github.multiformats")
                includeGroupByRegex("com\\.github\\.komputing.*")
            }
        }
    }
}

rootProject.name = "latifi-android"
include(":app")
