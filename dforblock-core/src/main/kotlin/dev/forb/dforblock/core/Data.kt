package dev.forb.dforblock.core

import kotlin.time.Instant

data class GameMessageData(
    val playerIdentity: PlayerIdentity,
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

data class PlayerJoinLeaveData(
    val playerIdentity: PlayerIdentity,
)

data class PlayerDeathData(
    val playerIdentity: PlayerIdentity,
    val deathMessage: String,
)

data class MCAdvancementMadeData(
    val playerIdentity: PlayerIdentity.Minecraft,
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