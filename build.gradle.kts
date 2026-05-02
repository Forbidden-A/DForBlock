plugins {
    alias (libs.plugins.kotlin.jvm)
    alias (libs.plugins.kotlin.plugin.serialization)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}