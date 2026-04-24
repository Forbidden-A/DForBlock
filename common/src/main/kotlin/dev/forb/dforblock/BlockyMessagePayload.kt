package dev.forb.dforblock

data class BlockyMessagePayload(
    val author: String,
    val content: String,
    val channel: String = "global",
    val luckpermsPrefix: String? = null,
    val skinHint: SkinHint? = null,
)
