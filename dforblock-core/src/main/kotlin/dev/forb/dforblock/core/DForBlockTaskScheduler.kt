package dev.forb.dforblock.core

import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.Optional
import dev.kord.core.Kord
import dev.kord.rest.json.request.ChannelModifyPatchRequest
import io.ktor.client.utils.EmptyContent.status
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes

class DForBlockTaskScheduler(
    private val schedulerScope: CoroutineScope,
    private val config: DForBlockConfig,
    private var defaultChannel: ChannelConfig,
    private var kord: Kord,
    private val communicator: IBlockyCommunicator
) {
    private var updateChannelJob: Job? = null
    private var updatePresenceJob: Job? = null

    private fun replaceStatistics(statistics: BlockyStatistics, text: String): String = when (statistics) {
        is BlockyStatistics.HytaleStatistics -> {
            text.replace("{game}", "Hytale")
                .replace("{onlinePlayers}", statistics.onlinePlayers.toString())
                .replace("{playerLimit}", statistics.playerLimit.toString())
                .replace("{tps}", 0.0.toString())
                .replace("{targetTps}", 0.0.toString())
                .replace("{mspt}", 0.0.toString())
        }
        is BlockyStatistics.MinecraftStatistics -> {
            text.replace("{game}", "Minecraft")
                .replace("{onlinePlayers}", statistics.onlinePlayers.toString())
                .replace("{playerLimit}", statistics.playerLimit.toString())
                .replace("{tps}", statistics.tps.toString())
                .replace("{targetTps}", statistics.targetTps.toString())
                .replace("{mspt}", statistics.mspt.toString())

        }
    }

    private val updateChannelBlock: suspend CoroutineScope.() -> Unit = {
        while (isActive) {
            val topic = replaceStatistics(communicator.serverStatistics(), config.formats.defaultChannelTopic)
            val patchRequest = ChannelModifyPatchRequest(
                topic = Optional.Value(topic)
            )
            try {
                kord.rest.channel.patchChannel(
                    channelId = Snowflake(defaultChannel.channelId),
                    channel = patchRequest,
                    reason = "default channel topic is enabled.",
                )
            } catch (e: Exception) {
                LOGGER.error { "Could not update channel topic: ${e.stackTraceToString()}" }
            }
            delay(5.minutes)
        }
    }

    private val updatePresenceBlock: suspend CoroutineScope.() -> Unit = {
        val status = when (val status = config.discordStatus) {
            0 -> PresenceStatus.Online
            1 -> PresenceStatus.Idle
            2 -> PresenceStatus.DoNotDisturb
            3 -> PresenceStatus.Offline
            else -> PresenceStatus.Online.apply { LOGGER.info { "Unexpected status: $status" } }
        }
        while (isActive) {
            val statistics = communicator.serverStatistics()
            val since = when (statistics) {
                is BlockyStatistics.MinecraftStatistics -> statistics.startup
                is BlockyStatistics.HytaleStatistics -> statistics.startup
            }
            val presenceText = replaceStatistics(statistics, config.formats.discordPresenceText)
            try {
                kord.editPresence {
                    this.status = status
                    this.since = since
                    when (config.richPresenceType) {
                        0 -> playing(presenceText)
                        1 -> listening(presenceText)
                        2 -> watching(presenceText)
                        3 -> competing(presenceText)
                        4 -> streaming(presenceText, config.discordStreamUrl)
                    }
                }
            } catch (e: Exception) {
                LOGGER.error { "Could not update presence text: ${e.stackTraceToString()}" }
            }
            delay(5.minutes)
        }
    }

    fun start() {
        if (config.useDefaultChannelTopic) {
            updateChannelJob = schedulerScope.launch(block = updateChannelBlock)
            LOGGER.info { "Started update channel job." }
        }

        if (config.useRichPresence) {
            updatePresenceJob = schedulerScope.launch(block = updatePresenceBlock)
            LOGGER.info { "Started update presence job." }
        }
    }

    fun stop() {
        updateChannelJob?.cancel()
        updatePresenceJob?.cancel()
        updateChannelJob = null
        updatePresenceJob = null
        schedulerScope.coroutineContext.cancelChildren()
        LOGGER.info { "All jobs cancelled." }
    }

}