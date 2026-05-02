package dev.forb.dforblock

data class DiscordMessagePayload(
    val author: String,
    val content: String,
    val channelID: ULong,
    val messageID: ULong
)