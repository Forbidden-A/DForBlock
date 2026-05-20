import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

evaluationDependsOn(":dforblock-core")

plugins {
    kotlin("jvm")
    id("net.fabricmc.fabric-loom")
    id("maven-publish")
}

version = rootProject.version as String
group = "dev.forb"
base.archivesName = project.property("archives_base_name") as String


val targetJavaVersion = 25

java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}


fabricApi {

}

val minecraftVersion: String by rootProject
val loaderVersion: String by project
val fabricApiVersion: String by project
val kotlinLoaderVersion: String by project
val kyoriAdventureFabricVersion: String by project

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://snapshots.kord.dev")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("net.fabricmc:fabric-language-kotlin:$kotlinLoaderVersion")
    include(implementation("net.kyori:adventure-platform-fabric:${kyoriAdventureFabricVersion}")) { }
    include(implementation(libs.adventure.legacy.get())) { }
    include(implementation(libs.adventure.text.get())) { }
    include(implementation(project(":dforblock-core", configuration = "shadow"))) { }
    compileOnly(libs.kotlin.logging)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", loaderVersion)
    filteringCharset = "UTF-8"

    from(rootProject.file("config/core.json5"))
    from(rootProject.file("config/channels.json5"))
    from(rootProject.file("config/permissions.json5"))
    from(rootProject.file("config/messages.json5"))

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to minecraftVersion,
            "loader_version" to loaderVersion,
            "kotlin_loader_version" to kotlinLoaderVersion
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
