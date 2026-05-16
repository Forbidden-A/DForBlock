package dev.forb.dforblock.core

import dev.forb.dforblock.core.config.ConfigManager
import java.util.UUID
import kotlin.time.Instant

data class GameMessageData(
    val playerIdentity: PlayerData,
    val messageContent: String,
    val channelName: String = "default",
)

data class DiscordMessageData(
    val author: String,
    val content: String,
    val channelId: ULong,
    val messageID: ULong,
    val role: String = "Discord",
    val isAttachment: Boolean = false,
    val attachmentLink: String? = null
)

sealed class PlayerData {
    abstract val name: String
    abstract val uuid: UUID
    abstract val displayName: String?

    fun qualifiedName(configManager: ConfigManager, communicator: IBlockyCommunicator): String {
        if (communicator.isLuckperms) {
            return luckPermsQualifiedName(configManager.core.playerQualifier, uuid, name, displayName).withoutMinecraftFormatting()
        }

        val placeholders = mapOf(
            "{playerName}" to name,
            "{playerQualifiedName}" to (displayName ?: name).withoutMinecraftFormatting(),
            "{playerUuid}" to uuid.toString(),
            "{prefix}" to "",
            "{suffix}" to ""
        )

        return configManager.core.playerQualifier.withPlaceholders(placeholders)
    }
    fun buildAvatarUrl(configManager: ConfigManager): String? = when (this) {
        is Hytale -> configManager.core.minecraftAvatarProviderUrl
        is Minecraft -> configManager.core.minecraftAvatarProviderUrl
    }?.withPlaceholders("{username}" to name, "{uuid}" to uuid.toString())

    data class Minecraft(override val uuid: UUID, override val name: String, override val displayName: String?) : PlayerData()
    data class Hytale(override val uuid: UUID, override val name: String, override val displayName: String?) : PlayerData()
}

data class PlayerJoinLeaveData(
    val playerIdentity: PlayerData,
)

data class PlayerDeathData(
    val playerIdentity: PlayerData,
    val deathMessage: String,
)

data class MCAdvancementMadeData(
    val playerIdentity: PlayerData.Minecraft,
    val advancementName: String,
    val advancementDescription: String,
    val advancementType: String,
    val actionType: String,
)

data class GameStatistics(
    val gameType: GameType,
    val onlinePlayers: Int,
    val playerLimit: Int,
    val startup: Instant,
    val gameVersion: String,
    val targetTps: Double,
    val tps: Double,
    val mspt: Double,
) {
    enum class GameType{
        Minecraft, Hytale
    }
}