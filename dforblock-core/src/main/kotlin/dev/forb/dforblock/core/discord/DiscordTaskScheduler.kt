package dev.forb.dforblock.core.discord

import dev.forb.dforblock.core.*
import dev.forb.dforblock.core.config.ChannelConfig
import dev.forb.dforblock.core.config.ConfigManager
import dev.forb.dforblock.core.config.Core
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.Optional
import dev.kord.core.Kord
import dev.kord.rest.json.request.ChannelModifyPatchRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Collections.emptySet
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DiscordTaskScheduler(
    internal val schedulerScope: CoroutineScope,
    private val configManager: ConfigManager,
    private var kord: Kord,
    private val communicator: IBlockyCommunicator,
    private val messageQueue: Channel<MessageCreateRequest>
) {
    private var updateChannelJobs: MutableSet<Job> = ConcurrentHashMap.newKeySet()
    private suspend fun CoroutineScope.updateChannel(channelName: String, targetChannel: ChannelConfig) {
        try {
            if (targetChannel.topicTemplate == null) {
                return
            }

            while (isActive) {
                val placeholders = buildCommonPlaceholders(communicator) + ("{channelName}" to channelName)
                val topic = targetChannel.topicTemplate.withPlaceholders(placeholders)
                val patchRequest = ChannelModifyPatchRequest(
                    topic = Optional.Value(topic)
                )
                try {
                    kord.rest.channel.patchChannel(
                        channelId = Snowflake(targetChannel.channelId),
                        channel = patchRequest,
                        reason = "DForBlock channel topic is enabled.",
                    )
                } catch (e: Exception) {
                    if (e is CancellationException)
                        throw e
                    LOGGER.error { "Could not update channel topic for channel '$channelName': ${e.message}\n${e.stackTraceToString()}" }
                }
                delay(5.minutes)
            }
        } catch (_: CancellationException) {
            LOGGER.info { "Update channel task for channel '$channelName' is successfully cancelled." }
        }
    }

    private var updatePresenceJob: Job? = null
    private val updatePresenceBlock: suspend CoroutineScope.() -> Unit = {
        try {
            while (isActive) {
                try {
                    val statistics = communicator.serverStatistics()
                    val placeholders = buildCommonPlaceholders(statistics)
                    kord.editPresence {
                        status = configManager.core.discordStatus
                        since = statistics.startup
                        if (configManager.core.showActivity) {
                            val text = configManager.core.activityText?.withPlaceholders(placeholders) ?: ""
                            when (configManager.core.activityType) {
                                Core.RichPresenceType.Playing -> playing(text)
                                Core.RichPresenceType.Listening -> listening(text)
                                Core.RichPresenceType.Watching -> watching(text)
                                Core.RichPresenceType.Competing -> competing(text)
                                Core.RichPresenceType.Streaming -> streaming(
                                    text,
                                    configManager.core.streamUrl?.withPlaceholders(placeholders) ?: ""
                                )

                                else -> {
                                    LOGGER.error { "Unknown activity type '${configManager.core.activityType}'" }
                                }
                            }
                        } else if (configManager.core.showThinkingBubble) {
                            state = configManager.core.thinkingBubbleText?.withPlaceholders(placeholders)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException)
                        throw e
                    LOGGER.error { "Could not update presence text: ${e.stackTraceToString()}" }
                }
                delay(5.minutes)
            }
        } catch (_: CancellationException) {
            LOGGER.info { "Presence task successfully cancelled." }
        }
    }

    private var watchdogJob: Job? = null
    private val watchdogBlock: suspend CoroutineScope.() -> Unit = {
        try {
            var isDead = false
            while (isActive) {
                delay(configManager.core.watchdogInterval.minutes)
                LOGGER.debug { "Checking heartbeat with a timeout of ${configManager.core.watchdogTimeout} second(s)..." }
                try {
                    val isAlive =
                        withTimeoutOrNull(configManager.core.watchdogTimeout.seconds) { communicator.heartbeat() }
                    when (isAlive) {
                        true -> {
                            isDead = false
                            LOGGER.debug { "Server is alive." }
                        }

                        else -> {
                            if (isDead)
                                continue
                            val template = configManager.messages.serverWatchdog
                            if (template == null) {
                                LOGGER.error { "Watchdog task ran but message template is null?" }
                                break
                            }
                            val targetChannel = configManager.channels[template.targetChannel]
                            if (targetChannel == null) {
                                LOGGER.warn { "Could not find channel '${template.targetChannel}' for server watchdog." }
                                break
                            }
                            val request = MessageCreateRequest(
                                targetChannel = template.targetChannel to targetChannel,
                                template = template,
                                webhookPersona = template.webhookRequest(configManager, communicator, null),
                                identifier = "SERVER_WATCHDOG_SIREN"
                            )
                            messageQueue.send(request)
                            isDead = true
                            LOGGER.info { "Server is not responding..." }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    LOGGER.error { "Failed heartbeat! ${e.message}\n${e.stackTraceToString()}" }
                }
            }
        } catch (_: CancellationException) {
            LOGGER.info { "Watchdog task successfully cancelled." }
        }
    }

    private var sendMessagesJob : Job? = null
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun startSendingMessages() =
        messageQueue.receiveAsFlow().collect { request ->
            val success = request.fulfil(kord)
            if (!success)
                LOGGER.error { "Failed to fulfil message create request: ${request.identifier}" }
        }


    fun start() {
        sendMessagesJob = schedulerScope.launch { startSendingMessages() }
        LOGGER.info { "Started sending queued messages." }

        configManager.messages.serverLogs?.let { template ->
            configManager.channels[template.targetChannel]?.let { targetChannel ->
                LogtoDiscordHandler.startFlushing(schedulerScope, kord, template, targetChannel, configManager, communicator)
                LOGGER.info { "Started sending log batches in channel '${template.targetChannel}'." }
            } ?: LOGGER.warn { "Could not find channel '${template.targetChannel}' for server logs." }
        }

        configManager.messages.serverWatchdog?.let { template ->
            configManager.channels[template.targetChannel]?.also {
                watchdogJob = schedulerScope.launch(block = watchdogBlock)
                LOGGER.info { "Started watchdog job in channel '${template.targetChannel}'." }
            } ?: LOGGER.warn { "Could not find channel '${template.targetChannel}' for server watchdog." }
        }

        if (configManager.core.showActivity || configManager.core.showThinkingBubble) {
            updatePresenceJob = schedulerScope.launch(block = updatePresenceBlock)
            LOGGER.info { "Started update presence job." }
        }

        configManager.channels.forEach { (name, channel) ->
            if (channel.topicTemplate != null) {
                updateChannelJobs.add(schedulerScope.launch { updateChannel(name, channel) })
                LOGGER.info { "Started update channel job for channel '$name' with id '${channel.channelId}'." }
            }
        }
    }

    fun stop() {
        messageQueue.close()
        LOGGER.info { "Closed message creation request queue." }
        updatePresenceJob?.cancel()
        updatePresenceJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        updateChannelJobs.forEach { it.cancel() }
        updateChannelJobs.clear()
        LOGGER.info { "Stopping log batching job." }
        LogtoDiscordHandler.stopFlushing()
        LOGGER.info { "Stopped log batching successfully." }
        schedulerScope.coroutineContext.cancelChildren()
        LOGGER.info { "All jobs cancelled." }
    }

}