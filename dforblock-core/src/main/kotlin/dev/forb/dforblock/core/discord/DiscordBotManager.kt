package dev.forb.dforblock.core.discord

import dev.forb.dforblock.core.DForBlock
import dev.forb.dforblock.core.IBlockyCommunicator
import dev.forb.dforblock.core.LOGGER
import dev.forb.dforblock.core.config.ConfigManager
import dev.forb.dforblock.core.constructMessage
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.event.gateway.DisconnectEvent
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.NON_PRIVILEGED
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

class DiscordBotManager(
    private val dForBlock: DForBlock,
    private val configManager: ConfigManager,
    private val communicator: IBlockyCommunicator,
) {

    var isInitialised: Boolean = false
        private set

    var isReady: Boolean = false
        private set

    private val exceptionHandler = CoroutineExceptionHandler { context, throwable ->
        if (throwable is CancellationException || throwable.toString().contains("CancellationException")) {
            LOGGER.debug { "Coroutine cancelled $context, ${throwable.message}\n${throwable.stackTraceToString()}" }
        } else {
            LOGGER.error { "Unhandled exception in botScope: ${throwable.message}\n${throwable.stackTraceToString()}" }
        }
    }

    val botScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)

    val taskScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob(botScope.coroutineContext[Job]) + exceptionHandler)

    var kord: Kord? = null
        private set

    var taskScheduler: DiscordTaskScheduler? = null
        private set

    var discordEventHandler: DiscordEventHandler? = null
        private set


    suspend fun createGuildCommands(kord: Kord, guildId: ULong) {
        @Suppress("UnusedFlow")
        kord.createGuildApplicationCommands(Snowflake(guildId)) {
            input(
                name = "playerlist",
                description = "Get the list of online players"
            )

            input(
                name = "panel",
                description = "Send the control panel"
            )
        }
    }

    @PrivilegedIntent
    fun start() = botScope.launch {
        try {
            LOGGER.info { "Setting up DForBlock..." }
            setup()
            LOGGER.info { "Logging in..." }
            kord?.login {
                intents = Intents.NON_PRIVILEGED + Intents(Intent.MessageContent)
            } ?: LOGGER.error { "Setup was called but Kord was not setup." }
        } catch (e: Exception) {
            if (e !is CancellationException)
                LOGGER.error { "Failed to initialise Discord bot: ${e.message}\n${e.stackTraceToString()}" }

            isInitialised = false
            isReady = false
        }
    }

    private suspend fun setup() {
        kord = Kord(configManager.core.discordToken).also { kord ->
            for (guildId in configManager.core.guildIds) {
                LOGGER.info { "Creating commands in guild with Id '$guildId'" }
                createGuildCommands(kord, guildId)
            }
            discordEventHandler = DiscordEventHandler(configManager, communicator).apply {
                kord.on<ReadyEvent> {
                    isReady = true
                    LOGGER.info { "DForBlock is now ready." }
                    taskScheduler?.start() ?: LOGGER.error { "Kord is ready but taskScheduler is null." }
                    dForBlock.onServerStart()
                }

                kord.on<GuildChatInputCommandInteractionCreateEvent> { onDiscordChatCommand() }

                kord.on<GuildButtonInteractionCreateEvent> { onDiscordButtonPress() }

                kord.on<GuildModalSubmitInteractionCreateEvent> { onDiscordModalSubmit() }

                kord.on<MessageCreateEvent> { onDiscordMessageReceive() }

                kord.on<DisconnectEvent> { LOGGER.info { "Gateway disconnected." } }
            }
            taskScheduler = DiscordTaskScheduler(taskScope, configManager, kord, communicator)
        }
        isInitialised = true
    }

    suspend fun stop() {
        LOGGER.info { "Logging out..." }
        if (configManager.messages.serverLogs != null && LogtoDiscordHandler.logQueue.isNotEmpty()) {
            val batch = LogtoDiscordHandler.flush()
            if (!batch.isNullOrBlank()) {
                kord?.apply {
                    val template = configManager.messages.serverLogs
                    if (template != null) {
                        val targetChannel = configManager.channels[template.targetChannel]
                        targetChannel?.let { targetChannel ->
                            rest.channel.createMessage(
                                Snowflake(targetChannel.channelId),
                                constructMessage(template, mapOf("{batch}" to batch))
                            )
                        }
                    }
                }
            }
        }
        taskScheduler?.stop() ?: LOGGER.error { "Stop called but taskScheduler is null." }
        withTimeoutOrNull(5.seconds) {
            kord?.apply {
                shutdown()
                resources.httpClient.close()
            } ?: LOGGER.error { "Stop called but kord is null." }
        }

        LOGGER.info { "Logged out." }
        isReady = false
        isInitialised = false
        kord = null
        taskScheduler = null
        discordEventHandler = null
        LOGGER.info { "Goodbye." }
    }

}