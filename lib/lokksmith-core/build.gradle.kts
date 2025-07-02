import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform

plugins {
    id("multiplatform-conventions")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.dokka)
    alias(libs.plugins.poko)
    alias(libs.plugins.maven.publish)
    id("testlogger-conventions")
    id("spotless-conventions")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            //api(libs.kotlinx.collections.immutable)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)

            implementation(libs.androidx.datastore.preferences)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            compileOnly(libs.skydoves.compose.stable.marker)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            api(libs.androidx.activity)
            api(libs.androidx.browser)
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
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

buildConfig {
    packageName("dev.lokksmith")
    useKotlinOutput { internalVisibility = false }
    buildConfigField(
        "VERSION",
        when {
            project.hasProperty("VERSION_NAME") -> project.property("VERSION_NAME") as String
            else -> "SNAPSHOT"
        },
    )
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
