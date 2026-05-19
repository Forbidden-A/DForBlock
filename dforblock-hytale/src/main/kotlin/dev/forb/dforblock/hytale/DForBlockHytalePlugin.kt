package dev.forb.dforblock.hytale

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.logger.backend.HytaleLoggerBackend
import com.hypixel.hytale.server.core.HytaleServer
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import dev.forb.dforblock.core.*
import dev.forb.dforblock.core.config.ConfigManager
import dev.forb.dforblock.core.discord.LogtoDiscordHandler
import eu.mikart.adventure.platform.hytale.HytaleAudiences
import eu.mikart.adventure.platform.hytale.HytaleComponentSerializer
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.SimpleFormatter
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * DForBlock - A Hytale server plugin.
 */
class DForBlockHytalePlugin(init: JavaPluginInit) : JavaPlugin(init) {

    companion object {
        private val hytaleLogger = HytaleLogger.forEnclosingClass()

        @JvmStatic
        var instance: DForBlockHytalePlugin? = null
            private set
    }

    var dForBlockOrchestrator: DForBlockOrchestrator? = null
        private set
    var communicator: IBlockyCommunicator? = null
        private set
    var configManager: ConfigManager? = null
        private set

    lateinit var startup: Instant
        private set

    var isLuckperms: Boolean = false
        private set

    private var discordLogSubscriber: CopyOnWriteArrayList<LogRecord>? = null

    val configDir: Path
        get() = Path.of("mods", "dforblock")

    init {
        instance = this
    }

    override fun setup() {
        startup = Clock.System.now()
        val dir = configDir.toFile()
        if (!dir.exists()) {
            dir.mkdirs()
        }

        configManager = ConfigManager(configDir, JSON)
        communicator = HytaleCommunicator(
            configDir = configDir,
            isLuckperms = { isLuckperms },
            configManager = configManager!!,
        )

        dForBlockOrchestrator = DForBlockOrchestrator(
            configManager ?: run {
                hytaleLogger.at(Level.SEVERE)
                    .log("Unexpected state, 'configManager is null' while creating dforBlock...")
                return
            },
            communicator ?: run {
                hytaleLogger.at(Level.SEVERE)
                    .log("Unexpected state, 'communicator is null' while creating dforBlock...")
                return
            }
        )

        hytaleLogger.at(Level.INFO).log("[DForBlock] Welcome! Setup complete.")
    }

    override fun start() {
        isLuckperms = HytaleServer.get().pluginManager.plugins.any { it.identifier.name == "LuckPerms" }
        dForBlockOrchestrator?.start() ?: run {
            hytaleLogger.at(Level.SEVERE)
                .log("Unexpected state, 'dForBlockOrchestrator is null' while starting dforBlock...")
            return
        }

        registerListeners()

        if (configManager?.messages?.serverLogs != null) {
            discordLogSubscriber = object : CopyOnWriteArrayList<LogRecord>() {
                override fun add(element: LogRecord): Boolean {
                    try {
                        if (element.level.intValue() >= Level.INFO.intValue()) {
                            val msg = element.message ?: ""
                            val rawLoggerName = element.loggerName ?: ""
                            val cleanModuleName = rawLoggerName.substringAfterLast("][").takeIf { it != "Hytale" }
                            val prefix = if (cleanModuleName != null) "[$cleanModuleName] " else ""
                            val finalMessage = "$prefix$msg"
                            LogtoDiscordHandler.enqueue(
                                element.level.name,
                                finalMessage
                            )
                        }
                    } catch (e: Exception) {
                        System.err.println("[DForBlock] Discord logger caught an error: ${e.message}")
                    }
                    return true
                }
            }
            HytaleLoggerBackend.subscribe(discordLogSubscriber)
        }

        hytaleLogger.at(Level.INFO).log("[DForBlock] Started!")
    }

