package dev.forb.dforblock

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import kotlin.io.path.div

class Dforblock : ModInitializer {
    val configPath = FabricLoader.getInstance().configDir / "dforblock.toml"

    lateinit var minecraftServer: MinecraftServer
        private set

    override fun onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            minecraftServer = server
            DForBlock.enable(FabricBlockyCommunicator(this))
        }
    }
}
