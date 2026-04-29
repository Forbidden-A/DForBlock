package dev.forb.dforblock

import java.util.UUID

sealed class SkinHint {
    data class Minecraft(val uuid: UUID, val username: String): SkinHint()
    data class Hytale(val id: String, val username: String): SkinHint()
}