package dev.forb.dforblock.core

import java.util.*

sealed class SkinHint {
    data class Minecraft(val uuid: UUID, val username: String) : SkinHint()
    data class Hytale(val id: String, val username: String) : SkinHint()
}