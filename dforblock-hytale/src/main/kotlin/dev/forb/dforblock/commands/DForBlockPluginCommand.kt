package dev.forb.dforblock.commands

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase

/**
 * Main command for DForBlock plugin.
 *
 * Usage:
 * - /dfo help - Show available commands
 * - /dfo info - Show plugin information
 * - /dfo reload - Reload plugin configuration
 */
class DForBlockPluginCommand : AbstractCommandCollection("dfo", "DForBlock plugin commands") {

    init {
        addSubCommand(HelpSubCommand())
        addSubCommand(InfoSubCommand())
        addSubCommand(ReloadSubCommand())
    }

    override fun canGeneratePermission(): Boolean = false
}

/**
 * /dfo help - Show available commands
 */
class HelpSubCommand : CommandBase("help", "Show available commands") {

    init {
        setPermissionGroup(null)
    }

    override fun canGeneratePermission(): Boolean = false

    override fun executeSync(context: CommandContext) {
        context.sendMessage(Message.raw(""))
        context.sendMessage(Message.raw("=== DForBlock Commands ==="))
        context.sendMessage(Message.raw("/dfo help - Show this help message"))
        context.sendMessage(Message.raw("/dfo info - Show plugin information"))
        context.sendMessage(Message.raw("/dfo reload - Reload configuration"))
        context.sendMessage(Message.raw("========================"))
    }
}

/**
 * /dfo info - Show plugin information
 */
class InfoSubCommand : CommandBase("info", "Show plugin information") {

    init {
        setPermissionGroup(null)
    }

    override fun canGeneratePermission(): Boolean = false

    override fun executeSync(context: CommandContext) {
        val plugin = dev.forb.dforblock.DForBlockPlugin.instance

        context.sendMessage(Message.raw(""))
        context.sendMessage(Message.raw("=== DForBlock Info ==="))
        context.sendMessage(Message.raw("Name: DForBlock"))
        context.sendMessage(Message.raw("Version: by rootProject"))
        context.sendMessage(Message.raw("Author: ForbiddenBA"))
        context.sendMessage(Message.raw("Status: " + if (plugin != null) "Running" else "Not loaded"))
        context.sendMessage(Message.raw("===================="))
    }
}

/**
 * /dfo reload - Reload plugin configuration
 */
class ReloadSubCommand : CommandBase("reload", "Reload plugin configuration") {

    init {
        setPermissionGroup(null)
    }

    override fun canGeneratePermission(): Boolean = false

    override fun executeSync(context: CommandContext) {
        val plugin = dev.forb.dforblock.DForBlockPlugin.instance

        if (plugin == null) {
            context.sendMessage(Message.raw("Error: Plugin not loaded"))
            return
        }

        context.sendMessage(Message.raw("Reloading DForBlock..."))

        // TODO: Add your reload logic here

        context.sendMessage(Message.raw("DForBlock reloaded successfully!"))
    }
}