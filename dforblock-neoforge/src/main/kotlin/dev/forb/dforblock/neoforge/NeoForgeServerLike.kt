package dev.forb.dforblock.neoforge

import dev.forb.dforblock.core.GameStatistics
import dev.forb.dforblock.core.MinecraftServerLike
import dev.forb.dforblock.core.PlayerData
import net.minecraft.commands.CommandSource
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.permissions.PermissionSet
import kotlin.time.Instant

class NeoForgeServerLike(val minecraftServer: MinecraftServer, val startup: Instant) : MinecraftServerLike {
    override val isStopped: Boolean
        get() = minecraftServer.isStopped

    override val players: Set<PlayerData>
        get() = minecraftServer.playerList.players.map { minecraftPlayer ->
            PlayerData.Minecraft(
                uuid = minecraftPlayer.uuid,
                name = minecraftPlayer.name.string,
                displayName = minecraftPlayer.displayName.string,
            )
        }.toSet()

    override val statistics: GameStatistics
        get() {

            val tickManager = minecraftServer.tickRateManager()

            val mspt = minecraftServer.averageTickTimeNanos / 1_000_000.0
            val targetTps = tickManager.tickrate().toDouble()

            val tps = if (mspt > 0.0) {
                targetTps.coerceAtMost(1000.0 / mspt)
            } else {
                targetTps
            }

            return GameStatistics(
                gameType = GameStatistics.GameType.Minecraft,
                onlinePlayers = minecraftServer.playerCount,
                playerLimit = minecraftServer.maxPlayers,
                startup = startup,
                gameVersion = minecraftServer.serverVersion,
                targetTps = targetTps,
                tps = tps,
                mspt = mspt
            )
        }

    override fun executeIfPossible(command: Runnable) = minecraftServer.executeIfPossible(command)

    override fun halt(wait: Boolean) = minecraftServer.halt(wait)

    override fun executeCommand(command: String, builder: StringBuilder) {
        executeIfPossible {
            val commandSource = object : CommandSource {
                override fun sendSystemMessage(message: Component) {
                    builder.append(message.string).append("\n")
                    minecraftServer.sendSystemMessage(message)
                }

                override fun acceptsSuccess(): Boolean = true
                override fun acceptsFailure(): Boolean = true
                override fun shouldInformAdmins(): Boolean = true
            }

            val customSource = minecraftServer.createCommandSourceStack()
                .withSource(commandSource)
                .withPermission(PermissionSet.ALL_PERMISSIONS)

            minecraftServer.commands.performPrefixedCommand(customSource, command)
        }
    }
}