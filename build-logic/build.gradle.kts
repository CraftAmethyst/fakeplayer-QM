plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("com.gradleup.shadow:com.gradleup.shadow.gradle.plugin:9.6.1")
    implementation("io.papermc.paperweight.userdev:io.papermc.paperweight.userdev.gradle.plugin:2.0.0-beta.21")
}
