package dev.forb.dforblock

import dev.forb.dforblock.DForBlock.logger
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

fun prepareMinecraftMessage(payload: DiscordMessagePayload, config: DForBlockConfig): Component {
    val processedString = config.minecraftChatFormat
        .replace("{author}", payload.author)
        .replace("{content}", payload.content)

    config.channels.firstOrNull { it.channelId == payload.channelID }?.apply {
        processedString.replace("{channel}", channelName)
    }

    val miniMessage = MiniMessage.miniMessage()
    return miniMessage.deserialize(processedString)
}

suspend fun createMessage(config: DForBlockConfig, kord: Kord, payload: BlockyMessagePayload) {
    val channel = DForBlock.config.channels.firstOrNull { it.channelName.equals(payload.channelName, ignoreCase = true) }
    val channelId = Snowflake(
        channel?.channelId ?:
        return logger.warning { "Failed to find channel with name '${payload.channelName}', are you sure it's configured?" }
    )

    try {
        kord.rest.channel.createMessage(
            channelId = channelId
        ) {
            content = config.discordChatFormat
                .replace("{author}", payload.author)
                .replace("{content}", payload.messageContent)

        }
    } catch (e: Exception) {
        logger.warning { "Could not create message: ${e.message}" }
    }
}

suspend fun createWebhookMessage(config: DForBlockConfig, kord: Kord, payload: BlockyMessagePayload) {
    val channel = DForBlock.config.channels.firstOrNull { it.channelName.equals(payload.channelName, ignoreCase = true) }
    val channelId = Snowflake(
        channel?.channelId ?:
        return logger.warning { "Failed to find channel with name '${payload.channelName}', are you sure it's configured?" }
    )

    if (channel.webhookId == null || channel.webhookToken == null) {
        return logger.severe {
            "Attempted to create a webhook message with a misconfigured webhookId or webhookToken for channel '${payload.channelName}' with id '${channel.channelId}'."
        }
    }

    val webhookId = Snowflake(channel.webhookId)

    try {
        kord.rest.webhook.executeWebhook(webhookId, channel.webhookToken) {
            content = config.discordChatFormat
                .replace("{author}", payload.author)
                .replace("{content}", payload.messageContent)
            username = payload.author
            payload.skinHint?.let { skinHint ->
                avatarUrl = when(skinHint) {
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