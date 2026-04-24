package dev.forb.dforblock

data class DiscordMessagePayload(
    val author: String,
    val content: String,
    val luckpermsPrefix: String? = null,
    val channel: ULong,
)