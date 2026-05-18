include(
    ":dforblock-core",
    ":dforblock-fabric",
    ":dforblock-neoforge",
    ":dforblock-papermc",
    ":dforblock-hytale",
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