    private fun registerListeners() {
        val event = eventRegistry
        val entityStore = entityStoreRegistry

        try {
            event.register(PlayerChatEvent::class.java) { event ->
                val payload = GameMessageData(
                    messageContent = event.content,
                    channelName = "default",
                    playerIdentity = PlayerData.Hytale(
                        event.sender.uuid,
                        event.sender.username,
                        displayName = null
                    )
                )
                dForBlockOrchestrator?.launch { onBlockyMessageReceive(payload) }
            }

            event.register(PlayerConnectEvent::class.java) { event ->
                val payload = PlayerJoinLeaveData(
                    PlayerData.Hytale(
                        event.playerRef.uuid,
                        event.playerRef.username,
                        null
                    )
                )
                dForBlockOrchestrator?.launch { onPlayerJoin(payload) }
            }

            event.register(PlayerDisconnectEvent::class.java) { event ->
                val payload = PlayerJoinLeaveData(
                    playerIdentity = PlayerData.Hytale(
                        event.playerRef.uuid,
                        event.playerRef.username,
                        null
                    )
                )
                dForBlockOrchestrator?.launch { onPlayerLeave(payload) }
            }

            val playerDeathHandler: DeathSystems.OnDeathSystem = object : DeathSystems.OnDeathSystem() {
                override fun onComponentAdded(
                    ref: Ref<EntityStore?>,
                    component: DeathComponent,
                    store: Store<EntityStore?>,
                    buffer: CommandBuffer<EntityStore?>
                ) {
                    val playerRef = store.getComponent(ref, PlayerRef.getComponentType())
                        ?: return LOGGER.debug { "Could not get playerRef, entity was likely not a player." }
                    val player =
                        store.getComponent(ref, Player.getComponentType()) ?: return LOGGER.debug { "Could not get player, entity was likely not a player." }
                    val configManager = configManager ?: return LOGGER.error { "Config Manager is null while handling player death." }
                    val communicator = communicator ?: return LOGGER.error { "Communicator is null while handling player death.." }
                    val uuid = playerRef.uuid
                    val name = playerRef.username
                    val identity = PlayerData.Hytale(
                        uuid, name, player.displayName
                    )
                    val deathMessage = component.deathMessage?.let {
                        var ansiText = it.ansiMessage
                        val qualifiedName = identity.qualifiedName(configManager, communicator)
                        if (ansiText.startsWith("You were", ignoreCase = true)) {
                            ansiText = ansiText.replaceFirst("You were", "$qualifiedName was", ignoreCase = true)
                        } else if (ansiText.startsWith("You", ignoreCase = true)) {
                            ansiText = ansiText.replaceFirst("You", qualifiedName, ignoreCase = true)
                        }
                        ansiText
                    } ?: "${identity.qualifiedName(configManager, communicator)} died"

                    val deathData = PlayerDeathData(identity, deathMessage)
                    dForBlockOrchestrator?.launch { onPlayerDeath(deathData) }
                }

                override fun getQuery(): Query<EntityStore> =
                    Query.and(Player.getComponentType(), DeathComponent.getComponentType())
            }

            entityStore.registerSystem(playerDeathHandler)

            hytaleLogger.at(Level.INFO).log("[DForBlock] Registered event listeners")
        } catch (e: Exception) {
            hytaleLogger.at(Level.WARNING).withCause(e).log("[DForBlock] Failed to register listeners")
        }
    }

    override fun shutdown() {
        runBlocking {
            dForBlockOrchestrator?.disable() ?: hytaleLogger.at(Level.INFO)
                .log("Plugin disabled but dforBlock was already null...")
        }
        discordLogSubscriber?.let { subscriber ->
            HytaleLoggerBackend.unsubscribe(subscriber)
        }
        discordLogSubscriber = null

        dForBlockOrchestrator = null
        communicator = null
        configManager = null
        isLuckperms = false
        hytaleLogger.at(Level.INFO).log("[DForBlock] Shutting down...")
        instance = null
    }
}