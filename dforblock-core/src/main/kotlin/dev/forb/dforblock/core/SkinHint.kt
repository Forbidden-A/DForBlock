package dev.forb.dforblock.core

import dev.forb.dforblock.core.config.ConfigManager

sealed class SkinHint {
    abstract val username: String
    abstract val uuid: String

    data class Minecraft(override val uuid: String, override val username: String) : SkinHint()
    data class Hytale(override val uuid: String, override val username: String) : SkinHint()
}

fun SkinHint.build(configManager: ConfigManager): String? = when (this) {
    is SkinHint.Minecraft -> configManager.core.minecraftAvatarProviderUrl?.replace("{uuid}", uuid)?.replace("{username}", username)
    is SkinHint.Hytale -> configManager.core.hytaleAvatarProviderUrl?.replace("{uuid}", uuid)?.replace("{username}", username)
}