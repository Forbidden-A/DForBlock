package dev.forb.dforblock

import dev.forb.dforblock.DForBlock.logger
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

fun DForBlockConfig.findChannelByName(name: String): ChannelConfig? =
    channels.firstOrNull { it.channelName.equals(name, ignoreCase = true) }

fun DForBlockConfig.findChannelById(id: ULong): ChannelConfig? = channels.firstOrNull { it.channelId == id }
fun prepareMinecraftMiniMessage(payload: DiscordMessagePayload, config: DForBlockConfig): Component {
    val processedString = config.toMinecraftChatFormat
        .replace("{author}", payload.author)
        .replace("{content}", payload.content)

    config.findChannelById(payload.channelId)?.apply {
        processedString.replace("{channel}", channelName)
    }

    val miniMessage = MiniMessage.miniMessage()
    return miniMessage.deserialize(processedString)
}

fun createMessage(config: DForBlockConfig, scope: CoroutineScope, kord: Kord, payload: BlockyMessagePayload) {
    val channel = config.findChannelByName(payload.channelName)
        ?: return logger.warning { "Failed to find channel with name '${payload.channelName}', are you sure it's configured?" }

    sendDiscordMessage(
        config.toDiscordChatFormat
            .replace("{author}", payload.author)
            .replace("{content}", payload.messageContent),
        Snowflake(channel.channelId),
        scope,
        kord,
    )
}

fun createWebhookMessage(config: DForBlockConfig, scope: CoroutineScope, kord: Kord, payload: BlockyMessagePayload) {
    val channel = config.findChannelByName(payload.channelName)
        ?: return logger.warning { "Failed to find channel with name '${payload.channelName}', are you sure it's configured?" }

    if (channel.webhookId == null || channel.webhookToken == null) {
        return logger.severe {
            "Attempted to create a webhook message with a misconfigured webhookId or webhookToken for channel '${payload.channelName}' with id '${channel.channelId}'."
        }
    }

    val webhookId = Snowflake(channel.webhookId)

    scope.launch {
        try {
            kord.rest.webhook.executeWebhook(webhookId, channel.webhookToken) {
                content = config.toDiscordChatFormat
                    .replace("{author}", payload.author)
                    .replace("{content}", payload.messageContent)
                username = payload.author
                payload.skinHint?.let { skinHint ->
                    avatarUrl = when (skinHint) {
                        is SkinHint.Minecraft -> {
                            config.minecraftAvatarProviderUrl
                                .replace("{username}", skinHint.username)
                                .replace("{uuid}", skinHint.uuid.toString())
                        }

                        is SkinHint.Hytale -> {
                            config.hytaleAvatarProviderUrl
                                .replace("{username}", skinHint.username)
                                .replace("{id}", skinHint.id)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warning { "Could not create webhook message: ${e.message}" }
        }
    }
}

fun sendDiscordMessage(messageContent: String, channel: Snowflake, scope: CoroutineScope, kord: Kord) {
    scope.launch {
        try {
            kord.rest.channel.createMessage(
                channelId = channel
            ) {
                content = messageContent
            }
        } catch (e: Exception) {
            logger.warning { "Could not create message: ${e.message}" }
        }
    }
}