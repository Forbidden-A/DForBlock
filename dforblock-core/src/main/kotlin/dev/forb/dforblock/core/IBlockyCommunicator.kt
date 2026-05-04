package dev.forb.dforblock.core

import java.io.File

interface IBlockyCommunicator {

    fun getConfigFile(): File

    fun ensureConfigFile(): Boolean

    fun broadcastMessage(payload: DiscordMessagePayload, config: DForBlockConfig)

    fun onlinePlayers(): Set<String>

    fun serverStatistics(): BlockyStatistics
}