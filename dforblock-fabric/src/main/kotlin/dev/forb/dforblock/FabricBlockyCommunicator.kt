package dev.forb.dforblock

import java.io.File
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class FabricBlockyCommunicator(val mod: DForBlockFabric) : IBlockyCommunicator {
    override fun getConfigFile(): File = mod.configPath.toFile()

    override fun ensureConfigFile(): Boolean {
        try {
            mod.configDir.createDirectories()
        } catch (e: Exception) {
            mod.logger.severe { e.stackTraceToString() }
        }

        if (!mod.configPath.exists())
        {
            val resourceStream = DForBlockFabric::class.java.getResourceAsStream("/dforblock.toml")
            if (resourceStream != null) {
                Files.copy(resourceStream, mod.configPath)
                resourceStream.close()
                mod.logger.warning { "========================================"}
                mod.logger.warning { "Created config file, please restart after configuring it correctly." }
                mod.logger.warning { "========================================"}
            } else {
                mod.logger.severe { "========================================"}
                mod.logger.severe { "Unexpected state, 'config file does not exist', please ensure mod jar is unmodified." }
                mod.logger.severe { "========================================"}
            }
            return false
        }
        return true
    }

    override fun broadcastMessage(
        payload: DiscordMessagePayload,
        config: DForBlockConfig
    ) {
        val kyoriComponent = prepareMinecraftMiniMessage(payload, config)
        println(kyoriComponent)
        mod.adventure?.players()?.sendMessage(kyoriComponent)?: return mod.logger.severe { "Unexpected state, 'adventure is null', please report this.." }
    }

    override fun onlinePlayers(): Set<String> =
        mod.minecraftServer?.playerList?.players?.map { it.displayName.toString() }?.toSet()
            ?: emptySet<String>().apply { mod.logger.severe { "Unexpected state, 'minecraftServer is null', please report this.." } }

    override fun serverStatistics(): BlockyStatistics {
        val minecraftServer = mod.minecraftServer
            ?: return BlockyStatistics.MinecraftStatistics(0.0, 0.0, 0.0, 0, 0).apply {
                mod.logger.severe { "Unexpected state, 'minecraftServer is null', please report this.." }
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
