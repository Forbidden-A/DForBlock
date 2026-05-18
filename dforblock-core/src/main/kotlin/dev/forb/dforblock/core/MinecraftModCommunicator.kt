package dev.forb.dforblock.core

import dev.forb.dforblock.core.config.ConfigManager
import kotlinx.coroutines.suspendCancellableCoroutine
import net.kyori.adventure.audience.Audience
import java.nio.file.Path
import java.util.concurrent.RejectedExecutionException
import kotlin.coroutines.resume

interface ModdedMinecraftServer {
    val players: Set<PlayerData>
    val statistics: GameStatistics
    val isStopped: Boolean
    fun executeIfPossible(command: Runnable)
    fun halt(wait: Boolean)
    fun executeCommand(command: String, builder: StringBuilder)
}

class MinecraftModCommunicator(
    val minecraftServer: ModdedMinecraftServer,
    val configManager: ConfigManager,
    val playerAudience: () -> Audience?,
    override val isLuckperms: Boolean,
    override val configDir: Path,
) : IBlockyCommunicator {

    override suspend fun heartbeat(): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            minecraftServer.executeIfPossible {
                if (continuation.isActive) continuation.resume(true)
            }
        } catch (_: RejectedExecutionException) { // Server is already stopping
            if (continuation.isActive) continuation.resume(true)
        }
    }

    override fun broadcastMessage(payload: DiscordMessageData) {
        val template = configManager.messages.discordUserChats ?: return
        val channel =
            configManager.channels.entries.firstOrNull { (k, v) -> v.channelId == payload.channelId } ?: return
        val kyoriComponent = prepareMinecraftMiniMessage(payload, channel.key, template)
        playerAudience()?.sendMessage(kyoriComponent) ?: LOGGER.error { "Unexpected state, 'player audience is null', please report this.." }
    }

    override fun onlinePlayers(): Set<String> =
        minecraftServer.players.map { it.qualifiedName(configManager, this) }.toSet()

    private lateinit var latestStatistics: GameStatistics

    override suspend fun serverStatistics(): GameStatistics = suspendCancellableCoroutine { continuation ->
        if (minecraftServer.isStopped) {
            if (continuation.isActive && ::latestStatistics.isInitialized) {
                continuation.resume(latestStatistics)
            }
        }
        else {
            latestStatistics = minecraftServer.statistics
            continuation.resume(latestStatistics)
        }
    }

    override fun stopServer() = minecraftServer.halt(false)

    override suspend fun executeCommand(command: String): String = suspendCancellableCoroutine { continuation ->
        val builder = StringBuilder()
        try {
            minecraftServer.executeCommand(command, builder)
            val result = builder.toString().trim()
            if (continuation.isActive) {
                if (result.isEmpty()) continuation.resume("Command executed successfully (no text output).")
                else continuation.resume(result)
            }
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume("Internal Error executing command: ${e.message} ${e.stackTraceToString()}")
        }
    }
}