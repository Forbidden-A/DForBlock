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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

    internal val botScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val taskScope = CoroutineScope(Dispatchers.Default + SupervisorJob(botScope.coroutineContext[Job]))

    internal lateinit var kord: Kord

    lateinit var taskScheduler: DiscordTaskScheduler
        private set

    private lateinit var discordEventHandler: DiscordEventHandler

    suspend fun createGuildCommands(kord: Kord, guildId: ULong) {
        kord.createGuildChatInputCommand(
            name = "playerlist",
            description = "Get the list of online players",
            guildId = Snowflake(guildId)
        )

        kord.createGuildChatInputCommand(
            name = "panel",
            description = "Send the control panel",
            guildId = Snowflake(guildId)
        )
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
            kord.login {
                intents = Intents.NON_PRIVILEGED + Intents(Intent.MessageContent)
            }
            isInitialised = true
        } catch (e: Exception) {
            LOGGER.error { "Failed to initialise Discord bot: ${e.message}\n${e.stackTraceToString()}" }
            isInitialised = false
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
        logout()
        botScope.cancel()
        isReady = false
        isInitialised = false
        LOGGER.info { "Goodbye." }
    }

    private suspend fun logout() {
        LOGGER.info { "Logging out..." }
        kord.shutdown()
        LOGGER.info { "Logged out." }
        isReady = false
    }

}