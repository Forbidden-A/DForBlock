package dev.forb.dforblock.core

import kotlin.time.Instant

sealed class BlockyStatistics() {
    abstract val onlinePlayers: Int
    abstract val playerLimit: Int
    abstract val startup: Instant

    data class MinecraftStatistics(
        val targetTps: Double,
        val tps: Double,
        val mspt: Double,
        override val onlinePlayers: Int,
        override val playerLimit: Int,
        override val startup: Instant
    ) : BlockyStatistics()

    data class HytaleStatistics(override val onlinePlayers: Int, override val playerLimit: Int, override val startup: Instant) : BlockyStatistics()
}