package dev.forb.dforblock.core.discord

import dev.forb.dforblock.core.DForBlock
import dev.forb.dforblock.core.IBlockyCommunicator
import dev.forb.dforblock.core.LOGGER
import dev.forb.dforblock.core.config.ConfigManager
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class DiscordBotManager(
    private val dForBlock: DForBlock,
    private val configManager: ConfigManager,
    private val communicator: IBlockyCommunicator,
) {
    /**
     * Variables
     */

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

    internal val botScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)

    private val taskScope = CoroutineScope(Dispatchers.Default + SupervisorJob(botScope.coroutineContext[Job]) + exceptionHandler)

    internal lateinit var kord: Kord

    lateinit var taskScheduler: DiscordTaskScheduler
        private set

    private lateinit var discordEventHandler: DiscordEventHandler

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

    @OptIn(PrivilegedIntent::class)
    fun start() = botScope.launch { login() }

    @PrivilegedIntent
    private suspend fun login() {
        try {
            LOGGER.info { "Setting up DForBlock..." }
            setup()
            taskScheduler = DiscordTaskScheduler(
                taskScope,
                configManager,
                kord,
                communicator
            )
            LOGGER.info { "Logging in..." }
            isInitialised = true
            kord.login {
                intents = Intents.NON_PRIVILEGED + Intents(Intent.MessageContent)
            }
        } catch (_: CancellationException) {
            isInitialised = false
        } catch (e: Exception) {
            LOGGER.error { "Failed to initialise Discord bot: ${e.message}\n${e.stackTraceToString()}" }
        }
    }

    private suspend fun setup() {
        kord = Kord(configManager.core.discordToken) {
            enableShutdownHook = true
        }

        for (guildId in configManager.core.guildIds) {
            createGuildCommands(kord, guildId)
        }

        discordEventHandler = DiscordEventHandler(configManager, communicator)

        with(discordEventHandler) {
            kord.on<ReadyEvent> {
                isReady = true
                LOGGER.info { "DForBlock is now ready." }
                taskScheduler.start()
                dForBlock.onServerStart()
            }

            kord.on<GuildChatInputCommandInteractionCreateEvent> { onDiscordChatCommand() }

            kord.on<GuildButtonInteractionCreateEvent> { onDiscordButtonPress() }

            kord.on<GuildModalSubmitInteractionCreateEvent> { onDiscordModalSubmit() }

            kord.on<MessageCreateEvent> { onDiscordMessageReceive() }

            kord.on<DisconnectEvent> {
                LOGGER.info { "Gateway disconnected." }
            }

        }
    }

    fun stop() = runBlocking {
        withTimeoutOrNull(5.seconds) {
            logout().join()
        }
        botScope.coroutineContext.cancelChildren()
        isReady = false
        isInitialised = false
        LOGGER.info { "Goodbye." }
    }

    private fun logout(): Job = botScope.launch {
        LOGGER.info { "Logging out..." }
        taskScheduler.stop()
        kord.shutdown()
        kord.resources.httpClient.close()
        LOGGER.info { "Logged out." }
        isReady = false
    }

}