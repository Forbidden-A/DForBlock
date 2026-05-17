package dev.forb.dforblock.core.discord

import dev.forb.dforblock.core.LOGGER
import dev.forb.dforblock.core.config.ChannelConfig
import dev.forb.dforblock.core.config.MessageTemplate
import dev.forb.dforblock.core.constructMessage
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.seconds

object LogtoDiscordHandler {
    internal val logQueue = ConcurrentLinkedQueue<String>()
    private var flushJob: Job? = null

    private val ansiEscapeRegex = Regex("\u001B\\[[;\\d]*m")

    fun enqueue(level: String, message: String) {
        val cleanMessage = message.replace(ansiEscapeRegex, "")
        logQueue.add("[$level] $cleanMessage")
    }

    internal fun flush(): String? {
        if (logQueue.isNotEmpty()) {
            val builder = StringBuilder()

            while (logQueue.isNotEmpty() && builder.length < 1900) {
                val msg = logQueue.peek()
                if (builder.length + msg.length > 1900) {
                    break
                }
                builder.append(logQueue.poll()).append("\n")
            }

            return if (builder.isNotEmpty()) builder.trimEnd().toString() else null
        }
        return null
    }

    fun startFlushing(scope: CoroutineScope, kord: Kord, template: MessageTemplate, targetChannel: ChannelConfig) {
        if (flushJob != null) return

        flushJob = scope.launch {
            while (isActive) {
                val batch = flush()
                if (batch.isNullOrBlank()) continue
                try {
                    kord.rest.channel.createMessage(
                        Snowflake(targetChannel.channelId),
                        constructMessage(template, mapOf("{batch}" to batch))
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    LOGGER.error { "Failed to create console log batch message in channel '${template.targetChannel}': ${e.message}\n${e.stackTraceToString()}" }
                }
                delay(4.seconds)
            }
        }
    }

    fun stopFlushing() {
        flushJob?.cancel()
        flushJob = null
        logQueue.clear()
    }
}