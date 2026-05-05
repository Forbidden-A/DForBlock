package dev.forb.dforblock.core

import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.Optional
import dev.kord.core.Kord
import dev.kord.rest.json.request.ChannelModifyPatchRequest
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

    private val updateChannelBlock: suspend CoroutineScope.() -> Unit = {
        while (isActive) {
            val topic = config.formats.defaultChannelTopic.let {
                when (val statistics = communicator.serverStatistics()) {
                    is BlockyStatistics.HytaleStatistics -> {
                        it.replace("{game}", "Hytale")
                            .replace("{onlinePlayers}", statistics.onlinePlayers.toString())
                            .replace("{playerLimit}", statistics.playerLimit.toString())
                            .replace("{tps}", 0.0.toString())
                            .replace("{targetTps}", 0.0.toString())
                            .replace("{mspt}", 0.0.toString())
                    }
                    is BlockyStatistics.MinecraftStatistics -> {
                        it.replace("{game}", "Minecraft")
                            .replace("{onlinePlayers}", statistics.onlinePlayers.toString())
                            .replace("{playerLimit}", statistics.playerLimit.toString())
                            .replace("{tps}", statistics.tps.toString())
                            .replace("{targetTps}", statistics.targetTps.toString())
                            .replace("{mspt}", statistics.mspt.toString())

                    }
                }
            }
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

    fun start() {
        if (config.useDefaultChannelTopic) {
            updateChannelJob = schedulerScope.launch(block = updateChannelBlock)
            LOGGER.info { "Started update channel job." }
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