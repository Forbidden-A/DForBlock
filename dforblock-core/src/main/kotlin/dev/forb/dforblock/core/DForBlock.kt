package dev.forb.dforblock.core

import dev.kord.common.Color
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.MessageFlags
import dev.kord.common.entity.Snowflake
import dev.kord.common.exception.RequestException
import dev.kord.core.Kord
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.gateway.DisconnectEvent
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.NON_PRIVILEGED
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.container
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.random.nextInt

/*
* This is the entry point of this project
*  */
object DForBlock {

    val LOGGER = KotlinLogging.logger{}

    val json = Json {
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    var isEnabled: Boolean = false
        private set

    var isReady: Boolean = false
        private set

    private lateinit var communicator: IBlockyCommunicator

    private lateinit var config: DForBlockConfig

    private lateinit var kord: Kord

    private val botScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun enable(icommunicator: IBlockyCommunicator) {
        LOGGER.info { "DForBlock starting..." }
        communicator = icommunicator

        if (!communicator.ensureConfigFile())
            return LOGGER.error { "Start up halted: Failed to load config file." }

        try {
            config = loadConfig(communicator.getConfigFile(), json)
        } catch (e: Exception) {
            return LOGGER.error { "Failed to load config, start up halted: ${e.message}" }
        }

        if (config.channels["default"] == null)
            return LOGGER.error { "You must configure a channel with the name 'default'." }

        botScope.launch { start() }
        isEnabled = true
        LOGGER.info { "DForBlock enabled." }
    }

    private suspend fun initialise() {
        kord = Kord(config.discordToken) {
            enableShutdownHook = true
        }

        kord.on<ReadyEvent> {
            isReady = true
            LOGGER.info { "DForBlock is now ready." }
            handleServerStarted()
        }

        kord.on<MessageCreateEvent> {
            if (message.author?.isBot ?: true)
                return@on

            try {
                message.getGuildOrNull() ?: return@on
            } catch (exception: RequestException) {
                LOGGER.warn { "Unexpected exception while getting guild: ${exception.message}" }
                return@on
            }

            if (message.content.isEmpty())
                return@on LOGGER.warn { "detected empty discord message, are you sure you enabled the message content intent?" }

            val member = message.getAuthorAsMemberOrNull() ?: message.author
            val name = member?.effectiveName ?: "Unknown"
            val payload = DiscordMessagePayload(
                author = name,
                content = message.content,
                channelId = message.channelId.value,
                messageID = message.id.value,
            )
            communicator.broadcastMessage(payload, config)
        }

        kord.on<DisconnectEvent> {
            LOGGER.info { "Gateway disconnected." }
        }
    }

    @OptIn(PrivilegedIntent::class)
    private suspend fun start() {
        LOGGER.info { "Initialising DForBlock..." }
        initialise()
        LOGGER.info { "Logging in..." }
        kord.login {
            intents = Intents.NON_PRIVILEGED + Intents(Intent.MessageContent)
        }
    }

    private suspend fun stop() {
        LOGGER.info { "Logging out..." }
        kord.shutdown()
        LOGGER.info { "Logged out." }
        isReady = false
    }

    fun disable() {
        if (!isEnabled)
            return LOGGER.info { "Mod is not enabled, nothing to do!" }

        LOGGER.info { "Termination requested..." }
        handleServerStopped()
        botScope.launch { stop() }
        isEnabled = false
        LOGGER.info { "DForBlock disabled." }
    }

    fun handleBlockyMessage(payload: BlockyMessagePayload) {
        if (!isReady) {
            LOGGER.warn { "Attempted to handle message before discord is ready, ignoring..." }
            return
        }

        val channel = config.channels[payload.channelName]
            ?: return LOGGER.warn { "Failed to find channel with name '${payload.channelName}', are you sure it's configured?" }

        if (channel.useWebhooks)
            createWebhookMessage(config, channel, botScope, kord, payload)
        else
            createMessage(config, channel, botScope, kord, payload)

    }

    fun handleServerStarted() {
        val channel = config.channels["default"]
            ?: return LOGGER.error { "Couldn't find default channel, how did we reach this point?" }

        val channelId = channel.channelId

        sendDiscordMessage(
            config.formats.serverStartMessage,
            Snowflake(channelId),
            botScope,
            kord
        )
    }

    fun handleServerStopped() {
        val channel = config.channels["default"]
            ?: return LOGGER.error { "Couldn't find default channel, how did we reach this point?" }

        val channelId = channel.channelId


        sendDiscordMessage(
            config.formats.serverStopMessage,
            Snowflake(channelId),
            botScope,
            kord
        )

    }

    fun handlePlayerJoined(payload: PlayerJoinLeavePayload) {
        val channel = config.channels["default"]
            ?: return LOGGER.error { "Couldn't find default channel, how did we reach this point?" }

        val channelId = channel.channelId

        sendDiscordMessage(
            config.formats.playerJoinMessage
                .replace("{player}", payload.playerName)
                .replace("{prefix}", payload.prefix)
                .replace("{suffix}", payload.suffix),
            Snowflake(channelId),
            botScope,
            kord
        )
    }

    fun handlePlayerLeave(payload: PlayerJoinLeavePayload) {
        val channel = config.channels["default"]
            ?: return LOGGER.error { "Couldn't find default channel, how did we reach this point?" }

        val channelId = channel.channelId

        sendDiscordMessage(
            config.formats.playerLeaveMessage
                .replace("{player}", payload.playerName)
                .replace("{prefix}", payload.prefix)
                .replace("{suffix}", payload.suffix),
            Snowflake(channelId),
            botScope,
            kord
        )
    }

    fun handlePlayerDeath(payload: PlayerDeathPayload) {
        val channel = config.channels["default"]
            ?: return LOGGER.error { "Couldn't find default channel, how did we reach this point?" }

        val channelId = channel.channelId

        val processedContent = config.formats.playerDeathMessage
            .replace("{player}", payload.playerName)
            .replace("{prefix}", payload.prefix)
            .replace("{suffix}", payload.suffix)
            .replace("{deathMessage}", payload.deathMessage)

        botScope.launch {
            try {
                kord.rest.channel.createMessage(Snowflake(channelId)) {
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
    }

    fun handleMCAdvancementMade(payload: MCAdvancementMadePayload) {
        val channel = config.channels["default"]
            ?: return LOGGER.error { "Couldn't find default channel, how did we reach this point?" }

        val channelId = channel.channelId
        val processedContent = config.formats.mcAdvancementMadeMessage
            .replace("{player}", payload.playerName)
            .replace("{prefix}", payload.prefix)
            .replace("{suffix}", payload.suffix)
            .replace("{name}", payload.advancementName)
            .replace("{description}", payload.advancementDescription)

        botScope.launch {
            try {
                kord.rest.channel.createMessage(Snowflake(channelId)) {
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
    }
}