package dev.forb.dforblock

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.event.EventRegistry
import com.hypixel.hytale.logger.HytaleLogger
import dev.forb.dforblock.core.DForBlock
import dev.forb.dforblock.core.IBlockyCommunicator
import dev.forb.dforblock.core.JSON
import dev.forb.dforblock.core.LOGGER
import dev.forb.dforblock.core.config.ConfigManager
import dev.forb.dforblock.core.discord.LogtoDiscordHandler
import dev.forb.dforblock.listeners.PlayerListener
import java.util.logging.Level
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * DForBlock - A Hytale server plugin.
 */
class DForBlockPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    companion object {
        private val hytaleLogger = HytaleLogger.forEnclosingClass()

        @JvmStatic
        var instance: DForBlockPlugin? = null
            private set
    }

    init {
        instance = this
    }

    var dForBlock: DForBlock? = null
        private set
    var communicator: IBlockyCommunicator? = null
        private set

    var configManager: ConfigManager? = null
        private set

    lateinit var startup: Instant
        private set

    override fun setup() {
        hytaleLogger.at(Level.INFO).log("[DForBlock] Welcome")

        startup = Clock.System.now()
        configManager = ConfigManager(configDir, JSON)
        communicator = HytaleCommunicator(
            plugin = this,
            configManager = configManager ?: return LOGGER.error { "Unexpected state, 'configManager is null' while creating communicator.." },
            isLuckperms = isLuckperms,
            configDir = configDir,
            playerAudience = { minecraftServerAudiences?.players() }
        )
        dForBlock = DForBlock(configManager ?: return LOGGER.error { "Unexpected state, 'configManager is null' while creating dForBlock..." }, communicator ?: return@register dev.forb.dforblock.core.LOGGER.error { "Unexpected state, 'communicator is null' while creating dForBlock.." })
        dForBlock?.start() ?: return@register LOGGER.error { "Unexpected state, 'dFroBlock is null' while starting dforblock..'" }
        if (configManager?.messages?.serverLogs != null) {
            consoleAppender = object :
                AbstractAppender("DForBlockAppender", null, null, false, Property.EMPTY_ARRAY) {
                override fun append(event: LogEvent) {
                    if (event.level <= org.apache.logging.log4j.Level.INFO) LogtoDiscordHandler.enqueue(
                        event.level.name(),
                        event.message.formattedMessage
                    )
                }
            }

            consoleAppender?.apply {
                start()
                (LogManager.getRootLogger() as Logger).addAppender(this)
            }
        }

        // Register event listeners
        registerListeners()
    }

    private fun registerListeners() {
        val eventBus: EventRegistry = eventRegistry

        try {
            PlayerListener().register(eventBus)
            hytaleLogger.at(Level.INFO).log("[DForBlock] Registered player event listeners")
        } catch (e: Exception) {
            hytaleLogger.at(Level.WARNING).withCause(e).log("[DForBlock] Failed to register listeners")
        }
    }

    override fun start() {
        hytaleLogger.at(Level.INFO).log("[DForBlock] Started!")
        hytaleLogger.at(Level.INFO).log("[DForBlock] Use /dfo help for commands")
    }

    override fun shutdown() {
        hytaleLogger.at(Level.INFO).log("[DForBlock] Shutting down...")
        instance = null
    }
}