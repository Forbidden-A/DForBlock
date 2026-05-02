package dev.forb.dforblock

sealed class BlockyStatistics {
    data class MinecraftStatistics(val targetTps: Double, val tps: Double, val mspt: Double, val onlinePlayers: Int, val playerLimit: Int): BlockyStatistics()
    data class HytaleStatistics(val onlinePlayers: Int, val playerLimit: Int): BlockyStatistics()
}