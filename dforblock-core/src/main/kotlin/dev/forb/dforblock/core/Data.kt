package dev.forb.dforblock.core

data class MinecraftMessageData(
    val player: String,
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
    val role: String = "Discord"
)

data class PlayerJoinLeaveData(
    val playerName: String,
    val skinHint: SkinHint? = null,
    val prefix: String = "",
    val suffix: String = ""
)

data class PlayerDeathData(
    val playerName: String,
    val skinHint: SkinHint? = null,
    val deathMessage: String,
    val prefix: String = "",
    val suffix: String = "",
)

data class MCAdvancementMadeData(
    val playerName: String,
    val skinHint: SkinHint.Minecraft? = null,
    val advancementName: String,
    val advancementDescription: String,
    val prefix: String = "",
    val suffix: String = ""
)