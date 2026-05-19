package dev.forb.dforblock.papermc

import dev.forb.dforblock.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.bukkit.command.CommandException
import java.nio.file.Path
import kotlin.coroutines.resume

class PaperCommunicator(
    override val configDir: Path,
    override val isLuckperms: Boolean,
    val plugin: DForBlockPaper
) : IBlockyCommunicator {
    override fun stopServer() = plugin.server.shutdown()

    override fun onlinePlayers(): Set<String> {
        val plainText = PlainTextComponentSerializer.plainText()
        return plugin.server.onlinePlayers.mapTo(mutableSetOf()) { plainText.serialize(it.displayName()) }
    }

    override suspend fun serverStatistics(): GameStatistics {
        return GameStatistics(
            gameType = GameStatistics.GameType.Minecraft,
            onlinePlayers = plugin.server.onlinePlayers.size,
            playerLimit = plugin.server.maxPlayers,
            startup = plugin.startup,
            gameVersion = plugin.server.minecraftVersion,
            targetTps = plugin.server.serverTickManager.tickRate.toDouble(),
            tps = plugin.server.tps[0],
            mspt = plugin.server.averageTickTime
        )
    }


    override suspend fun heartbeat(): Boolean = suspendCancellableCoroutine { continuation ->
        if (!plugin.isEnabled) {
            if (continuation.isActive) continuation.resume(true)
            return@suspendCancellableCoroutine
        }

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            if (continuation.isActive) continuation.resume(true)
        }
    }

    override fun broadcastMessage(payload: DiscordMessageData) {
        val template = plugin.configManager?.messages?.discordUserChats ?: return
        val channelName =
            plugin.configManager?.channels?.entries?.firstOrNull { (_, v) -> v.channelId == payload.channelId }?.key
                ?: return
        val kyoriComponent = prepareMinecraftMiniMessage(payload, channelName, template)
        plugin.server.sendMessage(kyoriComponent)
    }

    override suspend fun executeCommand(command: String): String = suspendCancellableCoroutine { continuation ->
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            val builder = StringBuilder()
            val currentThreadId = Thread.currentThread().threadId()
            val logger = LogManager.getRootLogger() as org.apache.logging.log4j.core.Logger

            val threadLocalAppender =
                object : AbstractAppender("Capture-$currentThreadId", null, null, true, Property.EMPTY_ARRAY) {
                    override fun append(event: LogEvent) {
                        if (Thread.currentThread().threadId() == currentThreadId) {
                            val msg = event.message.formattedMessage
                            val cleanMsg = msg.replace(Regex("^\\[Server:?|^\\[Console:?"), "").trim()
                            builder.append(cleanMsg).append("\n")
                        }
                    }
                }
            try {
                threadLocalAppender.start()
                logger.addAppender(threadLocalAppender)
                try {
                    val success = plugin.server.dispatchCommand(plugin.server.consoleSender, command)
                    if (continuation.isActive) {
                        val result = builder.toString().trim()
                        if (result.isBlank())
                            continuation.resume(
                                if (success) "Command executed successfully (no text output)." else {
                                    "Unknown command '${command.split(' ').firstOrNull() ?: "Unknown"}'."
                                }
                            )
                        else
                            continuation.resume(result)
                    }
                } catch (e: CommandException) {
                    if (continuation.isActive) {
                        val result = builder.toString().trim()
                        if (result.isNotBlank()) {
                            continuation.resume(result)
                        } else {
                            val causeMessage = e.cause?.message ?: e.message ?: "Malformed command."
                            val cleanCause = causeMessage.replace("com.mojang.brigadier.exceptions.", "")
                            continuation.resume(cleanCause)
                        }
                    }
                    LOGGER.warn { "Command run failure: ${e.cause?.message ?: e.message ?: e.stackTraceToString()}" }
                }

            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume("Internal Error executing command: ${e.message} ${e.stackTraceToString()}")
            } finally {
                logger.removeAppender(threadLocalAppender)
                threadLocalAppender.stop()
            }
        }
    }
}