package dev.forb.dforblock.papermc

import dev.forb.dforblock.core.*
import dev.forb.dforblock.core.config.ConfigManager
import dev.forb.dforblock.core.discord.LogtoDiscordHandler
import io.papermc.paper.advancement.AdvancementDisplay
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.Instant

class DForBlockPaper : JavaPlugin(), Listener {

    var consoleAppender: AbstractAppender? = null

    var dForBlock: DForBlock? = null
        private set
    var communicator: IBlockyCommunicator? = null
        private set

    var configManager: ConfigManager? = null
        private set

    lateinit var startup: Instant
        private set

    var isLuckperms: Boolean = false
        private set

    val configDir: Path
        get() = dataFolder.toPath()

    override fun onEnable() {
        startup = Clock.System.now()
        isLuckperms = server.pluginManager.isPluginEnabled("LuckPerms")
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
        configManager = ConfigManager(configDir, JSON)
        communicator = MinecraftCommunicator(
            serverLike = PaperServerLike(this, server, startup),
            configManager = configManager ?: return LOGGER.error { "Unexpected state, 'configManager is null' while creating communicator..." },
            playerAudience = { server },
            isLuckperms = isLuckperms,
            configDir = configDir
        )

        dForBlock = DForBlock(
            configManager ?: return LOGGER.error { "Unexpected state, 'configManager is null' while creating dForBlock..." },
            communicator ?: return LOGGER.error { "Unexpected state, 'communicator is null' while creating dForBlock..." }
        )

        dForBlock?.start() ?: return LOGGER.error { "Unexpected state, 'dForBlock is null' while starting dForBlock..." }
        server.pluginManager.registerEvents(this, this)

        if (configManager?.messages?.serverLogs != null) {
            consoleAppender = object : AbstractAppender("DForBlockAppender", null, null, false, Property.EMPTY_ARRAY) {
                override fun append(event: LogEvent) {
                    if (event.level <= Level.INFO) {
                        LogtoDiscordHandler.enqueue(
                            event.level.name(),
                            event.message.formattedMessage
                        )
                    }
                }
            }

            consoleAppender?.apply {
                start()
                (LogManager.getRootLogger() as Logger).addAppender(this)
            }
        }
    }

    override fun onDisable() {
        runBlocking {
            dForBlock?.disable() ?: LOGGER.info { "Plugin disabled but dForBlock was already null..." }
        }

        consoleAppender?.apply {
            (LogManager.getRootLogger() as Logger).removeAppender(this)
            stop()
        }

        consoleAppender = null
        dForBlock = null
        communicator = null
        configManager = null
    }

    @EventHandler
    fun onChatMessage(event: AsyncChatEvent) {
        val content = PlainTextComponentSerializer.plainText().serialize(event.message())
        val payload = GameMessageData(
            messageContent = content,
            channelName = "default",
            playerIdentity = PlayerData.Minecraft(
                event.player.uniqueId,
                event.player.name,
                displayName = PlainTextComponentSerializer.plainText().serialize(event.player.displayName())
            )
        )
        dForBlock?.launch { onBlockyMessageReceive(payload) }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val payload = PlayerJoinLeaveData(
            PlayerData.Minecraft(
                player.uniqueId, // Bukkit uses Universally Unique Identifier (UUID) directly
                player.name,
                PlainTextComponentSerializer.plainText().serialize(player.displayName())
            )
        )
        dForBlock?.launch { onPlayerJoin(payload) }
    }

    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        val player = event.player
        val payload = PlayerJoinLeaveData(
            playerIdentity = PlayerData.Minecraft(
                player.uniqueId,
                player.name,
                PlainTextComponentSerializer.plainText().serialize(player.displayName())
            )
        )
        dForBlock?.launch { onPlayerLeave(payload) }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        val deathMessage = event.deathMessage()?.let {
            PlainTextComponentSerializer.plainText().serialize(it)
        } ?: "died"

        val payload = PlayerDeathData(
            playerIdentity = PlayerData.Minecraft(
                player.uniqueId,
                player.name,
                PlainTextComponentSerializer.plainText().serialize(player.displayName())
            ),
            deathMessage = deathMessage,
        )
        dForBlock?.launch { onPlayerDeath(payload) }
    }

    @EventHandler
    fun onAdvancementEarned(event: PlayerAdvancementDoneEvent) {
        val display = event.advancement.display ?: return
        if (!display.doesAnnounceToChat()) return

        val actionType = when (display.frame()) {
            AdvancementDisplay.Frame.TASK -> "made"
            AdvancementDisplay.Frame.GOAL -> "reached"
            AdvancementDisplay.Frame.CHALLENGE -> "completed"
        }

        val payload = MCAdvancementMadeData(
            advancementName = PlainTextComponentSerializer.plainText().serialize(display.title()),
            advancementDescription = PlainTextComponentSerializer.plainText().serialize(display.description()),
            advancementType = display.frame().name.lowercase(),
            actionType = actionType,
            playerIdentity = PlayerData.Minecraft(
                event.player.uniqueId,
                event.player.name,
                PlainTextComponentSerializer.plainText().serialize(event.player.displayName())
            )
        )

        dForBlock?.launch { onMinecraftAdvancement(payload) }
    }
}