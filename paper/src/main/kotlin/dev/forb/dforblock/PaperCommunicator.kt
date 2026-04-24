package dev.forb.dforblock

import dev.forb.dforblock.DForBlock.logger
import net.kyori.adventure.text.Component
import java.util.logging.Level
import java.io.File

class PaperCommunicator(val paper: DForBlockPaper) : IBlockyCommunicator {
    private lateinit var config: DForBlockConfig
    override fun getConfigFile(): File {
        TODO("Not yet implemented")
    }

    override fun broadcastMessage(payload: DiscordMessagePayload) {
        paper.server.sendMessage {
            Component.text("${config.discordPrefix}${payload.luckpermsPrefix}${payload.author} >> ${payload.content}")
        }
        TODO("Proper formatting and handling TODO")
    }

    override fun onlinePlayers(): Set<String>? {
        if (!paper.isEnabled) {
            logger.log(Level.SEVERE) { "Plugin not enabled on server." }
            return null
        }
        return paper.server.onlinePlayers.map { it.name }.toSortedSet()
    }

    override fun serverStatistics(): BlockyStatistics {
        TODO("Not yet implemented")
    }
}