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

rootProject.name = "commcare-ios-app"

// Include commcare-core as a composite build
includeBuild("../commcare-core") {
    dependencySubstitution {
        substitute(module("org.commcare:commcare-core")).using(project(":"))
    }
}
