pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "monagora"

include(":core:identity")
include(":core:objects")
include(":core:storage")
include(":core:storage-android")
include(":core:sync")
include(":core:trust")
include(":app:gps-citoyen")
