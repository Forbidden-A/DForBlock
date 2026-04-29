package dev.forb.dforblock

import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class DForBlockPaper : JavaPlugin() {

    override fun onEnable() {
        DForBlock.enable(PaperCommunicator(this))

        val listener = object : Listener {

            @EventHandler
            fun onAsync(event: AsyncChatEvent) {
                val payload = BlockyMessagePayload(
                    author = event.player.displayName().toString(),
                    messageContent = event.message().toString(),
                    skinHint = SkinHint.Minecraft(event.player.uniqueId, event.player.name)
                )
                DForBlock.handleBlockyMessage(payload)
            }

        }

        server.pluginManager.registerEvents(listener, this)

    }

    override fun onDisable() {
        DForBlock.disable()
    }
}
