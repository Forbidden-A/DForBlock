package dev.forb.dforblock.core.config

import kotlinx.serialization.Serializable

@Serializable
data class ChannelConfig(
    val channelId: ULong,
    val allowsWebhooks: Boolean = false,
    val webhookToken: String? = null,
    val webhookId: ULong? = null,
    val topicTemplate: String? = null
) {
    init {
        if (allowsWebhooks)
            require(webhookId != null && webhookToken != null) {
                "Configuration error: 'webhookId' and 'webhookToken' must be provided if 'allowWebhooks' is true for channel ID: $channelId."
            }
    }
}