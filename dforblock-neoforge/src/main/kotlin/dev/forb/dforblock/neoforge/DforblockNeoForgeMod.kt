package dev.forb.dforblock.neoforge

import dev.forb.dforblock.core.DForBlock
import dev.forb.dforblock.core.GameMessageData
import dev.forb.dforblock.core.IBlockyCommunicator
import dev.forb.dforblock.core.JSON
import dev.forb.dforblock.core.LOGGER
import dev.forb.dforblock.core.MCAdvancementMadeData
import dev.forb.dforblock.core.MinecraftCommunicator
import dev.forb.dforblock.core.PlayerData
import dev.forb.dforblock.core.PlayerDeathData
import dev.forb.dforblock.core.PlayerJoinLeaveData
import dev.forb.dforblock.core.config.ConfigManager
import dev.forb.dforblock.core.discord.LogtoDiscordHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences
import net.minecraft.advancements.AdvancementType
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.AdvancementEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Main mod class.
 *
 */
@Mod(DforblockNeoForgeMod.ID)
object DforblockNeoForgeMod {
    const val ID = "dforblock"

    val configDir = FMLPaths.CONFIGDIR.get() / "dforblock"

    init {

        val obj = runForDist(
            serverTarget = {
                NeoForge.EVENT_BUS.register(DForBlockNeoForge(this.configDir))
            },
            clientTarget = {},
        )

        println(obj)
    }
}

class DForBlockNeoForge(val configDir: Path) {

    var minecraftServerAudiences: MinecraftServerAudiences? = null
        private set

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

    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        minecraftServerAudiences = MinecraftServerAudiences.of(event.server)
        startup = Clock.System.now()
        isLuckperms = ModList.get().isLoaded("luckperms")
        configManager = ConfigManager(configDir, JSON)
        communicator = MinecraftCommunicator(
            serverLike = NeoForgeServerLike(event.server, startup),
            configManager = configManager ?: return LOGGER.error { "Unexpected state, 'configManager is null' while creating communicator..." },
            playerAudience = { minecraftServerAudiences?.players() },
            isLuckperms = { isLuckperms },
            configDir = configDir
        )
        dForBlock = DForBlock(configManager?: return LOGGER.error { "Unexpected state, 'configManager is null' while creating dForBlock..." }, communicator?:return LOGGER.error { "Unexpected state, 'communicator is null' while creating dForBlock..." })
        dForBlock?.start() ?: return LOGGER.error { "Unexpected state, 'dForBlock is null' while starting dForBlock..." }
        if (configManager?.messages?.serverLogs != null) {
            consoleAppender = object :
                AbstractAppender("DForBlockAppender", null, null, false, Property.EMPTY_ARRAY) {
                override fun append(event: LogEvent) {
                    if (event.level <= Level.INFO) LogtoDiscordHandler.enqueue(
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
    }

    @SubscribeEvent
    fun onChatMessage(event: ServerChatEvent) {
        val content = event.rawText
        val payload = GameMessageData(
            messageContent = content,
            channelName = "default",
            playerIdentity = PlayerData.Minecraft(
                event.player.uuid,
                event.player.name.string,
                displayName = event.player.displayName.string
            )
        )
        dForBlock?.launch { onBlockyMessageReceive(payload) }
    }

    @SubscribeEvent
    fun onServerStopped(event: ServerStoppedEvent) {
        runBlocking {
            dForBlock?.disable() ?: LOGGER.info { "Server stopped but dForBlock was already null..." }
            delay(500.milliseconds) // ensure things got closed properly :/
        }
        consoleAppender?.apply {
            (LogManager.getRootLogger() as Logger).removeAppender(this)
            stop()
        }
        consoleAppender = null
        minecraftServerAudiences = null
        dForBlock = null
        communicator = null
        configManager = null
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return

        val payload = PlayerJoinLeaveData(
            PlayerData.Minecraft(
                player.uuid,
                player.name.string,
                player.displayName.string
            )
        )
        dForBlock?.launch { onPlayerJoin(payload) }
    }

    @SubscribeEvent
    fun onPlayerLeave(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return

        val payload = PlayerJoinLeaveData(
            playerIdentity = PlayerData.Minecraft(
                player.uuid,
                player.name.string,
                player.displayName.string
            )
        )
        dForBlock?.launch { onPlayerLeave(payload) }
    }

    @SubscribeEvent
    fun onPlayerDeath(event: LivingDeathEvent) {
        val entity = event.entity
        if (entity !is ServerPlayer) return

        val payload = PlayerDeathData(
            playerIdentity = PlayerData.Minecraft(
                entity.uuid,
                entity.name.string,
                entity.displayName.string
            ),
            deathMessage = event.source.getLocalizedDeathMessage(entity).string,
        )
        dForBlock?.launch { onPlayerDeath(payload) }
    }

    @SubscribeEvent
    fun onAdvancementEarned(event: AdvancementEvent.AdvancementEarnEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val displayOptional = event.advancement.value.display()
        if (displayOptional.isEmpty) return
        val displayInfo = displayOptional.get()
        if (!displayInfo.shouldAnnounceChat())
            return

        val actionType = when (displayInfo.type) {
            AdvancementType.TASK -> "made"
            AdvancementType.GOAL -> "achieved"
            AdvancementType.CHALLENGE -> "completed"
        }

        val payload = MCAdvancementMadeData(
            advancementName = displayInfo.title.string,
            advancementDescription = displayInfo.description.string,
            advancementType = displayInfo.type.name.lowercase(),
            actionType = actionType,
            playerIdentity = PlayerData.Minecraft(player.uuid, player.name.string, player.displayName.string)
        )

        dForBlock?.launch { onMinecraftAdvancement(payload) }
    }

}