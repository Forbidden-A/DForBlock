package dev.forb.dforblock.core.discord

import dev.forb.dforblock.core.IBlockyCommunicator
import dev.forb.dforblock.core.LOGGER
import dev.forb.dforblock.core.config.ChannelConfig
import dev.forb.dforblock.core.config.ConfigManager
import dev.forb.dforblock.core.config.ContainerElementTextDisplay
import dev.forb.dforblock.core.config.MessageTemplate
import dev.forb.dforblock.core.webhookRequest
import dev.kord.core.Kord
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

object LogtoDiscordHandler {
    internal val logQueue = ConcurrentLinkedDeque<String>()
    private var flushJob: Job? = null

    private val ansiEscapeRegex = Regex("\u001B\\[[;\\d]*m")
    private val internetProtocolAddressRegex = Regex("""/?(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?""")

    fun enqueue(level: String, message: String) {
        var cleanMessage = message.replace(ansiEscapeRegex, "")
        cleanMessage = internetProtocolAddressRegex.replace(cleanMessage, "[IP REDACTED]")
        logQueue.add("[$level] $cleanMessage")
    }

    private var _messageSizeLimit: Int? = null

    val messageSizeLimit: Int
        get() = _messageSizeLimit!!

    internal fun flush(): String? {
        if (logQueue.isNotEmpty()) {
            val builder = StringBuilder()

            while (logQueue.isNotEmpty() && builder.length < messageSizeLimit) {
                val msg = logQueue.peek() ?: break
                val spaceAvailable = messageSizeLimit - builder.length - 1

                if (msg.length > spaceAvailable) {
                    if (builder.isEmpty()) {
                        val chunk = msg.substring(0, messageSizeLimit)
                        val remainder = msg.substring(messageSizeLimit)

                        builder.append(chunk)
                        logQueue.poll()
                        logQueue.addFirst(remainder)
                    }
                    break
                } else {
                    builder.append(logQueue.poll()).append("\n")
                }
            }
            return if (builder.isNotEmpty()) builder.trimEnd().toString() else null
        }
        return null
    }

    fun startFlushing(scope: CoroutineScope, kord: Kord, template: MessageTemplate, targetChannel: ChannelConfig, configManager: ConfigManager, communicator: IBlockyCommunicator) {
        if (flushJob != null) return

        if (_messageSizeLimit == null) {
            _messageSizeLimit = when {
                template.standard != null -> {
                    val std = template.standard

                    if (std.content?.contains("{batch}") == true) {
                        2_000 - std.content.replace("{batch}", "").length
                    } else if (std.embed != null) {
                        val embed = std.embed
                        var totalChars = 0
                        totalChars += embed.title?.replace("{batch}", "")?.length ?: 0
                        totalChars += embed.description?.replace("{batch}", "")?.length ?: 0
                        totalChars += embed.authorName?.replace("{batch}", "")?.length ?: 0
                        totalChars += embed.footerText?.replace("{batch}", "")?.length ?: 0
                        embed.fields?.forEach { field ->
                            totalChars += field.name.replace("{batch}", "").length
                            totalChars += field.value?.replace("{batch}", "")?.length ?: 1
                        }

                        val maxTotalAvailable = 6_000 - totalChars

                        when {
                            embed.description?.contains("{batch}") == true ->
                                min(4_096 - embed.description.replace("{batch}", "").length, maxTotalAvailable)

                            embed.title?.contains("{batch}") == true ->
                                min(256 - embed.title.replace("{batch}", "").length, maxTotalAvailable)

                            embed.authorName?.contains("{batch}") == true ->
                                min(256 - embed.authorName.replace("{batch}", "").length, maxTotalAvailable)

                            embed.footerText?.contains("{batch}") == true ->
                                min(2_048 - embed.footerText.replace("{batch}", "").length, maxTotalAvailable)

                            embed.fields?.any { it.name.contains("{batch}") } == true -> {
                                val field = embed.fields.first { it.name.contains("{batch}") }
                                min(256 - field.name.replace("{batch}", "").length, maxTotalAvailable)
                            }

                            embed.fields?.any { it.value?.contains("{batch}") == true } == true -> {
                                val field = embed.fields.first { it.value?.contains("{batch}") == true }
                                min(1_024 - (field.value?.replace("{batch}", "")?.length ?: 1), maxTotalAvailable)
                            }

                            else -> throw IllegalStateException("Could not find '{batch}' inside template.standard.embed!")
                        }
                    } else {
                        throw IllegalStateException("Template standard content and embed are both null!")
                    }
                }

                template.container != null -> {
                    val display = template.container.elements.filterIsInstance<ContainerElementTextDisplay>()
                        .firstOrNull { it.text.contains("{batch}") }
                        ?: throw IllegalStateException("Could not find '{batch}' inside template.container!")

                    4_000 - display.text.replace("{batch}", "").length
                }

                else -> throw IllegalStateException("Template is empty! Both standard and container are null.")
            }
        }

        flushJob = scope.launch {
            while (isActive) {
                val batch = flush()
                if (!batch.isNullOrBlank()) {
                    try {
                        val request = MessageCreateRequest(
                            targetChannel = template.targetChannel to targetChannel,
                            template = template,
                            placeholders = mapOf("{batch}" to batch),
                            webhookPersona = template.webhookRequest(configManager, communicator, null),
                            identifier = "LOG_INTERCEPTOR_LOG_BATCH"
                        )
                        val success = request.fulfil(kord)
                        if (!success)
                            LOGGER.error { "Failed to send log batch to '${template.targetChannel}'" }
                    } catch (e: CancellationException) {
                        throw e
                    }
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