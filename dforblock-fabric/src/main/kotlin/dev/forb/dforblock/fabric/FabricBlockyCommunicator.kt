package dev.forb.dforblock.fabric

import dev.forb.dforblock.core.*
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class FabricBlockyCommunicator(val mod: DForBlockFabric) : IBlockyCommunicator {

    override fun getConfigFile(): File = configPath.toFile()

    override fun ensureConfigFile(): Boolean {
        try {
            configDir.createDirectories()
        } catch (e: Exception) {
            LOGGER.error { e.stackTraceToString() }
        }
        if (!configPath.exists()) {
            val resourceStream = DForBlockFabric::class.java.getResourceAsStream("/dforblock.json5")
            if (resourceStream != null) {
                Files.copy(resourceStream, configPath)
                resourceStream.close()
                LOGGER.warn { "========================================" }
                LOGGER.warn { "Created config file, please restart after configuring it correctly." }
                LOGGER.warn { "========================================" }
            } else
                LOGGER.error { "Unexpected state, 'config file does not exist', please ensure mod jar is unmodified." }
            return false
        }
        return true
    }

    override fun broadcastMessage(
        payload: DiscordMessagePayload,
        config: DForBlockConfig
    ) {
        val kyoriComponent = prepareMinecraftMiniMessage(payload, config)
        mod.adventure?.players()?.sendMessage(kyoriComponent)
            ?: return LOGGER.error { "Unexpected state, 'adventure is null', please report this.." }
    }

    override fun onlinePlayers(): Set<String> =
        mod.minecraftServer?.playerList?.players?.map { it.displayName.toString() }?.toSet()
            ?: emptySet<String>().apply { LOGGER.error { "Unexpected state, 'minecraftServer is null', please report this.." } }

    override fun serverStatistics(): BlockyStatistics {
        val minecraftServer = mod.minecraftServer
            ?: return BlockyStatistics.MinecraftStatistics(0.0, 0.0, 0.0, 0, 0).apply {
                LOGGER.error { "Unexpected state, 'minecraftServer is null', please report this.." }
            }

        val tickManager = minecraftServer.tickRateManager()

        val mspt = tickManager.millisecondsPerTick().toDouble()
        val targetTps = tickManager.tickrate().toDouble()

        val tps = if (mspt > 0.0) {
            targetTps.coerceAtMost(1000.0 / mspt)
        } else {
            targetTps
        }

        return BlockyStatistics.MinecraftStatistics(
            targetTps = targetTps,
            tps = tps,
            mspt = mspt,
            onlinePlayers = minecraftServer.playerCount,
            playerLimit = minecraftServer.maxPlayers
        )
    }
}
