package dev.forb.dforblock

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.event.gateway.DisconnectEvent
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

/*
* This is the entry point of this project
*  */
object DForBlock {

    val logger: Logger = Logger.getLogger("DForBlock")
    var isEnabled: Boolean = false
        private set

    private lateinit var communicator: IBlockyCommunicator

    lateinit var config: DForBlockConfig
        private set

    lateinit var kord: Kord
        private set

    private val botScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun enable(icommunicator: IBlockyCommunicator) {
        logger.info { "DForBlock starting..." }
        communicator = icommunicator
        config = loadConfig(communicator.getConfigFile())
        botScope.launch { start() }
        logger.info { "DForBlock enabled." }
    }

    private suspend fun initialise() {
        kord = Kord(config.discordToken)
        kord.on<ReadyEvent> {
            logger.info { "DForBlock ready" }
            isEnabled = true
        }

        kord.on<MessageCreateEvent> {

            val payload = DiscordMessagePayload(
                author = (message.author?: return@on).username,
                content = message.content,
                channel = message.channelId.value
            )
            communicator.broadcastMessage(payload)
            TODO("Check channel and route message correctly, etc..")
        }

        kord.on<DisconnectEvent> {
            logger.info { "Gateway disconnected" }
        }
    }

    private suspend fun start() {
        logger.info { "Initialising DForBlock..." }
        initialise()
        logger.info { "Logging in..." }
        kord.login()
    }

    private suspend fun stop() {
        logger.info { "Logging out..." }
        kord.shutdown()
    }

    fun disable() {
        logger.info { "Termination requested..." }
        botScope.launch { stop() }
        isEnabled = false
        logger.info { "DForBlock disabled." }
    }

    fun handleBlockyMessage(payload: BlockyMessagePayload) {
        if (!isEnabled) {
            logger.log(Level.SEVERE) { "Attempted to handle blocky message before enabling..." }
            return
        }

        val channelId = Snowflake(
            config.channels[payload.channel] ?:
                return logger.log(Level.SEVERE) { "Failed to find channel ${payload.channel}, are you sure you configured it?" }
        )

        botScope.launch {
            kord.rest.channel.createMessage(
                channelId = channelId
            ) {
                content = "${payload.author}: ${payload.content}"
            }
            TODO("Format message correctly, create webhook if enabled, etc...")
        }
    }
}