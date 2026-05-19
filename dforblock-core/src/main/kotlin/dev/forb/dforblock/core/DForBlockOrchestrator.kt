package dev.forb.dforblock.core

import dev.forb.dforblock.core.config.ConfigManager
import dev.forb.dforblock.core.discord.DiscordBotManager
import dev.kord.gateway.PrivilegedIntent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

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
class DForBlockOrchestrator(private val configManager: ConfigManager, private val communicator: IBlockyCommunicator) {

    var botManager: DiscordBotManager? = null
        private set

    fun launch(block: suspend DForBlockOrchestrator.() -> Unit) = botManager?.botScope?.launch { block() }

    @OptIn(PrivilegedIntent::class)
    fun start() {
        LOGGER.info { "DForBlock starting..." }
        botManager = DiscordBotManager(this, configManager, communicator)

        val isConfigLoaded = configManager.load()

        if (!isConfigLoaded)
            return LOGGER.error { "Start up halted: Failed to load config file." }

        botManager?.start() ?: LOGGER.error { "Failed to start DForBlock, botManager was null." }
    }

    fun disable() = runBlocking(Dispatchers.Default) {
        if (!(botManager?.isInitialised ?: false)) {
            botManager?.botScope?.coroutineContext?.cancelChildren()
                ?: LOGGER.error { "Disable called but botManager is null." }
            LOGGER.info { "Mod was not running, not much to do.." }
            return@runBlocking
        }

        LOGGER.info { "Termination requested..." }
        val stopJob = onServerStop()
        withTimeoutOrNull(10.seconds) { stopJob?.join() }
        botManager?.stop()
        botManager = null
        LOGGER.info { "DForBlock disabled." }
    }

    /**
     * Game Event Handlers
     */

    suspend fun onBlockyMessageReceive(payload: GameMessageData) = botManager?.let { botManager ->
        if (!botManager.isReady)
            return@let LOGGER.warn { "Attempted to handle game message before discord is ready, message will not be sent." }

        val template = configManager.messages.playerChats ?: return@let
        if (!template.isEnabled) return@let

        val placeholders = (
                buildCommonPlaceholders(communicator) + buildPlayerPlaceholders(
                    payload.playerIdentity,
                    configManager,
                    communicator
                )
                        + mapOf("{messageContent}" to payload.messageContent, "{channelName}" to payload.channelName)
                )
        val channel = configManager.channels[payload.channelName] ?: configManager.channels[template.targetChannel]
        ?: return@let LOGGER.warn { "Failed to find channel with name '${payload.channelName}', are you sure it's configured?" }

        botManager.kord?.let { kord ->
            botManager.botScope.launch {
                val success = channel.createMessage(
                    kord, template, payload.playerIdentity,
                    configManager, communicator, placeholders
                )
                if (!success) {
                    LOGGER.warn { "Failed to handle game message." }
                }
            }
        } ?: LOGGER.error { "Tried to handle game message but kord is null." }
    } ?: LOGGER.error { "Tried to handle game message but botManager is null." }

    suspend fun onServerStart() = botManager?.let { botManager ->
        if (!botManager.isReady)
            return@let LOGGER.warn { "Attempted to send startup message before discord is ready, message will not be sent." }

        val template = configManager.messages.serverStarts ?: return@let
        if (!template.isEnabled) return@let
        val placeholders = buildCommonPlaceholders(communicator)
        val channel = configManager.channels[template.targetChannel]
            ?: return@let LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botManager.kord?.let { kord ->
            botManager.botScope.launch {
                val success = channel.createMessage(
                    kord,
                    template,
                    null,
                    configManager,
                    communicator,
                    placeholders
                )
                if (!success) {
                    LOGGER.warn { "Failed to handle server start event." }
                }
            }
        } ?: LOGGER.error { "Tried to handle server start event but kord is null." }
    } ?: LOGGER.error { "Tried to handle server start event but botManager is null." }

    suspend fun onServerStop(): Job? {
        if (botManager == null) {
            LOGGER.error { "Tried to handle server stop event but botManager is null." }
            return null
        }

        botManager?.let { botManager ->
            if (!botManager.isReady) {
                LOGGER.warn { "Attempted to send shutdown message while discord is not ready, message will not be sent." }
                return null
            }

            val template = configManager.messages.serverStops ?: return null
            if (!template.isEnabled) return null
            val placeholders = buildCommonPlaceholders(communicator)
            val channel = configManager.channels[template.targetChannel]
                ?: run {
                    LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }
                    return null
                }
            botManager.kord?.let { kord ->
                return botManager.botScope.launch {
                    val success = channel.createMessage(
                        kord, template, null,
                        configManager, communicator, placeholders
                    )
                    if (!success) {
                        LOGGER.warn { "Failed to handle server stop event." }
                    }
                }
            } ?: LOGGER.error { "Tried to handle server stop event but kord is null." }
        } ?: LOGGER.error { "Tried to handle server stop event but botManager is null." }

