package dev.forb.dforblock.core

import dev.forb.dforblock.core.config.ConfigManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import net.kyori.adventure.audience.Audience
import java.nio.file.Path
import java.util.concurrent.RejectedExecutionException
import kotlin.coroutines.resume

interface MinecraftModdedServerLike {
    val players: Set<PlayerData>
    val statistics: GameStatistics
    val isNotActive: Boolean
    fun executeIfPossible(runnable: Runnable)
    fun halt(wait: Boolean)
    fun executeCommand(command: String, builder: StringBuilder)
}

class ModdedMinecraftCommunicator(
    val serverLike: MinecraftModdedServerLike,
    val configManager: ConfigManager,
    val playerAudience: () -> Audience?,
    override val isLuckperms: () -> Boolean,
    override val configDir: Path,
) : IBlockyCommunicator {

    override suspend fun heartbeat(): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            serverLike.executeIfPossible {
                if (continuation.isActive) continuation.resume(true)
            }
        } catch (_: RejectedExecutionException) { // Server is stopping
            if (continuation.isActive) continuation.resume(true)
        } catch (e: Exception) {
            LOGGER.error { "Exception during heartbeat ${e.message}\n${e.stackTraceToString()}" }
            if (continuation.isActive) continuation.resume(false)
        }
    }

    override fun broadcastMessage(payload: DiscordMessageData) {
        val template = configManager.messages.discordUserChats ?: return
        val channelName =
            configManager.channels.entries.firstOrNull { (k, v) -> v.channelId == payload.channelId }?.key ?: return
        val kyoriComponent = prepareMiniMessage(payload, channelName, template)
        playerAudience()?.sendMessage(kyoriComponent)
            ?: LOGGER.error { "Unexpected state, 'player audience is null', please report this.." }
    }

    override fun onlinePlayers(): Set<String> =
        serverLike.players.mapTo(mutableSetOf()) { it.qualifiedName(configManager, this) }

    private lateinit var latestStatistics: GameStatistics

    override suspend fun serverStatistics(): GameStatistics = suspendCancellableCoroutine { continuation ->
        if (serverLike.isNotActive) {
            if (continuation.isActive && ::latestStatistics.isInitialized) {
                continuation.resume(latestStatistics)
            }
        } else {
            latestStatistics = serverLike.statistics
            continuation.resume(latestStatistics)
        }
    }

    override fun stopServer() = serverLike.halt(false)

    override suspend fun executeCommand(command: String): String = suspendCancellableCoroutine { continuation ->
        val builder = StringBuilder()
        try {
            runBlocking { serverLike.executeCommand(command, builder) }
            val result = builder.toString().trim()
            if (continuation.isActive) {
                if (result.isEmpty()) {
                    continuation.resume("Command executed successfully (no text output).")
                } else {
                    continuation.resume(result)
                }
            }
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume("Internal Error executing command: ${e.message} ${e.stackTraceToString()}")
        }
    }
}