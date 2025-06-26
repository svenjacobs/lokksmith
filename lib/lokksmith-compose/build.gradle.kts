import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform

plugins {
    id("multiplatform-conventions")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
    id("kotlin-parcelize")
    id("testlogger-conventions")
    id("spotless-conventions")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":lokksmith-core"))
            api(compose.ui)
            implementation(compose.components.uiToolingPreview)
        }

        androidMain.dependencies {
            api(libs.androidx.activity.compose)
            api(libs.androidx.browser)
            implementation(compose.preview)
        }
    }
}

android {
    namespace = "dev.lokksmith.android"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            sourcesJar = true,
            javadocJar = JavadocJar.Dokka("dokkaGenerate"),
            androidVariantsToPublish = listOf("release"),
        )
    )
}
