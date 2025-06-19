plugins {
    id("com.diffplug.spotless")
}

spotless {
    kotlin {
        // Need to specify paths for Multiplatform and Android projects
        target("src/*/kotlin/**/*.kt")
        licenseHeaderFile(rootProject.file("LICENSE_HEADER.txt"))
        ktfmt("0.55").kotlinlangStyle()
    }
}
