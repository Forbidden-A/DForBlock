package dev.forb.dforblock

import java.util.logging.Level
import java.io.File

class PaperCommunicator(val paper: DForBlockPaper) : IBlockyCommunicator {

    override fun getConfigFile(): File {
        val config = File(paper.dataFolder, "dforblock.toml")
        if (!config.exists()) {
            config.parentFile.let { if (!it.exists()) it.mkdirs() }

            paper.saveResource(config.path, false)
        }
        return config
    }

    override fun broadcastMessage(payload: DiscordMessagePayload, config: DForBlockConfig) {
        val component = prepareMinecraftMessage(payload, config)

        val channel = config.channels.firstOrNull { it.channelId == payload.channelID } ?: return

        if (channel.channelName.equals("global", ignoreCase = true)) {
            paper.server.sendMessage(component)
        } else {
          paper.logger.log(Level.WARNING) { "Other channels are W.I.P, this message with id '${payload.messageID}' will be ignored.." }
        }
    }

    override fun onlinePlayers(): Set<String> {
        return paper.server.onlinePlayers.map { it.name }.toSortedSet()
    }

    override fun serverStatistics(): BlockyStatistics {
        return BlockyStatistics.MinecraftStatistics(
            tps = paper.server.tps[1],
            mspt = paper.server.averageTickTime,
            onlinePlayers = paper.server.onlinePlayers.size,
            playerLimit = paper.server.maxPlayers
        )
    }
}