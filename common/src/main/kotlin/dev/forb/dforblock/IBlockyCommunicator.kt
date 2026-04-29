package dev.forb.dforblock

import java.io.File

interface IBlockyCommunicator {

    fun getConfigFile(): File
    fun broadcastMessage(payload: DiscordMessagePayload, config: DForBlockConfig)

    fun onlinePlayers(): Set<String>?

    fun serverStatistics(): BlockyStatistics
}