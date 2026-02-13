import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    android {
        namespace = "dev.lokksmith.android"
        compileSdk { version = release(libs.versions.android.compileSdk.get().toInt()) }
    }

    androidLibrary {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

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
            sourcesJar = SourcesJar.Sources(),
            javadocJar = JavadocJar.Dokka("dokkaGenerate"),
            androidVariantsToPublish = listOf("release"),
        )
    )
}
