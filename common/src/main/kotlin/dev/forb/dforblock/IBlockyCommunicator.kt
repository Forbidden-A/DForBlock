package dev.forb.dforblock

import java.io.File

interface IBlockyCommunicator {
    fun getConfigFile(): File
    fun broadcastMessage(payload: DiscordMessagePayload)

    fun onlinePlayers(): Set<String>?

    fun serverStatistics(): BlockyStatistics
}