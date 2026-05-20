package dev.forb.dforblock.core.discord

import dev.forb.dforblock.core.LOGGER
import dev.forb.dforblock.core.config.ChannelConfig
import dev.forb.dforblock.core.config.MessageTemplate
import dev.forb.dforblock.core.constructMessage
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.rest.builder.message.MessageBuilder
import kotlinx.coroutines.CancellationException

data class WebhookRequest(
    val username: String?,
    val avatarUrl: String?,
    val withComponents: Boolean
)

data class MessageCreateRequest(
    val targetChannel: Pair<String, ChannelConfig>,
    val builder: MessageBuilder.() -> Unit,
    val isWebhook: Boolean = false,
    val webhookPersona: WebhookRequest? = null,
    val placeholders: Map<String, String> = emptyMap(),
    val identifier: String
) {
    init {
        require(!(isWebhook && webhookPersona == null)) { "Attempt to create message request as webhook but persona was not provided." }
    }

    constructor(targetChannel: Pair<String, ChannelConfig>, template: MessageTemplate, placeholders: Map<String, String> = emptyMap(), webhookPersona: WebhookRequest?, identifier: String) : this(
        targetChannel,
        constructMessage(template, placeholders),
        template.asWebhook,
        webhookPersona,
        placeholders,
        identifier
    )

    private suspend fun fulfilAsWebhook(kord: Kord): Boolean {
        val (channelName, channelConfig) = targetChannel
        if (!channelConfig.allowsWebhooks) return false.also { LOGGER.error { "Message creation '$identifier' failed: Attempted to create a webhook message in '$channelName' which does now allow webhooks." } }
        val webhookId = channelConfig.webhookId?.let { Snowflake(it) }
            ?: return false.also { LOGGER.error { "Message creation '$identifier' failed: Malformed channel config for channel '$channelName', it allows webhooks but webhookId is null." } }
        val webhookToken = channelConfig.webhookToken
            ?: return false.also { LOGGER.error { "Message creation '$identifier' failed: Malformed channel config for channel '$channelName', it allows webhooks but webhookToken is null." } }
        val webhookPersona = webhookPersona
            ?: return false.also { LOGGER.error { "Asked to create a webhook message '$identifier' but webhookPersona is null." } }
        kord.rest.webhook.executeWebhook(webhookId, webhookToken, withComponents = webhookPersona.withComponents) {
            username = webhookPersona.username
            avatarUrl = webhookPersona.avatarUrl
            builder()
        }
        return true
    }

    private suspend fun fulfilAsNormal(kord: Kord): Boolean {
        val (_, channelConfig) = targetChannel
        kord.rest.channel.createMessage(Snowflake(channelConfig.channelId), builder)
        return true
    }

    internal suspend fun fulfil(kord: Kord): Boolean {
        try {
            if (isWebhook)
                return fulfilAsWebhook(kord)
            return fulfilAsNormal(kord)
        } catch (e: Exception) {
            if (e is CancellationException || e.cause is CancellationException || e.toString().contains("CancellationException")) {
                LOGGER.info { "Message creation was cancelled for message '$identifier'" + e.cause?.let { "\n${it.message ?: it.stackTraceToString()}" } }
            }
            else {
                LOGGER.error { "Message creation failed for message '$identifier': ${e.message}\n${e.stackTraceToString()}" }
            }
            return false
        }
    }
}