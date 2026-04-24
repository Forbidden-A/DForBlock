package dev.forb.dforblock

import org.bukkit.plugin.java.JavaPlugin

class DForBlockPaper : JavaPlugin() {

    override fun onEnable() {
        DForBlock.enable(PaperCommunicator(this))
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
