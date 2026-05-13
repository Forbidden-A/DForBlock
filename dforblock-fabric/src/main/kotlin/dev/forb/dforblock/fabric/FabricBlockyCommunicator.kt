package dev.forb.dforblock.fabric

import dev.forb.dforblock.core.*
import dev.forb.dforblock.core.config.ConfigManager
import kotlinx.coroutines.suspendCancellableCoroutine
import net.minecraft.commands.CommandSource
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.PermissionSet
import kotlin.coroutines.resume
import kotlin.time.Clock

class FabricBlockyCommunicator(val mod: DForBlockFabric) : IBlockyCommunicator {

    override val configDir = dev.forb.dforblock.fabric.configDir

    override fun broadcastMessage(payload: DiscordMessageData) {
        val template = mod.configManager.messages.discordUserChats ?: return
        val channel = mod.configManager.channels.entries.firstOrNull { (k, v) -> v.channelId == payload.channelId } ?: return
        val kyoriComponent = prepareMinecraftMiniMessage(payload, channel.key, template)
        mod.adventure?.players()?.sendMessage(kyoriComponent)
            ?: return LOGGER.error { "Unexpected state, 'adventure is null', please report this.." }
    }

    override fun onlinePlayers(): Set<String> =
        mod.minecraftServer?.playerList?.players?.map { it.displayName.string }?.toSet()
            ?: emptySet<String>().apply { LOGGER.error { "Unexpected state, 'minecraftServer is null', please report this.." } }

    override fun serverStatistics(): GameStatistics {
        val minecraftServer = mod.minecraftServer
            ?: return GameStatistics(
                GameStatistics.GameType.Minecraft, 0, 0, Clock.System.now(),
                "unknown",
                .0,
                .0,
                .0,
            ).apply {
                LOGGER.error { "Unexpected state, 'minecraftServer is null', please report this.." }
            }

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
            startup = mod.startup,
            gameVersion = minecraftServer.serverVersion,
            targetTps = targetTps,
            tps = tps,
            mspt = mspt,
        )
    }

    override fun stopServer() {
        mod.minecraftServer?.halt(false) ?: return LOGGER.error { "Unexpected state, 'minecraftServer is null', please report this.." }
    }

    override suspend fun executeCommand(command: String): String {
        val minecraftServer = mod.minecraftServer ?: return "".apply { LOGGER.error { "Unexpected state, 'minecraftServer is null', please report this.." } }

        return suspendCancellableCoroutine { continuation ->
            minecraftServer.execute {
                val builder = StringBuilder()

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

                try {
                    minecraftServer.commands.performPrefixedCommand(customSource, command)
                    val result = builder.toString().trim()

                    if (result.isEmpty()) {
                        continuation.resume("Command executed successfully (no text output).")
                    } else {
                        continuation.resume(result)
                    }
                } catch (e: Exception) {
                    continuation.resume("Internal Error executing command: ${e.message} ${e.stackTraceToString()}")
                }
            }
        }
    }
}
