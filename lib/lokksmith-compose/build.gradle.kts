import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    // Compose's `checkComposeUiTestConfigurationForWasmJs` requires an executable binary so that
    // webpack bundles the Skiko runtime for browser tests. See https://youtrack.jetbrains.com/issue/CMP-4906
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        binaries.executable()
    }

    android {
        namespace = "dev.lokksmith.compose.android"
        compileSdk { version = release(libs.versions.android.compileSdk.get().toInt()) }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":lokksmith-core"))
            api(libs.compose.multiplatform.ui)
            api(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.compose.multiplatform.ui.tooling.preview)
        }

        androidMain.dependencies {
            api(libs.androidx.activity.compose)
            api(libs.androidx.browser)
            implementation(libs.compose.multiplatform.ui.tooling.preview)
        }

        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
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
