import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
    id("maven-publish")
}

version = rootProject.property("version") ?: "Unknown"
group = "dev.forb"
base.archivesName = project.property("archives_base_name") as String


val targetJavaVersion = 25

java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)

    withSourcesJar()
}


fabricApi {

}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")
    implementation("net.kyori:adventure-platform-fabric:${project.property("kyori_adventure_fabric_version")}")
    include("net.kyori:adventure-platform-fabric:${project.property("kyori_adventure_fabric_version")}")
    implementation(project(":dforblock-core"))
    include(project(":dforblock-core"))
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    from(rootProject.file("dforblock.json5"))

    filesMatching("fabric.mod.json") {
        expand("version" to project.version,
            "minecraft_version" to (project.property("minecraft_version")?: "Failed to get version"),
            "loader_version" to (project.property("loader_version")?: "Failed to get version"),
            "kotlin_loader_version" to (project.property("kotlin_loader_version")?: "Failed to get version")
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
