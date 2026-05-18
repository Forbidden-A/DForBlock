plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

val minecraftVersion: String by rootProject

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

version = rootProject.version

val shadowed by configurations.creating
configurations.implementation.get().extendsFrom(shadowed)

dependencies {
    shadowed(project(":dforblock-core"))
    shadowed(libs.kotlin.logging)
    shadowed(libs.kord.core)
    shadowed(libs.kotlin.stdlib)
    shadowed(libs.json5)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.legacy)
    compileOnly(libs.adventure.text)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.paper.api)
    compileOnly("org.apache.logging.log4j:log4j-core:2.25.2")
}

tasks.shadowJar {
    configurations = listOf(project.configurations["shadowed"])
    archiveClassifier.set("")

    relocate("io.ktor", "dev.forb.dforblock.shadow.ktor")
    relocate("dev.kord", "dev.forb.dforblock.shadow.kord")
    relocate("kotlin", "dev.forb.dforblock.shadow.kotlin")
    relocate("kotlinx", "dev.forb.dforblock.shadow.kotlinx")

    mergeServiceFiles()
}

kotlin {
    jvmToolchain(25)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(minecraftVersion)
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }

    processResources {
        val props = mapOf("version" to rootProject.version , "description" to project.description )
        filesMatching("plugin.yml") {
            expand(props)
        }

        from(rootProject.file("config/core.json5"))
        from(rootProject.file("config/channels.json5"))
        from(rootProject.file("config/permissions.json5"))
        from(rootProject.file("config/messages.json5"))
    }
}
