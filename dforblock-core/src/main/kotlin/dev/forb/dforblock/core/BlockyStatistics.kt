package dev.forb.dforblock.core

import kotlin.time.Instant

sealed class BlockyStatistics {
    data class MinecraftStatistics(
        val targetTps: Double,
        val tps: Double,
        val mspt: Double,
        val onlinePlayers: Int,
        val playerLimit: Int,
        val startup: Instant
    ) : BlockyStatistics()

    data class HytaleStatistics(val onlinePlayers: Int, val playerLimit: Int, val startup: Instant) : BlockyStatistics()
}