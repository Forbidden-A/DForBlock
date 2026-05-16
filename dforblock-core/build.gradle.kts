import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.shadow)
}

group = "dev.forb.dforblock"
version = rootProject.version as String

repositories {
    mavenCentral()
    maven("https://snapshots.kord.dev")

}

val shadowed by configurations.registering
configurations.implementation.extendsFrom(shadowed)

dependencies {
    shadowed(libs.kotlin.logging)
    shadowed(libs.kord.core) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
    }
    shadowed(libs.json5)

    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.legacy)
    compileOnly(libs.adventure.text)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.luckperms)
    compileOnly(libs.kotlinx.coroutines)
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_25
    compilerOptions.javaParameters = true
}

tasks.shadowJar {
    configurations = listOf(project.configurations["shadowed"])
    archiveClassifier.set("shadow")

    relocate("io.ktor", "dev.forb.dforblock.shadow.ktor")
    relocate("dev.kord", "dev.forb.dforblock.shadow.kord")

}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}