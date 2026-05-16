package dev.forb.dforblock.core

import dev.forb.dforblock.core.config.ChannelConfig
import dev.forb.dforblock.core.config.ConfigManager
import dev.forb.dforblock.core.config.Core
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.Optional
import dev.kord.core.Kord
import dev.kord.rest.json.request.ChannelModifyPatchRequest
import kotlinx.coroutines.*
import java.util.Collections.emptySet
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

class DForBlockTaskScheduler(
    private val schedulerScope: CoroutineScope,
    private val configManager: ConfigManager,
    private var kord: Kord,
    private val communicator: IBlockyCommunicator
) {
    private var updateChannelJobs: MutableSet<Job> = ConcurrentHashMap.newKeySet()
    private var updatePresenceJob: Job? = null

    private suspend fun CoroutineScope.updateChannel(channelName: String, targetChannel: ChannelConfig) {
        if (targetChannel.topicTemplate == null) { return }

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
                    reason = "default channel topic is enabled.",
                )
            } catch (e: Exception) {
                LOGGER.error { "Could not update channel topic: ${e.message}\n${e.stackTraceToString()}" }
            }
            delay(5.minutes)
        }
    }

    private val updatePresenceBlock: suspend CoroutineScope.() -> Unit = {
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
                    }
                    else if (configManager.core.showThinkingBubble) {
                        state = configManager.core.thinkingBubbleText?.withPlaceholders(placeholders)
                    }
                }
            } catch (e: Exception) {
                LOGGER.error { "Could not update presence text: ${e.stackTraceToString()}" }
            }
            delay(5.minutes)
        }
    }

    fun start() {
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
        updateChannelJobs.forEach { it.cancel() }
        updatePresenceJob?.cancel()
        updateChannelJobs = emptySet()
        updatePresenceJob = null
        schedulerScope.coroutineContext.cancelChildren()
        schedulerScope.cancel()
        LOGGER.info { "All jobs cancelled." }
    }

}