package dev.forb.dforblock.hytale

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.HytaleServer
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.ShutdownReason
import com.hypixel.hytale.server.core.universe.Universe
import dev.forb.dforblock.core.DiscordMessageData
import dev.forb.dforblock.core.GameStatistics
import dev.forb.dforblock.core.IBlockyCommunicator
import dev.forb.dforblock.core.config.ConfigManager
import dev.forb.dforblock.core.prepareMiniMessage
import eu.mikart.adventure.platform.hytale.ConsoleCommandSender
import eu.mikart.adventure.platform.hytale.HytaleAudiences
import eu.mikart.adventure.platform.hytale.HytaleComponentSerializer
import kotlinx.coroutines.suspendCancellableCoroutine
import net.kyori.adventure.text.Component
import java.nio.file.Path
import java.util.*
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.toKotlinInstant

class HytaleCommunicator(
    override val configDir: Path,
    override val isLuckperms: () -> Boolean,
    val configManager: ConfigManager,
) : IBlockyCommunicator {

    override fun onlinePlayers(): Set<String> = Universe.get().players.mapTo(mutableSetOf()) { ref -> ref.username }
    override fun stopServer() {
        HytaleServer.get().shutdownServer(
            ShutdownReason(
                0, HytaleComponentSerializer.get().serialize(
                    Component.text("Shutdown requested by DForBlock")
                ).formattedMessage
            )
        )
    }
    override suspend fun heartbeat(): Boolean = suspendCancellableCoroutine { continuation ->
        if (DForBlockHytalePlugin.instance == null || HytaleServer.get().isShuttingDown || !HytaleServer.get().isBooted) {
            if (continuation.isActive) continuation.resume(true)
            return@suspendCancellableCoroutine
        }
        if (continuation.isActive) HytaleServer.SCHEDULED_EXECUTOR.execute { continuation.resume(true) }
    }

    override fun broadcastMessage(payload: DiscordMessageData) {
        val template = configManager.messages.discordUserChats ?: return
        val channelName =
            configManager.channels.entries.firstOrNull { (_, v) -> v.channelId == payload.channelId }?.key
                ?: return

        val kyoriComponent = prepareMiniMessage(payload, channelName, template)
        val message = HytaleComponentSerializer.get().serialize(kyoriComponent)
        val universe = Universe.get() ?: return

        HytaleServer.SCHEDULED_EXECUTOR.execute {
            universe.worlds.forEach { (_, world) ->
                world.sendMessage(message)
            }
        }
    }

    override suspend fun serverStatistics(): GameStatistics {
        val universe = Universe.get()
        val server = HytaleServer.get()

        if (universe == null || server == null) {
            return GameStatistics(
                gameType = GameStatistics.GameType.Hytale,
                onlinePlayers = 0,
                playerLimit = 0,
                startup = Clock.System.now(),
                gameVersion = "Unknown",
                targetTps = 30.0,
                tps = 30.0,
                mspt = 0.0
            )
        }

        val worlds = universe.worlds.values.filterNotNull()
        val globalBaselineTargetTps = 30.0
        var totalEfficiencyRatio = 0.0
        var highestMspt = 0.0

        for (world in worlds) {
            val targetTps = world.tps.toDouble().coerceAtLeast(1.0)
            val tickStepNanos = world.tickStepNanos
            val metrics = world.bufferedTickLengthMetricSet

            var worldActualTps = targetTps
            var worldMspt = 0.0

            if (metrics != null && metrics.periodsNanos.isNotEmpty()) {
                val avgTickNanos = metrics.getAverage(0) // The most recent average
                if (avgTickNanos > 0) {
                    worldMspt = avgTickNanos / 1_000_000.0
                    val actualTickNanos = max(avgTickNanos, tickStepNanos.toDouble())

                    val calculatedTps = (1.0 / actualTickNanos) * 1_000_000_000.0
                    worldActualTps = calculatedTps.coerceAtMost(targetTps)
                }
            }

            if (worldMspt > highestMspt) {
                highestMspt = worldMspt
            }

            totalEfficiencyRatio += (worldActualTps / targetTps)
        }

        val averageEfficiency = if (worlds.isNotEmpty()) (totalEfficiencyRatio / worlds.size) else 1.0
        val displayTps = globalBaselineTargetTps * averageEfficiency

        return GameStatistics(
            gameType = GameStatistics.GameType.Hytale,
            onlinePlayers = universe.playerCount,
            playerLimit = server.config.maxPlayers,
            startup = server.boot.toKotlinInstant(),
            gameVersion = Universe.MANIFEST.serverVersion ?: "Unknown",
            targetTps = globalBaselineTargetTps,
            tps = (displayTps * 100.0).roundToLong() / 100.0,
            mspt = (highestMspt * 100.0).roundToLong() / 100.0
        )
    }

    override suspend fun executeCommand(command: String): String = suspendCancellableCoroutine { continuation ->
        val builder = StringBuilder()
        val sender: ConsoleCommandSender = object : ConsoleCommandSender() {
            override fun sendMessage(message: Message) {
                val logger = HytaleLogger.forEnclosingClass()
                val ansiMessage = message.ansiMessage
                builder.append(ansiMessage)
                logger.atInfo().log(ansiMessage)
            }

            override fun hasPermission(perm: String): Boolean = true
            override fun hasPermission(perm: String, b: Boolean): Boolean = true
            override fun getDisplayName(): String = "DForBlock"
            override fun getUuid(): UUID = UUID.randomUUID()
        }
        try {
            HytaleServer.get().commandManager.handleCommand(
                sender,
                command
            ).whenComplete { _, exception ->
                if (continuation.isActive) {
                    if (exception != null) {
                        val errorCause = exception.cause?.message ?: exception.message ?: "Malformed command."
                        continuation.resume(builder.toString().trim() + "\nError: $errorCause")
                    } else {
                        val result = builder.toString().trim()
                        if (result.isBlank()) {
                            continuation.resume("Command executed successfully (no text output).")
                        } else {
                            continuation.resume(result)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume("Internal Error executing command: ${e.message} ${e.stackTraceToString()}")
            }
        }
    }
}