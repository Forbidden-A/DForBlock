plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

version = property("version") as String

allprojects {
    repositories {
        mavenCentral()
        maven("https://snapshots.kord.dev")
    }
}