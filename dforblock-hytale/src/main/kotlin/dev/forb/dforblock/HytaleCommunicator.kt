package dev.forb.dforblock

import dev.forb.dforblock.core.DiscordMessageData
import dev.forb.dforblock.core.GameStatistics
import dev.forb.dforblock.core.IBlockyCommunicator
import dev.forb.dforblock.core.config.ConfigManager
import java.nio.file.Path

class HytaleCommunicator(override val configDir: Path, override val isLuckperms: Boolean, configManager: ConfigManager, plugin: DForBlockPlugin) : IBlockyCommunicator {

    override suspend fun heartbeat(): Boolean {
        TODO("Not yet implemented")
    }

    override fun broadcastMessage(payload: DiscordMessageData) {
        TODO("Not yet implemented")
    }

    override fun onlinePlayers(): Set<String> {
        TODO("Not yet implemented")
    }

    override suspend fun serverStatistics(): GameStatistics {
        TODO("Not yet implemented")
    }

    override fun stopServer() {
        TODO("Not yet implemented")
    }

    override suspend fun executeCommand(command: String): String {
        TODO("Not yet implemented")
    }
}