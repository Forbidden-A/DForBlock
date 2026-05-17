package dev.forb.dforblock.core

import java.nio.file.Path

interface IBlockyCommunicator {

    val configDir: Path

    val isLuckperms: Boolean

    suspend fun heartbeat(): Boolean

    fun broadcastMessage(payload: DiscordMessageData)

    fun onlinePlayers(): Set<String>

    fun serverStatistics(): GameStatistics

    fun stopServer()

    suspend fun executeCommand(command: String): String
}