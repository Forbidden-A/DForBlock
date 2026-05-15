package dev.forb.dforblock.core

import java.util.UUID
import kotlin.time.Instant

data class GameMessageData(
    val playerName: String,
    val playerUuid: String,
    val messageContent: String,
    val channelName: String = "default",
    val skinHint: SkinHint? = null,
    val prefix: String = "",
    val suffix: String = ""
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
    val playerName: String,
    val playerUuid: String,
    val skinHint: SkinHint? = null,
    val prefix: String = "",
    val suffix: String = ""
)

data class PlayerDeathData(
    val playerName: String,
    val playerUuid: String,
    val skinHint: SkinHint? = null,
    val deathMessage: String,
    val prefix: String = "",
    val suffix: String = "",
)

data class MCAdvancementMadeData(
    val playerName: String,
    val playerUuid: String,
    val skinHint: SkinHint.Minecraft? = null,
    val advancementName: String,
    val advancementDescription: String,
    val prefix: String = "",
    val suffix: String = ""
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