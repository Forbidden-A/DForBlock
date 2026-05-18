package dev.forb.dforblock.fabric

import dev.forb.dforblock.core.*
import dev.forb.dforblock.core.config.ConfigManager
import dev.forb.dforblock.core.discord.LogtoDiscordHandler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.loader.api.FabricLoader
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences
import net.minecraft.server.level.ServerPlayer
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.time.Clock
import kotlin.time.Instant


val isLuckperms: Boolean = FabricLoader.getInstance().isModLoaded("luckperms")
val configDir: Path = FabricLoader.getInstance().configDir / "dforblock"

class DForBlockFabric : ModInitializer {

    companion object {
        lateinit var INSTANCE: DForBlockFabric
            private set
        val isInitialized: Boolean
            get() = ::INSTANCE.isInitialized
    }

    init {
        INSTANCE = this
    }

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

    override fun onInitialize() {
        INSTANCE = this

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            minecraftServerAudiences = MinecraftServerAudiences.of(server)
            startup = Clock.System.now()
            configManager = ConfigManager(configDir, JSON)
            communicator = ModdedMinecraftCommunicator(
                serverLike = FabricModdedServerLike(server, startup),
                configManager = configManager ?: return@register LOGGER.error { "Unexpected state, 'configManager is null' while creating communicator.." },
                isLuckperms = isLuckperms,
                configDir = configDir,
                playerAudience = { minecraftServerAudiences?.players() }
            )
            dForBlock = DForBlock(configManager ?: return@register LOGGER.error { "Unexpected state, 'configManager is null' while creating dForBlock..." }, communicator ?: return@register LOGGER.error { "Unexpected state, 'communicator is null' while creating dForBlock.." })
            dForBlock?.start() ?: return@register LOGGER.error { "Unexpected state, 'dFroBlock is null' while starting dforblock..'" }
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

        ServerLifecycleEvents.SERVER_STOPPED.register { _ ->
            dForBlock?.disable() ?: LOGGER.info { "Server stopped but dForBlock was already null..." }
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

        ServerMessageEvents.CHAT_MESSAGE.register { message, player, bound ->
            val content = message.unsignedContent()?.string ?: message.signedContent()
            val payload = GameMessageData(
                messageContent = content,
                channelName = "default",
                playerIdentity = PlayerData.Minecraft(
                    player.uuid,
                    player.name.string,
                    displayName = player.displayName.string
                )
            )
            dForBlock?.launch { onBlockyMessageReceive(payload) }
        }

        ServerPlayerEvents.JOIN.register { player ->
            val payload = PlayerJoinLeaveData(
                PlayerData.Minecraft(
                    player.uuid,
                    player.name.string,
                    player.displayName.string
                )
            )
            dForBlock?.launch { onPlayerJoin(payload) }
        }

        ServerPlayerEvents.LEAVE.register { player ->
            val payload = PlayerJoinLeaveData(
                playerIdentity = PlayerData.Minecraft(
                    player.uuid,
                    player.name.string,
                    player.displayName.string
                )
            )
            dForBlock?.launch { onPlayerLeave(payload) }
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            if (entity !is ServerPlayer)
                return@register

            val payload = PlayerDeathData(
                playerIdentity = PlayerData.Minecraft(
                    entity.uuid,
                    entity.name.string,
                    entity.displayName.string
                ),
                deathMessage = source.getLocalizedDeathMessage(entity).string,
            )
            dForBlock?.launch { onPlayerDeath(payload) }
        }

    }

}
