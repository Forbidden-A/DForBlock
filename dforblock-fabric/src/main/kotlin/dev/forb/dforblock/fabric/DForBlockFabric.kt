package dev.forb.dforblock.fabric

import dev.forb.dforblock.core.*
import dev.forb.dforblock.core.config.ConfigManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.loader.api.FabricLoader
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
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

    var minecraftServer: MinecraftServer? = null
        private set

    var adventure: MinecraftServerAudiences? = null
        private set

    lateinit var dForBlock: DForBlock
        private set
    lateinit var communicator: IBlockyCommunicator
        private set

    lateinit var configManager: ConfigManager
        private set

    lateinit var startup: Instant
        private set

    override fun onInitialize() {
        INSTANCE = this
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            minecraftServer = server
            adventure = MinecraftServerAudiences.of(server)
            startup = Clock.System.now()
            communicator = FabricBlockyCommunicator(this, isLuckperms)
            configManager = ConfigManager(configDir, JSON)
            dForBlock = DForBlock(configManager, communicator)
            dForBlock.start()
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            dForBlock.disable()
            minecraftServer = null
            adventure = null
        }

        ServerMessageEvents.CHAT_MESSAGE.register { message, player, bound ->
            val content = message.unsignedContent()?.string ?: message.signedContent()
            val payload = GameMessageData(
                messageContent = content,
                channelName = "default",
                playerIdentity = PlayerIdentity.Minecraft(
                    player.uuid,
                    player.name.string,
                    displayName = player.displayName.string
                )
            )
            dForBlock.onBlockyMessageReceive(payload)
        }

        ServerPlayerEvents.JOIN.register { player ->
            val payload = PlayerJoinLeaveData(
                PlayerIdentity.Minecraft(
                    player.uuid,
                    player.name.string,
                    player.displayName.string
                )
            )
            dForBlock.onPlayerJoin(payload)
        }

        ServerPlayerEvents.LEAVE.register { player ->
            val payload = PlayerJoinLeaveData(
                playerIdentity = PlayerIdentity.Minecraft(
                    player.uuid,
                    player.name.string,
                    player.displayName.string
                )
            )
            dForBlock.onPlayerLeave(payload)
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            if (entity !is ServerPlayer)
                return@register

            val payload = PlayerDeathData(
                playerIdentity = PlayerIdentity.Minecraft(
                    entity.uuid,
                    entity.name.string,
                    entity.displayName.string
                ),
                deathMessage = source.getLocalizedDeathMessage(entity).string,
            )
            dForBlock.onPlayerDeath(payload)
        }

    }

}
