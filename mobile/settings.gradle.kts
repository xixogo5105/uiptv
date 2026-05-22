pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal {
            content {
                includeGroup("com.uiptv.thirdparty")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "uiptv-mobile"

include(":shared")
include(":androidApp")
