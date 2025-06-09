import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
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
