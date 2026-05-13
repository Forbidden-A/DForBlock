package dev.forb.dforblock.core.config

import dev.kord.common.entity.PresenceStatus
import kotlinx.serialization.Serializable

@Serializable
data class Core(
    val discordToken: String,
    val guildIds: Set<ULong>,
    val discordStatus: PresenceStatus = PresenceStatus.Online,
    val showThinkingBubble: Boolean = false,
    val thinkingBubbleText: String? = null,
    val showActivity: Boolean = false,
    val activityType: RichPresenceType? = null,
    val activityText: String? = null,
    val streamUrl: String? = null,
    val serverPersonaName: String? = null,
    val serverPersonaAvatarUrl: String? = null,
    val serverPanelUrl: String? = null,
    val minecraftAvatarProviderUrl: String? = null,
    val hytaleAvatarProviderUrl: String? = null,
) {

    enum class RichPresenceType {
        Playing, Listening, Watching, Competing, Streaming
    }

    init {
        require(!(showActivity && showThinkingBubble)) { "Configuration error: Cannot show thinking bubble while showing an activity." }
        require(!(activityType == RichPresenceType.Streaming && streamUrl == null)) { "Configuration error: Cannot be streaming without a *valid* steamUrl." }
        if (showActivity)
            require(activityText != null) { "Configuration error: Cannot show empty activity." }
        if (showThinkingBubble)
            require(thinkingBubbleText != null) { "Configuration error: Cannot show empty thinking bubble." }
        require(guildIds.isNotEmpty()) { "Configuration error: Must include at least one guild Id." }
        require(discordToken.isNotEmpty()) { "Configuration error: Must set the discord token." }
    }
}
