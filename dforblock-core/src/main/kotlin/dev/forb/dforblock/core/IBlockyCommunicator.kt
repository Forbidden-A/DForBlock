package dev.forb.dforblock.core

import java.io.File

interface IBlockyCommunicator {

    fun getConfigFile(): File

    fun ensureConfigFile(): Boolean

    fun broadcastMessage(payload: DiscordMessageData, config: DForBlockConfig)

    fun onlinePlayers(): Set<String>

    fun serverStatistics(): GameStatistics

    fun stopServer()

    suspend fun executeCommand(command: String): String
}