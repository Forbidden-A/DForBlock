package dev.forb.dforblock.core

import dev.forb.dforblock.core.config.ConfigManager
import dev.kord.common.Color
import dev.kord.common.entity.*
import dev.kord.common.exception.RequestException
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.effectiveName
import dev.kord.core.entity.interaction.ActionInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.entity.interaction.GuildInteraction
import dev.kord.core.entity.interaction.GuildModalSubmitInteraction
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
* This is the core of this project
*  */
class DForBlock(private val configManager: ConfigManager, private val communicator: IBlockyCommunicator) {

    /**
     * Variables
     */

    private val botScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val taskScope = CoroutineScope(Dispatchers.Default + SupervisorJob(botScope.coroutineContext[Job]))

    private lateinit var kord: Kord

    lateinit var taskScheduler: DForBlockTaskScheduler
        private set

    var isInitialised: Boolean = false
        private set

    var isReady: Boolean = false
        private set

    /**
     * Discord setup
     */

    fun start() {
        LOGGER.info { "DForBlock starting..." }

        val isConfigLoaded = configManager.load()

        if (!isConfigLoaded)
            return LOGGER.error { "Start up halted: Failed to load config file." }

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
            configManager = configManager,
            kord = this.kord,
            communicator = this.communicator
        )
        LOGGER.info { "Logging in..." }
        kord.login {
            intents = Intents.NON_PRIVILEGED + Intents(Intent.MessageContent)
        }
    }

    private suspend fun setup() {
        kord = Kord(configManager.core.discordToken) {
            enableShutdownHook = true
        }

        for (guildId in configManager.core.guildIds) {
            createGuildCommands(kord, guildId)
        }

        kord.on<ReadyEvent> {
            isReady = true
            LOGGER.info { "DForBlock is now ready." }
            taskScheduler.start()
            onServerStart()
        }

        kord.on<GuildChatInputCommandInteractionCreateEvent> { onDiscordChatCommand() }

        kord.on<GuildButtonInteractionCreateEvent> { onDiscordButtonPress() }

        kord.on<GuildModalSubmitInteractionCreateEvent> { onDiscordModalSubmit() }

        kord.on<MessageCreateEvent> { onDiscordMessageReceive() }

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
        onServerStop()
        botScope.launch { logout() }
        isInitialised = false
        LOGGER.info { "DForBlock disabled." }
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

    /**
     * Discord Event Handlers
     */

    suspend fun GuildChatInputCommandInteractionCreateEvent.onDiscordChatCommand() {
        when (interaction.invokedCommandName) {
            "playerlist" -> showOnlinePlayers(interaction)
            "panel" -> sendNewPanel(interaction)
        }
    }

    suspend fun GuildButtonInteractionCreateEvent.onDiscordButtonPress() {
        when (interaction.componentId) {
            "BUTTON_STOP_SERVER" -> {
                handleStopServerButton(interaction)
            }

            "BUTTON_RUN_COMMAND" -> {
                handleRunCommandButton(interaction)
            }

            "BUTTON_SERVER_PLAYERS" -> {
                showOnlinePlayers(interaction)
            }

            "BUTTON_SERVER_STATUS" -> {
                handleServerStatusButton(interaction)
            }
        }
    }

    suspend fun GuildModalSubmitInteractionCreateEvent.onDiscordModalSubmit() {
        if (interaction.modalId == "MODAL_RUN_COMMAND")
            handleCommandRunModalSubmission(interaction)
    }

    suspend fun MessageCreateEvent.onDiscordMessageReceive() {
        if (message.author?.isBot ?: true)
            return

        try {
            message.getGuildOrNull() ?: return
        } catch (exception: RequestException) {
            LOGGER.warn { "Unexpected exception while getting guild: ${exception.stackTraceToString()}" }
            return
        }

        if (message.content.isEmpty()) {
            message.attachments.ifEmpty { LOGGER.warn { "detected empty discord message, are you sure you enabled the message content intent?" } }
            return
        }
        val member = message.getAuthorAsMemberOrNull() ?: message.author
        val name = member?.effectiveName ?: "Unknown"
        val payload = DiscordMessageData(
            author = name,
            content = message.content,
            channelId = message.channelId.value,
            messageID = message.id.value,
        )
        communicator.broadcastMessage(payload)
    }

    suspend fun <I> showOnlinePlayers(
        interaction: I,
    ) where I : ActionInteraction, I : GuildInteraction {
        val response = interaction.deferEphemeralResponse()
        val permission = configManager.permissions.playerlistCommand
        if (permission.check(interaction, reversed = true)
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

    suspend fun <I> handleServerStatusButton(
        interaction: I,
    ) where I : ActionInteraction, I : GuildInteraction {
        val response = interaction.deferEphemeralResponse()
        val permission = configManager.permissions.statusButton
        if (permission.check(interaction, reversed = true)) {
            response.respond {
                content = "**You do not have permission to use this button!**"
            }
            return
        }
        val statistics = communicator.serverStatistics()
        val mspt = "%.2f".format(statistics.mspt)
        val tps = "${"%.2f".format(statistics.tps)}/${"%.1f".format(statistics.targetTps)}"
        val body = """
            **Game**: ${statistics.gameType.name} ${statistics.gameVersion}
            **Players**: ${statistics.onlinePlayers}/${statistics.playerLimit}
            **Uptime**: ${statistics.startup.duration.beautify}
            **Running at**: ${mspt}mspt @ ${tps}tps
        """.trimIndent()
        response.respond {
            flags = MessageFlags(MessageFlag.IsComponentsV2)
            container {
                accentColor = Color(Random.nextInt(0..0xFFFF))
                textDisplay(body)
            }
        }
    }

    suspend fun <I> sendNewPanel(
        interaction: I,
    ) where I : ActionInteraction, I : GuildInteraction {
        val response = interaction.deferPublicResponse()
        if (configManager.permissions.panelCommand.check(interaction, reversed = true)) {
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
                        if (configManager.core.serverPanelUrl != null)
                            linkButton(url = configManager.core.serverPanelUrl ?: "") { label = "Open Panel" }
                    }
                }
            }
        } catch (exception: Exception) {
            LOGGER.error { "Failed to create panel: ${exception.stackTraceToString()}" }
        }
    }

    suspend fun <I> handleStopServerButton(
        interaction: I,
    ) where I : ActionInteraction, I : GuildInteraction {
        val response = interaction.deferEphemeralResponse()

        if (configManager.permissions.stopButton.check(interaction, reversed = true)) {
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

    suspend fun <I> handleRunCommandButton(
        interaction: I,
    ) where I : ComponentInteraction, I : GuildInteraction {
        if (configManager.permissions.runCommandButton.check(interaction, reversed = true)) {
            interaction.respondEphemeral {
                content = "**You do not have permission to use this button!**"
            }
            return
        }

        interaction.modal("Run Command", "MODAL_RUN_COMMAND") {
            label("Command") {
                textInput(TextInputStyle.Short, "MODAL_INPUT_COMMAND") {
                    placeholder = "time set day"
                    required = true
                    allowedLength = 1..1000
                }
            }
        }
    }

    suspend fun handleCommandRunModalSubmission(interaction: GuildModalSubmitInteraction) {
        val response = interaction.deferEphemeralResponse()
        val commandContent = interaction.textInputs["MODAL_INPUT_COMMAND"]?.value ?: return
        val command = commandContent.split(" ").firstOrNull() ?: return
        if (!configManager.permissions.allowedCommands.isAllowed(command)) {
            response.respond {
                flags = MessageFlags(MessageFlag.IsComponentsV2)
                container {
                    accentColor = Color(Random.nextInt(0..0xFFFF))
                    textDisplay("**This command is not allowed.**")
                }
            }
            return
        }

        val commandResult = communicator.executeCommand(commandContent)
        response.respond {
            flags = MessageFlags(MessageFlag.IsComponentsV2)
            container {
                accentColor = Color(Random.nextInt(0..0xFFFF))
                textDisplay("**Executed:** ${commandContent.take(50)}")
                separator(SeparatorSpacingSize.Small)
                textDisplay("**Result:** ```txt\n${commandResult.take(500)}```")
            }
        }
    }

    /**
     * Game Event Handlers
     */

    fun onBlockyMessageReceive(payload: GameMessageData) {
        if (!isReady)
            return LOGGER.warn { "Attempted to handle message before discord is ready, message will not be sent." }

        val template = configManager.messages.playerChats ?: return
        if (!template.isEnabled) return

        val placeholders = (
                buildCommonPlaceholders(communicator)
                        + buildPlayerPlaceholders(
                    payload.playerName,
                    payload.playerUuid,
                    payload.prefix,
                    payload.suffix
                )
                        + mapOf("{messageContent}" to payload.messageContent, "{channelName}" to payload.channelName)
                )
        val channel = configManager.channels[payload.channelName] ?: configManager.channels[template.targetChannel]
        ?: return LOGGER.warn { "Failed to find channel with name '${payload.channelName}', are you sure it's configured?" }

        botScope.launch {
            val success = channel.createMessage(kord, template, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle game message received." }
            }
        }
    }

    fun onServerStart() {
        if (!isReady)
            return LOGGER.warn { "Attempted to send startup message before discord is ready, message will not be sent." }

        val template = configManager.messages.serverStarts ?: return
        if (!template.isEnabled) return
        val placeholders = buildCommonPlaceholders(communicator)
        val channel = configManager.channels[template.targetChannel]
            ?: return LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botScope.launch {
            val success = channel.createMessage(kord, template, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle server start event." }
            }
        }
    }

    fun onServerStop() {
        if (!isReady)
            return LOGGER.warn { "Attempted to send shutdown message while discord is not ready, message will not be sent." }

        val template = configManager.messages.serverStops ?: return
        if (!template.isEnabled) return
        val placeholders = buildCommonPlaceholders(communicator)
        val channel = configManager.channels[template.targetChannel]
            ?: return LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botScope.launch {
            val success = channel.createMessage(kord, template, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle server stop event." }
            }
        }
    }

    fun onPlayerJoin(payload: PlayerJoinLeaveData) {
        if (!isReady)
            return LOGGER.warn { "Attempted to handle player join before discord is ready, message will not be sent." }

        val template = configManager.messages.playerJoins ?: return
        if (!template.isEnabled) return

        val placeholders = buildCommonPlaceholders(communicator) + buildPlayerPlaceholders(
            payload.playerName,
            payload.playerUuid,
            payload.prefix,
            payload.suffix
        )
        val channel = configManager.channels[template.targetChannel]
            ?: return LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botScope.launch {
            val success = channel.createMessage(kord, template, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle player join event." }
            }
        }
    }

    fun onPlayerLeave(payload: PlayerJoinLeaveData) {
        if (!isReady)
            return LOGGER.warn { "Attempted to handle player leave before discord is ready, message will not be sent." }

        val template = configManager.messages.playerLeaves ?: return
        if (!template.isEnabled) return

        val placeholders = buildCommonPlaceholders(communicator) + buildPlayerPlaceholders(
            payload.playerName,
            payload.playerUuid,
            payload.prefix,
            payload.suffix
        )
        val channel = configManager.channels[template.targetChannel]
            ?: return LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botScope.launch {
            val success = channel.createMessage(kord, template, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle player leave event." }
            }
        }
    }

    fun onPlayerDeath(payload: PlayerDeathData) {
        if (!isReady)
            return LOGGER.warn { "Attempted to handle player death before discord is ready, message will not be sent." }

        val template = configManager.messages.playerDies ?: return
        if (!template.isEnabled) return

        val placeholders = (
                buildCommonPlaceholders(communicator)
                        + buildPlayerPlaceholders(
                    payload.playerName,
                    payload.playerUuid,
                    payload.prefix,
                    payload.suffix
                )
                        + mapOf("{deathMessage}" to payload.deathMessage)
                )
        val channel = configManager.channels[template.targetChannel]
            ?: return LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botScope.launch {
            val success = channel.createMessage(kord, template, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle player death event." }
            }
        }
    }

    fun onMinecraftAdvancement(payload: MCAdvancementMadeData) {
        if (!isReady)
            return LOGGER.warn { "Attempted to handle mc player advancement before discord is ready, message will not be sent." }

        val template = configManager.messages.mcPlayerAdvances ?: return
        if (!template.isEnabled) return

        val placeholders = (
                buildCommonPlaceholders(communicator)
                        + buildPlayerPlaceholders(
                    payload.playerName,
                    payload.playerUuid,
                    payload.prefix,
                    payload.suffix
                )
                        + mapOf(
                    "{advancementName}" to payload.advancementName,
                    "{advancementDescription}" to payload.advancementDescription
                )
                )
        val channel = configManager.channels[template.targetChannel]
            ?: return LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botScope.launch {
            val success = channel.createMessage(kord, template, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle player death event." }
            }
        }
    }

}