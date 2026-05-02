package dev.forb.dforblock

data class BlockyMessagePayload(
    val author: String,
    val messageContent: String,
    val channelName: String = "global",
    val skinHint: SkinHint? = null,
)
