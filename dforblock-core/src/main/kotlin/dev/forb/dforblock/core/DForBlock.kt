package dev.forb.dforblock.core

import dev.forb.dforblock.core.config.ConfigManager
import dev.forb.dforblock.core.discord.DiscordBotManager
import dev.kord.common.entity.*
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
class DForBlock(private val configManager: ConfigManager, private val communicator: IBlockyCommunicator) {

    lateinit var botManager: DiscordBotManager
        private set

    @OptIn(PrivilegedIntent::class)
    fun start() {
        LOGGER.info { "DForBlock starting..." }
        botManager = DiscordBotManager(this, configManager, communicator)

        val isConfigLoaded = configManager.load()

        if (!isConfigLoaded)
            return LOGGER.error { "Start up halted: Failed to load config file." }

        botManager.start()
    }

    fun disable() = runBlocking(Dispatchers.Default) {
        if (!botManager.isInitialised) {
            botManager.botScope.coroutineContext.cancelChildren()
            LOGGER.info { "Mod was not running, not much to do.." }
            return@runBlocking
        }

        LOGGER.info { "Termination requested..." }
        val stopJob = onServerStop()
        withTimeoutOrNull(5.seconds){ stopJob?.join() }
        botManager.stop()
        LOGGER.info { "DForBlock disabled." }
    }

    /**
     * Game Event Handlers
     */

    fun onBlockyMessageReceive(payload: GameMessageData) {
        if (!botManager.isReady)
            return LOGGER.warn { "Attempted to handle message before discord is ready, message will not be sent." }

        val template = configManager.messages.playerChats ?: return
        if (!template.isEnabled) return

        val placeholders = (
                buildCommonPlaceholders(communicator)
                        + buildPlayerPlaceholders(
                    payload.playerIdentity,
                    configManager,
                    communicator
                )
                        + mapOf("{messageContent}" to payload.messageContent, "{channelName}" to payload.channelName)
                )
        val channel = configManager.channels[payload.channelName] ?: configManager.channels[template.targetChannel]
        ?: return LOGGER.warn { "Failed to find channel with name '${payload.channelName}', are you sure it's configured?" }

        botManager.botScope.launch {
            val success = channel.createMessage(botManager.kord, template, payload.playerIdentity,
                configManager, communicator, placeholders, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle game message received." }
            }
        }
    }

    fun onServerStart() {
        if (!botManager.isReady)
            return LOGGER.warn { "Attempted to send startup message before discord is ready, message will not be sent." }

        val template = configManager.messages.serverStarts ?: return
        if (!template.isEnabled) return
        val placeholders = buildCommonPlaceholders(communicator)
        val channel = configManager.channels[template.targetChannel]
            ?: return LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botManager.botScope.launch {
            val success = channel.createMessage(botManager.kord, template, null, configManager, communicator, placeholders, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle server start event." }
            }
        }
    }

    fun onServerStop(): Job? {
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

        return botManager.botScope.launch {
            val success = channel.createMessage(botManager.kord, template, null,
                configManager, communicator, placeholders, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle server stop event." }
            }
        }
    }

    fun onPlayerJoin(payload: PlayerJoinLeaveData) {
        if (!botManager.isReady)
            return LOGGER.warn { "Attempted to handle player join before discord is ready, message will not be sent." }

        val template = configManager.messages.playerJoins ?: return
        if (!template.isEnabled) return

        val placeholders = buildCommonPlaceholders(communicator) + buildPlayerPlaceholders(
            payload.playerIdentity,
            configManager,
            communicator
        )
        val channel = configManager.channels[template.targetChannel]
            ?: return LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botManager.botScope.launch {
            val success = channel.createMessage(botManager.kord, template,  payload.playerIdentity,

                configManager, communicator, placeholders, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle player join event." }
            }
        }
    }

    fun onPlayerLeave(payload: PlayerJoinLeaveData) {
        if (!botManager.isReady)
            return LOGGER.warn { "Attempted to handle player leave before discord is ready, message will not be sent." }

        val template = configManager.messages.playerLeaves ?: return
        if (!template.isEnabled) return

        val placeholders = buildCommonPlaceholders(communicator) + buildPlayerPlaceholders(
            payload.playerIdentity,
            configManager,
            communicator
        )
        val channel = configManager.channels[template.targetChannel]
            ?: return LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botManager.botScope.launch {
            val success = channel.createMessage(botManager.kord, template, payload.playerIdentity,

                configManager, communicator, placeholders, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle player leave event." }
            }
        }
    }

    fun onPlayerDeath(payload: PlayerDeathData) {
        if (!botManager.isReady)
            return LOGGER.warn { "Attempted to handle player death before discord is ready, message will not be sent." }

        val template = configManager.messages.playerDies ?: return
        if (!template.isEnabled) return

        val placeholders = (
                buildCommonPlaceholders(communicator)
                        + buildPlayerPlaceholders(
                    payload.playerIdentity,
                            configManager, communicator
                )
                        + mapOf("{deathMessage}" to payload.deathMessage)
                )
        val channel = configManager.channels[template.targetChannel]
            ?: return LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botManager.botScope.launch {
            val success = channel.createMessage(botManager.kord, template, payload.playerIdentity,

                configManager, communicator, placeholders, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle player death event." }
            }
        }
    }

    fun onMinecraftAdvancement(payload: MCAdvancementMadeData) {
        if (!botManager.isReady)
            return LOGGER.warn { "Attempted to handle mc player advancement before discord is ready, message will not be sent." }

        val template = configManager.messages.mcPlayerAdvances ?: return
        if (!template.isEnabled) return

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
            ?: return LOGGER.warn { "Failed to find channel with name '${template.targetChannel}', are you sure it's configured?" }

        botManager.botScope.launch {
            val success = channel.createMessage(botManager.kord, template, payload.playerIdentity,

                configManager, communicator, placeholders, constructMessage(template, placeholders))
            if (!success) {
                LOGGER.warn { "Failed to handle player death event." }
            }
        }
    }

}