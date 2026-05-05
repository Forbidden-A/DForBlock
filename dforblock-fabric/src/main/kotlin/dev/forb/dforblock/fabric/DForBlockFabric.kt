package dev.forb.dforblock.fabric

import dev.forb.dforblock.core.*
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


val isLuckperms: Boolean = FabricLoader.getInstance().isModLoaded("luckperms")
val configDir: Path = FabricLoader.getInstance().configDir
val configPath = configDir / "dforblock.json5"

class DForBlockFabric : ModInitializer {


    var minecraftServer: MinecraftServer? = null
        private set

    var adventure: MinecraftServerAudiences? = null
        private set

    override fun onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            minecraftServer = server
            adventure = MinecraftServerAudiences.of(server)
            DForBlock.enable(FabricBlockyCommunicator(this))
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            DForBlock.disable()
            minecraftServer = null
            adventure = null
        }

        ServerMessageEvents.CHAT_MESSAGE.register { message, player, bound ->
            val content = message.unsignedContent()?.string ?: message.signedContent()
            var payload = BlockyMessagePayload(
                player = player.displayName.string,
                messageContent = content,
                channelName = "default",
                skinHint = SkinHint.Minecraft(player.uuid, player.name.string)
            )
            if (isLuckperms) {
                payload = payload.copy(
                    prefix = luckpermsPrefixByUUID(player.uuid),
                    suffix = luckpermsSuffixByUUID(player.uuid)
                )
            }
            DForBlock.handleBlockyMessage(payload)
        }

        ServerPlayerEvents.JOIN.register { player ->
            var payload = PlayerJoinLeavePayload(player.displayName.string)
            if (isLuckperms) {
                payload = payload.copy(
                    prefix = luckpermsPrefixByUUID(player.uuid),
                    suffix = luckpermsSuffixByUUID(player.uuid)
                )
            }
            DForBlock.handlePlayerJoined(payload)
        }

        ServerPlayerEvents.LEAVE.register { player ->
            var payload = PlayerJoinLeavePayload(
                player.displayName.string
            )
            if (isLuckperms) {
                payload = payload.copy(
                    prefix = luckpermsPrefixByUUID(player.uuid),
                    suffix = luckpermsSuffixByUUID(player.uuid)
                )
            }
            DForBlock.handlePlayerLeave(payload)
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            if (entity !is ServerPlayer)
                return@register

            var payload = PlayerDeathPayload(
                playerName = entity.displayName.string,
                deathMessage = source.getLocalizedDeathMessage(entity).string,
            )
            if (isLuckperms) {
                payload = payload.copy(
                    prefix = luckpermsPrefixByUUID(entity.uuid),
                    suffix = luckpermsSuffixByUUID(entity.uuid)
                )
            }
            DForBlock.handlePlayerDeath(payload)
        }

    }

}
