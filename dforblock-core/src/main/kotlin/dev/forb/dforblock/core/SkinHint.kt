package dev.forb.dforblock.core

import java.util.*

sealed class SkinHint {
    data class Minecraft(val uuid: String, val username: String) : SkinHint()
    data class Hytale(val uuid: String, val username: String) : SkinHint()
}