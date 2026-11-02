// Desktop (Linux / Windows) build of Apparat. Standalone so the Android build is untouched.
// Run from the repo root:  ./gradlew -p desktop run
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
rootProject.name = "ApparatDesktop"
include(":engine", ":app")