        return null
    }

    suspend fun onPlayerJoin(payload: PlayerJoinLeaveData) = botManager?.let { botManager ->
        if (!botManager.isReady)
            return@let LOGGER.warn { "Attempted to handle player join before discord is ready, message will not be sent." }

        val template = configManager.messages.playerJoins ?: return@let
        if (!template.isEnabled) return@let

        val placeholders = buildCommonPlaceholders(communicator) + buildPlayerPlaceholders(
            payload.playerIdentity,
            configManager,
            communicator
        )
        val channel = configManager.channels[template.targetChannel]
            ?: return@let LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botManager.kord?.let { kord ->
            botManager.botScope.launch {
                val success = channel.createMessage(
                    kord, template, payload.playerIdentity,

                    configManager, communicator, placeholders
                )
                if (!success) {
                    LOGGER.warn { "Failed to handle player join event." }
                }
            }
        } ?: LOGGER.error { "Tried to handle player join event but kord is null." }
    } ?: LOGGER.error { "Tried to handle player join event but botManager is null." }

    suspend fun onPlayerLeave(payload: PlayerJoinLeaveData) = botManager?.let { botManager ->
        if (!botManager.isReady)
            return@let LOGGER.warn { "Attempted to handle player leave before discord is ready, message will not be sent." }

        val template = configManager.messages.playerLeaves ?: return@let
        if (!template.isEnabled) return@let

        val placeholders = buildCommonPlaceholders(communicator) + buildPlayerPlaceholders(
            payload.playerIdentity,
            configManager,
            communicator
        )
        val channel = configManager.channels[template.targetChannel]
            ?: return@let LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }
        botManager.kord?.let { kord ->
            botManager.botScope.launch {
                val success = channel.createMessage(
                    kord, template, payload.playerIdentity,

                    configManager, communicator, placeholders
                )
                if (!success) {
                    LOGGER.warn { "Failed to handle player leave event." }
                }
            }
        } ?: LOGGER.error { "Tried to handle player leave event but kord is null." }
    } ?: LOGGER.error { "Tried to handle player leave event but botManager is null." }

    suspend fun onPlayerDeath(payload: PlayerDeathData) = botManager?.let { botManager ->
        if (!botManager.isReady)
            return@let LOGGER.warn { "Attempted to handle player death before discord is ready, message will not be sent." }

        val template = configManager.messages.playerDies ?: return@let
        if (!template.isEnabled) return@let

        val placeholders = (
                buildCommonPlaceholders(communicator)
                        + buildPlayerPlaceholders(
                    payload.playerIdentity,
                    configManager, communicator
                )
                        + mapOf("{deathMessage}" to payload.deathMessage)
                )
        val channel = configManager.channels[template.targetChannel]
            ?: return@let LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }
        botManager.kord?.let { kord ->
            botManager.botScope.launch {
                val success = channel.createMessage(
                    kord, template, payload.playerIdentity,

                    configManager, communicator, placeholders
                )
                if (!success) {
                    LOGGER.warn { "Failed to handle player death event." }
                }
            }
        } ?: LOGGER.error { "Tried to handle player death event but kord is null." }
    } ?: LOGGER.error { "Tried to handle player death event but botManager is null." }

    suspend fun onMinecraftAdvancement(payload: MCAdvancementMadeData) = botManager?.let { botManager ->
        if (!botManager.isReady)
            return@let LOGGER.warn { "Attempted to handle mc player advancement before discord is ready, message will not be sent." }

        val template = configManager.messages.mcPlayerAdvances ?: return@let
        if (!template.isEnabled) return@let

        val placeholders = (
                buildCommonPlaceholders(communicator)
                        + buildPlayerPlaceholders(payload.playerIdentity, configManager, communicator)
                        + mapOf(
                    "{advancementName}" to payload.advancementName,
                    "{advancementDescription}" to payload.advancementDescription,
                    "{advancementType}" to payload.advancementType,
                    "{actionType}" to payload.actionType
                )
                )
        val channel = configManager.channels[template.targetChannel]
            ?: return@let LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }
        botManager.kord?.let { kord ->
            botManager.botScope.launch {
                val success = channel.createMessage(
                    kord, template, payload.playerIdentity,

                    configManager, communicator, placeholders
                )
                if (!success) {
                    LOGGER.warn { "Failed to handle mc player advancement event." }
                }
            }
        } ?: LOGGER.error { "Tried to handle mc player advancement event but kord is null." }
    } ?: LOGGER.error { "Tried to handle mc player advancement but kord is null." }

}