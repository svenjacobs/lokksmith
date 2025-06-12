rootProject.name = "demo"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            setUrl("https://central.sonatype.com/repository/maven-snapshots/")
            content { includeGroup("dev.lokksmith") }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        mavenLocal()
    }
}

include(":composeApp")

includeBuild("../lib") {
    dependencySubstitution {
        substitute(module("dev.lokksmith:lokksmith-core")).using(project(":lokksmith-core"))
        substitute(module("dev.lokksmith:lokksmith-android")).using(project(":lokksmith-android"))
    }
}
