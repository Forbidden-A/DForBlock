import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias (libs.plugins.shadow)
}

group = "dev.forb.dforblock"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://snapshots.kord.dev")

}

dependencies {
    implementation(libs.slf4j.simple)
    implementation(libs.kord.core)
    compileOnly(libs.luckperms)
    compileOnly(libs.kyori.api)
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