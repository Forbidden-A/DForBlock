package dev.forb.dforblock.core

import dev.forb.dforblock.core.config.ConfigManager
import java.io.File
import java.nio.file.Path

interface IBlockyCommunicator {

    val configDir: Path

    val isLuckperms: Boolean

    fun broadcastMessage(payload: DiscordMessageData)

    fun onlinePlayers(): Set<String>

    fun serverStatistics(): GameStatistics

    fun stopServer()

    suspend fun executeCommand(command: String): String
}