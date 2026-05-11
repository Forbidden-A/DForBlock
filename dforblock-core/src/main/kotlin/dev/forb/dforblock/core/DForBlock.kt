package dev.forb.dforblock.core

import dev.kord.common.Color
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.MessageFlags
import dev.kord.common.entity.Snowflake
import dev.kord.common.exception.RequestException
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.gateway.DisconnectEvent
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.NON_PRIVILEGED
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.component.actionRow
import dev.kord.rest.builder.message.container
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.random.nextInt

val LOGGER = KotlinLogging.logger {}
val JSON = Json {
    prettyPrint = true
    isLenient = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/*
* This is the entry point of this project
*  */
class DForBlock(val communicator: IBlockyCommunicator) {

    private val botScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val taskScope = CoroutineScope(Dispatchers.Default + SupervisorJob(botScope.coroutineContext[Job]))

    private lateinit var kord: Kord

    private lateinit var config: DForBlockConfig

    private lateinit var defaultChannel: ChannelConfig

    lateinit var taskScheduler: DForBlockTaskScheduler
        private set

    var isInitialised: Boolean = false
        private set

    var isReady: Boolean = false
        private set


    fun start() {
        LOGGER.info { "DForBlock starting..." }

        if (!communicator.ensureConfigFile())
            return LOGGER.error { "Start up halted: Failed to load config file." }

        try {
            config = loadConfig(communicator.getConfigFile(), JSON)
        } catch (e: Exception) {
            return LOGGER.error { "Start up halted: Failed to load config: ${e.stackTraceToString()}" }
        }

        val defaultChannel = config.channels["default"]
            ?: return LOGGER.error { "Start up halted: You must configure a channel with the name 'default'." }

        if (config.richPresenceType !in 0..4)
            return LOGGER.error { "Start up halted: rich presence must be between 0 and 2." }

        if (config.discordStatus !in 0..3)
            return LOGGER.error { "Start up halted: Discord status must be between 0 and 3." }

        if (config.useStateOnly && config.useRichPresence)
            return LOGGER.error { "Start up halted: Configuration conflict; both useStateOnly and useRichPresence are true." }

        this.defaultChannel = defaultChannel
        botScope.launch { login() }
        isInitialised = true
        LOGGER.info { "Initialisation complete.." }
    }

    @OptIn(PrivilegedIntent::class)
    private suspend fun login() {
        LOGGER.info { "Setting up DForBlock..." }
        setup()
        this.taskScheduler = DForBlockTaskScheduler(
            schedulerScope = this.taskScope,
            config = this.config,
            defaultChannel = this.defaultChannel,
            kord = this.kord,
            communicator = this.communicator
        )
        LOGGER.info { "Logging in..." }
        kord.login {
            intents = Intents.NON_PRIVILEGED + Intents(Intent.MessageContent)
        }
    }

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

    private suspend fun setup() {
        kord = Kord(config.discordToken) {
            enableShutdownHook = true
        }

        for (guildId in config.guildIds) {
            createGuildCommands(kord, guildId)
        }

        kord.on<ReadyEvent> {
            isReady = true
            LOGGER.info { "DForBlock is now ready." }
            taskScheduler.start()
            handleServerStarted()
        }

        kord.on<GuildChatInputCommandInteractionCreateEvent> { handleChatInputCommandInteraction(config) }

        kord.on<GuildButtonInteractionCreateEvent> { handleButtonInteraction(config) }

        kord.on<MessageCreateEvent> { handleMessageCreation(config) }

        kord.on<DisconnectEvent> {
            LOGGER.info { "Gateway disconnected." }
        }
    }


    private suspend fun logout() {
        LOGGER.info { "Logging out..." }
        kord.shutdown()
        LOGGER.info { "Logged out." }
        isReady = false
    }

    fun disable() {
        if (!isInitialised)
            return LOGGER.info { "Instance is not initialised, nothing to do.." }
        LOGGER.info { "Termination requested..." }
        taskScheduler.stop()
        handleServerStopped()
        botScope.launch { logout() }
        isInitialised = false
        LOGGER.info { "DForBlock disabled." }
    }

    fun handleBlockyMessage(payload: MinecraftMessageData) {
        if (!isReady)
            return LOGGER.warn { "Attempted to handle message before discord is ready, ignoring..." }

        val channel = config.channels[payload.channelName]
            ?: return LOGGER.warn { "Failed to find channel with name '${payload.channelName}', are you sure it's configured?" }

        if (channel.useWebhooks)
            createWebhookMessage(config, channel, botScope, kord, payload)
        else
            createMessage(config, channel, botScope, kord, payload)

    }

    fun handleServerStarted() {
        if (!isReady)
            return LOGGER.warn { "Attempted to send startup message before discord is ready.. message will not be sent." }

        sendDiscordMessage(
            config.formats.serverStartMessage,
            Snowflake(defaultChannel.channelId),
            botScope,
            kord
        )
    }

    fun handleServerStopped() {
        if (!isReady)
            return LOGGER.warn { "Discord disconnected before sending stopped message.. message will not be sent." }

        sendDiscordMessage(
            config.formats.serverStopMessage,
            Snowflake(defaultChannel.channelId),
            botScope,
            kord
        )
    }

    fun handlePlayerJoined(payload: PlayerJoinLeaveData) {
        if (!isReady)
            return LOGGER.warn { "Attempted to send player join message while discord is not ready... message will not be sent." }

        sendDiscordMessage(
            config.formats.playerJoinMessage
                .replace("{player}", payload.playerName)
                .replace("{prefix}", payload.prefix)
                .replace("{suffix}", payload.suffix),
            Snowflake(defaultChannel.channelId),
            botScope,
            kord
        )
    }

    fun handlePlayerLeave(payload: PlayerJoinLeaveData) {
        if (!isReady)
            return LOGGER.warn { "Attempted to send player leave message while discord is not ready... message will not be sent." }

        sendDiscordMessage(
            config.formats.playerLeaveMessage
                .replace("{player}", payload.playerName)
                .replace("{prefix}", payload.prefix)
                .replace("{suffix}", payload.suffix),
            Snowflake(defaultChannel.channelId),
            botScope,
            kord
        )
    }

    fun handlePlayerDeath(payload: PlayerDeathData) = botScope.launch {
        if (!isReady)
            return@launch LOGGER.warn { "Attempted to send player death message while discord is not ready... message will not be sent." }

        val processedContent = config.formats.playerDeathMessage
            .replace("{player}", payload.playerName)
            .replace("{prefix}", payload.prefix)
            .replace("{suffix}", payload.suffix)
            .replace("{deathMessage}", payload.deathMessage)
        try {
            kord.rest.channel.createMessage(Snowflake(defaultChannel.channelId)) {
                flags = MessageFlags(MessageFlag.IsComponentsV2)
                container {
                    accentColor = Color(Random.nextInt(0..0xFFFFFF))
                    textDisplay(processedContent)
                }
            }
        } catch (e: Exception) {
            LOGGER.warn { "Failed to send death message: ${e.stackTraceToString()}" }
        }
    }

    fun handleMCAdvancementMade(payload: MCAdvancementMadeData) = botScope.launch {
        if (!isReady)
            return@launch LOGGER.warn { "Attempted to send player advancement message while discord is not ready... message will not be sent." }

        val processedContent = config.formats.mcAdvancementMadeMessage
            .replace("{player}", payload.playerName)
            .replace("{prefix}", payload.prefix)
            .replace("{suffix}", payload.suffix)
            .replace("{name}", payload.advancementName)
            .replace("{description}", payload.advancementDescription)
        try {
            kord.rest.channel.createMessage(Snowflake(defaultChannel.channelId)) {
                flags = MessageFlags(MessageFlag.IsComponentsV2)
                container {
                    accentColor = Color(Random.nextInt(0..0xFFFFFF))
                    textDisplay(processedContent)
                }
            }
        } catch (e: Exception) {
            LOGGER.warn { "Failed to send advancement message: ${e.stackTraceToString()}" }
        }
    }


    suspend fun MessageCreateEvent.handleMessageCreation(config: DForBlockConfig) {
        if (message.author?.isBot ?: true)
            return

        try {
            message.getGuildOrNull() ?: return
        } catch (exception: RequestException) {
            LOGGER.warn { "Unexpected exception while getting guild: ${exception.stackTraceToString()}" }
            return
        }

        if (message.content.isEmpty())
            return LOGGER.warn { "detected empty discord message, are you sure you enabled the message content intent?" }

        val member = message.getAuthorAsMemberOrNull() ?: message.author
        val name = member?.effectiveName ?: "Unknown"
        val payload = DiscordMessageData(
            author = name,
            content = message.content,
            channelId = message.channelId.value,
            messageID = message.id.value,
        )
        communicator.broadcastMessage(payload, config)
    }

    suspend fun GuildChatInputCommandInteractionCreateEvent.handleChatInputCommandInteraction(config: DForBlockConfig) {
        when (interaction.invokedCommandName) {
            "playerlist" -> handlePlayerListCommand(config)
            "panel" -> sendNewPanel(config)
        }
    }

    suspend fun GuildChatInputCommandInteractionCreateEvent.handlePlayerListCommand(config: DForBlockConfig) {
        val response = interaction.deferEphemeralResponse()
        val permission = config.permissions.playerlistCommand
        if (permission != null && permission.check(interaction, reversed = true)
        ) {
            response.respond {
                content = "**You do not have permission to use this command!**"
            }
            return
        }

        val onlinePlayerlist = communicator.onlinePlayers()
        val statistics = communicator.serverStatistics()
        val body = if (onlinePlayerlist.isEmpty()) "**Server is empty.**" else onlinePlayerlist.joinToString(
            separator = ", ",
            prefix = "**Online players (${statistics.onlinePlayers}/${statistics.playerLimit}): `",
            postfix = "`**"
        )

        response.respond {
            flags = MessageFlags(MessageFlag.IsComponentsV2)
            container {
                accentColor = Color(Random.nextInt(0..0xFFFF))
                textDisplay(body)
            }
        }
    }

    suspend fun GuildChatInputCommandInteractionCreateEvent.sendNewPanel(config: DForBlockConfig) {
        val response = interaction.deferPublicResponse()
        if (config.permissions.panelCommand.check(interaction, reversed = true)) {
            response.respond {
                content = "**You do not have permission to use this command!**"
            }
            return
        }

        try {
            response.respond {
                flags = MessageFlags(MessageFlag.IsComponentsV2)

                container {
                    accentColor = Color(Random.nextInt(0..0xFFFF))
                    actionRow {
                        interactionButton(style = ButtonStyle.Primary, customId = "BUTTON_SERVER_STATUS") {
                            label = "Status"
                        }
                        interactionButton(style = ButtonStyle.Primary, customId = "BUTTON_SERVER_PLAYERS") {
                            label = "Players"
                        }
                        interactionButton(style = ButtonStyle.Secondary, customId = "BUTTON_RUN_COMMAND") {
                            label = "Run Command"
                        }
                    }
                    actionRow {
                        interactionButton(style = ButtonStyle.Danger, customId = "BUTTON_STOP_SERVER") {
                            label = "Stop"
                        }
                        if (config.panelLink != null) linkButton(url = config.panelLink) { label = "Open Panel" }
                    }
                }
            }
        } catch (exception: Exception) {
            LOGGER.error { "Failed to create panel: ${exception.stackTraceToString()}" }
        }
    }

    suspend fun GuildButtonInteractionCreateEvent.handleButtonInteraction(config: DForBlockConfig) {
        when (interaction.componentId) {
            "BUTTON_STOP_SERVER" -> { handleStopServerButton(config) }
            "BUTTON_RUN_COMMAND" -> { handleRunCommandButton(config) }
            "BUTTON_SERVER_PLAYERS" -> { handleServerPlayersButton(config) }
            "BUTTON_SERVER_STATUS" -> { handleServerStatusButton(config) }
        }
    }

    suspend fun GuildButtonInteractionCreateEvent.handleStopServerButton(config: DForBlockConfig) {
        val response = interaction.deferEphemeralResponse()

        if (config.permissions.stopButton.check(interaction, reversed = true)) {
            response.respond {
                content = "**You do not have permission to use this button!**"
            }
            return
        }

        response.respond {
            flags = MessageFlags(MessageFlag.IsComponentsV2)
            container {
                accentColor = Color(Random.nextInt(0..0xFFFF))
                textDisplay("**Stopping Server.. Goodbye.**")
            }
        }
        communicator.stopServer()
    }

    suspend fun GuildButtonInteractionCreateEvent.handleRunCommandButton(config: DForBlockConfig) {
        val response = interaction.deferEphemeralResponse()

        if (config.permissions.runCommandButton.check(interaction, reversed = true)) {
            response.respond {
                content = "**You do not have permission to use this button!**"
            }
            return
        }
    }

    suspend fun GuildButtonInteractionCreateEvent.handleServerPlayersButton(config: DForBlockConfig) {
        val response = interaction.deferEphemeralResponse()
        val permission = config.permissions.playersButton
        if (permission != null && permission.check(interaction, reversed = true)) {
            response.respond {
                content = "**You do not have permission to use this button!**"
            }
            return
        }

        val onlinePlayerlist = communicator.onlinePlayers()
        val statistics = communicator.serverStatistics()
        val body = if (onlinePlayerlist.isEmpty()) "**Server is empty.**" else onlinePlayerlist.joinToString(
            separator = ", ",
            prefix = "**Online players (${statistics.onlinePlayers}/${statistics.playerLimit}): `",
            postfix = "`**"
        )

        response.respond {
            flags = MessageFlags(MessageFlag.IsComponentsV2)
            container {
                accentColor = Color(Random.nextInt(0..0xFFFF))
                textDisplay(body)
            }
        }
    }

    suspend fun GuildButtonInteractionCreateEvent.handleServerStatusButton(config: DForBlockConfig) {
        val response = interaction.deferEphemeralResponse()
        val permission = config.permissions.statusButton
        if (permission != null && permission.check(interaction, reversed = true)) {
            response.respond {
                content = "**You do not have permission to use this button!**"
            }
            return
        }
    }

}