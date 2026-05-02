package dev.forb.dforblock

data class DiscordMessagePayload(
    val author: String,
    val content: String,
    val channelId: ULong,
    val messageID: ULong
)