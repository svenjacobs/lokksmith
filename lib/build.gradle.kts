import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.poko) apply false
    alias(libs.plugins.test.logger) apply false
    alias(libs.plugins.ben.manes.versions)
}

tasks.withType<DependencyUpdatesTask> {
    fun isNonStable(version: String) =
        listOf("alpha", "beta", "rc", "eap", "-m", ".m", "dev").any {
            version.lowercase().contains(it)
        }

    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}
