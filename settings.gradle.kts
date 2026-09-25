pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "picktrace-challenge"

include(":app")
include(":core:model")
include(":core:database")
include(":core:network")
include(":core:sync")
include(":core:data")
include(":core:designsystem")
include(":core:testing")
include(":feature:capture")
include(":feature:events")
