package dev.forb.dforblock

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
import dev.kord.rest.builder.component.mediaGallery
import dev.kord.rest.builder.component.textDisplay
import dev.kord.rest.builder.message.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.logging.Logger

/*
* This is the entry point of this project
*  */
object DForBlock {

    val logger: Logger = Logger.getLogger("DForBlock.core")

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
        logger.info { "DForBlock starting..." }
        communicator = icommunicator

        if (!communicator.ensureConfigFile())
            return logger.severe { "Start up halted: Failed to load config file." }

        try {
            config = loadConfig(communicator.getConfigFile(), json)
        } catch (e: Exception) {
            return logger.severe { "Failed to load config, start up halted: ${e.message}" }
        }

        if (config.channels["default"] == null)
            return logger.severe { "You must configure a channel with the name 'default'." }

        botScope.launch { start() }
        isEnabled = true
        logger.info { "DForBlock enabled." }
    }

    private suspend fun initialise() {
        kord = Kord(config.discordToken) {
            enableShutdownHook = true
        }

        kord.on<ReadyEvent> {
            isReady = true
            logger.info { "DForBlock is now ready." }
            handleServerStarted()
        }

        kord.on<MessageCreateEvent> {
            if (message.author?.isBot ?: true)
                return@on

            try {
                 message.getGuildOrNull() ?: return@on
            } catch (exception: RequestException) {
                logger.warning { "Unexpected exception while getting guild: ${exception.message}" }
                return@on
            }

            if (message.content.isEmpty())
                return@on logger.warning { "detected empty discord message, are you sure you enabled the message content intent?"}

            val member = message.getAuthorAsMemberOrNull()
            val name = member?.effectiveName ?: message.author?.effectiveName ?: "Unknown"
            val payload = DiscordMessagePayload(
                author = name,
                content = message.content,
                channelId = message.channelId.value,
                messageID = message.id.value,
            )
            communicator.broadcastMessage(payload, config)
        }

        kord.on<DisconnectEvent> {
            logger.info { "Gateway disconnected." }
        }
    }

    @OptIn(PrivilegedIntent::class)
    private suspend fun start() {
        logger.info { "Initialising DForBlock..." }
        initialise()
        logger.info { "Logging in..." }
        kord.login {
            intents = Intents.NON_PRIVILEGED + Intents(Intent.MessageContent)
        }
    }

    private suspend fun stop() {
        logger.info { "Logging out..." }
        kord.shutdown()
    }

    fun disable() {
        if (!isEnabled)
            return logger.info { "Mod is not enabled, nothing to do!" }

        logger.info { "Termination requested..." }
        handleServerStopped()
        botScope.launch { stop() }
        isEnabled = false
        logger.info { "DForBlock disabled." }
    }

    fun handleBlockyMessage(payload: BlockyMessagePayload) {
        if (!isReady) {
            logger.warning { "Attempted to handle message before discord is ready, ignoring..." }
            return
        }

        val channel = config.channels[payload.channelName]
            ?: return logger.warning { "Failed to find channel with name '${payload.channelName}', are you sure it's configured?" }

        if (channel.useWebhooks)
            createWebhookMessage(config, channel, botScope, kord, payload)
        else
            createMessage(config, channel, botScope, kord, payload)

    }

    fun handleServerStarted() {
        val channel = config.channels["default"]
            ?: return logger.severe { "Couldn't find default channel id, how did we reach this point?" }

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
            ?: return logger.severe { "Couldn't find default channel id, how did we reach this point?" }

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
            ?: return logger.severe { "Couldn't find default channel id, how did we reach this point?" }

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
            ?: return logger.severe { "Couldn't find default channel id, how did we reach this point?" }

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
            ?: return logger.severe { "Couldn't find default channel id, how did we reach this point?" }

        val channelId = channel.channelId

        val processedContent = config.formats.playerDeathMessage
            .replace("{player}", payload.playerName)
            .replace("{prefix}", payload.prefix)
            .replace("{suffix}", payload.suffix)
            .replace("{deathMessage}", payload.deathMessage)

        sendDiscordMessage(
            processedContent,
            Snowflake(channelId),
            botScope,
            kord
        )
    }

    fun handleMCAdvancementMade(payload: MCAdvancementMadePayload) {
        val channel = config.channels["default"]
            ?: return logger.severe { "Couldn't find default channel id, how did we reach this point?" }

        val channelId = channel.channelId

        val format = when(payload.type) {
            MCAdvancementMadePayload.MCAdvancementType.GOAL -> config.formats.mcGoalAdvancementMessage
            MCAdvancementMadePayload.MCAdvancementType.CHALLENGE -> config.formats.mcChallengeAdvancementMessage
            MCAdvancementMadePayload.MCAdvancementType.TASK -> config.formats.mcTaskAdvancementMessage
        }

        botScope.launch {
            try {
                kord.rest.channel.createMessage(Snowflake(channelId)) {
                    container {
                        payload.skinHint?.let { skinHint ->
                            mediaGallery {
                                item(config.minecraftAvatarProviderUrl
                                    .replace("{uuid}", skinHint.uuid.toString())
                                    .replace("{username}", skinHint.username),
                                )
                            }
                        }
                        textDisplay {
                            content = format
                                .replace("{player}", payload.playerName)
                                .replace("{prefix}", payload.prefix)
                                .replace("{suffix}", payload.suffix)
                                .replace("{name}", payload.advancementName)
                                .replace("{description}", payload.advancementDescription)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warning { "Failed to send advancement message: ${e.message}" }
            }
        }
    }
}