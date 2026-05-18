plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

group = "dev.forb.dforblock"
version = rootProject.version

repositories {
    mavenCentral()
}

val serverJarPath = file("libs/HytaleServer.jar")
val projectServerJar = file("server/HytaleServer.jar")
val siblingServerJar = file("../server/HytaleServer.jar")

dependencies {
    // Use HytaleServer.jar from libs folder, or fallback to server folders
    if (serverJarPath.exists()) {
        compileOnly(files(serverJarPath))
    } else if (projectServerJar.exists()) {
        compileOnly(files(projectServerJar))
    } else if (siblingServerJar.exists()) {
        compileOnly(files(siblingServerJar))
    } else {
        compileOnly(files("libs/HytaleServer.jar"))
    }


    // JSR305 annotations (@Nonnull, @Nullable)
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation(project(":dforblock-core", configuration = "shadow"))
    implementation(libs.kotlin.logging)
    implementation(libs.kord.core)
    implementation(libs.kotlin.stdlib)
    implementation(libs.json5)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Exclude server classes from the final JAR
    dependencies {

        exclude { it.moduleGroup == "com.hypixel" }
    }
}

// Disable the default jar task to avoid conflicts with shadowJar
tasks.named("jar") { enabled = false }

tasks.named("build") { dependsOn(tasks.shadowJar) }

// Task to copy server JAR to libs folder if not present
tasks.register("copyServerJar") {
    description = "Task to copy server JAR to libs folder if not present"
    doLast {
        val destJar = file("libs/HytaleServer.jar")
        if (!destJar.exists()) {
            val sources = listOf(file("server/HytaleServer.jar"), file("../server/HytaleServer.jar"))
            for (src in sources) {
                if (src.exists()) {
                    copy {
                        from(src)
                        into("libs")
                    }
                    break
                }
            }
        }
    }
}

tasks.named("compileKotlin") { dependsOn("copyServerJar") }

// Deploy plugin JAR to server mods folder
tasks.register("deployToServer", type = Copy::class) {
    description = "Deploy plugin JAR to server mods folder"
    // Using 'from shadowJar' automatically adds task dependency and proper input tracking
    from(tasks.shadowJar)
    into("server/mods")
    doLast {
        println("Deployed to server/mods/")
    }
}

// Watch for changes and auto-rebuild (useful during development)
tasks.register("watch") {
    description = "Watch for changes and auto-rebuild (useful during development)"
    doLast {
        println("Watching for changes... Press Ctrl+C to stop.")
        println("Run 'gradle build --continuous' for auto-rebuild on file changes.")
    }
}