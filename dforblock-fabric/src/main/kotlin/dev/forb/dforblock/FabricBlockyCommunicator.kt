package dev.forb.dforblock

import java.io.File

class FabricBlockyCommunicator(val mod: Dforblock) : IBlockyCommunicator {
    override fun getConfigFile(): File = mod.configPath.toFile()

    override fun broadcastMessage(
        payload: DiscordMessagePayload,
        config: DForBlockConfig
    ) {
        val (author, content, channelId, messageId) = payload
        TODO()
    }

    override fun onlinePlayers(): Set<String> =
        mod.minecraftServer.playerList.players.map { it.displayName.toString() }.toSet()

    override fun serverStatistics(): BlockyStatistics {
        val tickManager = mod.minecraftServer.tickRateManager()
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
            onlinePlayers = mod.minecraftServer.playerCount,
            playerLimit = mod.minecraftServer.maxPlayers
        )
    }
}
