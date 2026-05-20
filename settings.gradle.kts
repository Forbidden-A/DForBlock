include(
    ":dforblock-core",
    ":dforblock-hytale",
    ":dforblock-neoforge",
    ":dforblock-fabric",
    ":dforblock-papermc",
    )

rootProject.name = "dforblock"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("libs.versions.toml"))
        }
    }
}

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        gradlePluginPortal()
    }
}