package dev.forb.dforblock

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.loader.api.FabricLoader
import net.kyori.adventure.chat.ChatType
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences
import net.minecraft.server.MinecraftServer
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.io.path.div

class DForBlockFabric : ModInitializer {

    val logger: Logger = Logger.getLogger("DForBlock.fabric")

    val configDir: Path = FabricLoader.getInstance().configDir
    val configPath = configDir / "dforblock.toml"

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
            val payload = BlockyMessagePayload(
                author = player.name.string,
                messageContent = content,
                channelName = "global",
                skinHint = SkinHint.Minecraft(player.uuid, player.name.string)
            )
            DForBlock.handleBlockyMessage(payload)
        }
    }

}
