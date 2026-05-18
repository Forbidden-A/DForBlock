package dev.forb.dforblock.listeners

import com.hypixel.hytale.event.EventRegistry
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import java.util.logging.Level

/**
 * Listener for player connection events.
 */
class PlayerListener {

    companion object {
        private val LOGGER = HytaleLogger.forEnclosingClass()
    }

    /**
     * Register all player event listeners.
     */
    fun register(eventBus: EventRegistry) {
        // PlayerConnectEvent
        try {
            eventBus.register(PlayerConnectEvent::class.java, ::onPlayerConnect)
            LOGGER.at(Level.INFO).log("[DForBlock] Registered PlayerConnectEvent listener")
        } catch (e: Exception) {
            LOGGER.at(Level.WARNING).withCause(e).log("[DForBlock] Failed to register PlayerConnectEvent")
        }

        // PlayerDisconnectEvent
        try {
            eventBus.register(PlayerDisconnectEvent::class.java, ::onPlayerDisconnect)
            LOGGER.at(Level.INFO).log("[DForBlock] Registered PlayerDisconnectEvent listener")
        } catch (e: Exception) {
            LOGGER.at(Level.WARNING).withCause(e).log("[DForBlock] Failed to register PlayerDisconnectEvent")
        }
    }

    private fun onPlayerConnect(event: PlayerConnectEvent) {
        val playerName = event.playerRef?.username ?: "Unknown"
        val worldName = event.world?.name ?: "unknown"

        LOGGER.at(Level.INFO).log("[DForBlock] Player %s connected to world %s", playerName, worldName)

        // TODO: Add your player join logic here
    }

    private fun onPlayerDisconnect(event: PlayerDisconnectEvent) {
        val playerName = event.playerRef?.username ?: "Unknown"

        LOGGER.at(Level.INFO).log("[DForBlock] Player %s disconnected", playerName)

        // TODO: Add your player leave logic here
    }
}