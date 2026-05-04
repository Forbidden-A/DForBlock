import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.shadow)
}

group = "dev.forb.dforblock"
version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://snapshots.kord.dev")

}

dependencies {
    implementation(libs.slf4j.simple)
    implementation(libs.kord.core)
    implementation(libs.json5)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.luckperms)
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_25
    compilerOptions.javaParameters = true
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}