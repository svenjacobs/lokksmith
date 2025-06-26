plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.test.logger.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
}
