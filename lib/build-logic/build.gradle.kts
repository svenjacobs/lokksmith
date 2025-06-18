plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.test.logger.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
}
