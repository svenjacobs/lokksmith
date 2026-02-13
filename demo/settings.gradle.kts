@file:Suppress("UnstableApiUsage")

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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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

include(
    ":androidApp",
    ":composeApp",
)

includeBuild("../lib") {
    dependencySubstitution {
        substitute(module("dev.lokksmith:lokksmith-core")).using(project(":lokksmith-core"))
        substitute(module("dev.lokksmith:lokksmith-compose")).using(project(":lokksmith-compose"))
    }
}
