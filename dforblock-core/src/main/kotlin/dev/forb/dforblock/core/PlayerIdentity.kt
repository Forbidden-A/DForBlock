package dev.forb.dforblock.core

import dev.forb.dforblock.core.config.ConfigManager
import java.util.UUID

sealed class PlayerIdentity {

    abstract val name: String
    abstract val uuid: UUID
    abstract val displayName: String?

    fun qualifiedName(configManager: ConfigManager, communicator: IBlockyCommunicator): String {
        if (communicator.isLuckperms)
            return luckPermsQualifiedName(configManager.core.playerQualifier, uuid, name, displayName).withoutMinecraftFormatting()
        return configManager.core.playerQualifier.withPlaceholders(
            "{playerName}" to name,
            "{playerQualifiedName}" to (displayName ?: name).withoutMinecraftFormatting(),
            "{playerUuid}" to uuid.toString(),
            "{prefix}" to "",
            "{suffix}" to ""
        )
    }

    data class Minecraft(override val uuid: UUID, override val name: String, override val displayName: String?) : PlayerIdentity()
    data class Hytale(override val uuid: UUID, override val name: String, override val displayName: String?) : PlayerIdentity()
}

fun PlayerIdentity.buildAvatarUrl(configManager: ConfigManager): String? = when (this) {
    is PlayerIdentity.Minecraft -> configManager.core.minecraftAvatarProviderUrl
    is PlayerIdentity.Hytale -> configManager.core.hytaleAvatarProviderUrl
}?.withPlaceholders("{username}" to name, "{uuid}" to uuid.toString())