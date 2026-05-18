package dev.forb.dforblock.papermc

import dev.forb.dforblock.core.GameStatistics
import dev.forb.dforblock.core.MinecraftServerLike
import dev.forb.dforblock.core.PlayerData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Server
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.plugin.Plugin
import kotlin.time.Instant

class PaperServerLike(
    private val plugin: Plugin,
    private val server: Server,
    private val startup: Instant
) : MinecraftServerLike {

    override val isStopped: Boolean
        get() = !plugin.isEnabled

    override val players: Set<PlayerData>
        get() = server.onlinePlayers.map { player ->
            PlayerData.Minecraft(
                uuid = player.uniqueId,
                name = player.name,
                displayName = PlainTextComponentSerializer.plainText().serialize(player.displayName()),
            )
        }.toSet()

    override val statistics: GameStatistics
        get() {
            return GameStatistics(
                gameType = GameStatistics.GameType.Minecraft,
                onlinePlayers = server.onlinePlayers.size,
                playerLimit = server.maxPlayers,
                startup = startup,
                gameVersion = server.minecraftVersion,
                targetTps = server.serverTickManager.tickRate.toDouble(),
                tps = server.tps[0],
                mspt = server.averageTickTime
            )
        }

    override fun executeIfPossible(command: Runnable) {
        if (server.isPrimaryThread) {
            command.run()
        } else {
            server.scheduler.runTask(plugin, command)
        }
    }

    override fun halt(wait: Boolean) {
        server.shutdown()
    }

    override fun executeCommand(command: String, builder: StringBuilder) {
        executeIfPossible {
            val capturingSender = object : ConsoleCommandSender by server.consoleSender {
                override fun sendMessage(message: String) {
                    builder.append(message).append("\n")
                    server.consoleSender.sendMessage(message)
                }
                override fun sendMessage(message: Component) {
                    builder.append(PlainTextComponentSerializer.plainText().serialize(message)).append("\n")
                    server.consoleSender.sendMessage(message)
                }
            }

            server.dispatchCommand(capturingSender, command)
        }
    }
}