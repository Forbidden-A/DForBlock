package dev.forb.dforblock

import java.util.UUID

sealed class SkinHint {
    data class Minecraft(val uuid: UUID): SkinHint()
    data class Hytale(val id: String): SkinHint()
}