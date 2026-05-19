plugins {
    alias(libs.plugins.scaffoldit)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

group = "dev.forb"
version = rootProject.version

repositories {
    mavenCentral()
}

val shadowed by configurations.registering
configurations.implementation.get().extendsFrom(shadowed)

repositories {
    maven("https://repo.codemc.io/repository/ArikSquad/")
}

dependencies {
    shadowed(project(mapOf("path" to ":dforblock-core", "configuration" to "shadow")))

    shadowed("eu.mikart.adventure:adventure-platform-hytale:1.0.3")
    shadowed(libs.adventure.legacy)
    shadowed(libs.adventure.text)
    shadowed(libs.adventure.minimessage)

    shadowed("org.slf4j:slf4j-api:2.0.13")
    shadowed("org.slf4j:slf4j-jdk14:2.0.13")
}

tasks.shadowJar {
    configurations = listOf(project.configurations["shadowed"])
    archiveClassifier.set("")
    relocate("eu.mikart.adventure", "dev.forb.dforblock.shadow.adventure")

    mergeServiceFiles()
}

hytale {
    usePatchline("release")
    useVersion("latest")
    dependencies {
        useKotlin()
        compileOnly(libs.kotlin.logging.asString())
        compileOnly(libs.kord.core.asString())
        compileOnly(libs.json5.asString())
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("version", project.version)
    from(rootProject.file("config/core.json5"))
    from(rootProject.file("config/channels.json5"))
    from(rootProject.file("config/permissions.json5"))
    from(rootProject.file("config/messages.json5"))

    filesMatching("manifest.json") {
        expand(
            mapOf(
                "version" to project.version,
                "hytaleServerVersion" to libs.versions.hytale.get()
            )
        )
    }
}

fun Provider<MinimalExternalModuleDependency>.asString(): String =
    get().run { "$group:$name:$version" }