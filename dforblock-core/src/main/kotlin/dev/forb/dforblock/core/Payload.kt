package dev.forb.dforblock.core

data class BlockyMessagePayload(
    val author: String,
    val messageContent: String,
    val channelName: String = "default",
    val skinHint: SkinHint? = null,
    val prefix: String = "",
    val suffix: String = ""
)

data class DiscordMessagePayload(
    val author: String,
    val content: String,
    val channelId: ULong,
    val messageID: ULong,
    val role: String = "Discord"
)

data class PlayerJoinLeavePayload(
    val playerName: String,
    val skinHint: SkinHint? = null,
    val prefix: String = "",
    val suffix: String = ""
)

data class PlayerDeathPayload(
    val playerName: String,
    val skinHint: SkinHint? = null,
    val deathMessage: String,
    val prefix: String = "",
    val suffix: String = "",
)

data class MCAdvancementMadePayload(
    val playerName: String,
    val skinHint: SkinHint.Minecraft? = null,
    val advancementName: String,
    val advancementDescription: String,
    val type: MCAdvancementType,
    val prefix: String = "",
    val suffix: String = ""
) {
    enum class MCAdvancementType {
        GOAL, CHALLENGE, TASK
    }
}