package dev.forb.dforblock.core

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.interaction.GuildInteraction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

fun DForBlockConfig.findChannelById(id: ULong): Pair<String, ChannelConfig>? = channels.entries.firstOrNull {
    it.value.channelId == id
}?.toPair()

fun PermissionConfig.isAllowed(interaction: GuildInteraction): Boolean = allowAll || interaction.user.id.value in users || interaction.user.roleIds.any { it.value in roles }
fun PermissionConfig.isProhibited(interaction: GuildInteraction): Boolean = !allowAll && interaction.user.id.value !in users && interaction.user.roleIds.none { it.value in roles }
fun PermissionConfig.check(interaction: GuildInteraction, reversed: Boolean = false): Boolean =  if (reversed) isProhibited(interaction) else isAllowed(interaction)

val Instant.duration: Duration get() = Clock.System.now() - this
val Duration.beautify: String get() = this.toComponents { days, hours, minutes, seconds, _ ->
    buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0) append("${hours}h ")
        if (minutes > 0) append("${minutes}m ")
        append("${seconds}s")
    }.trim()
}

fun prepareMinecraftMiniMessage(payload: DiscordMessageData, config: DForBlockConfig): Component {
    val channelEntry = config.findChannelById(payload.channelId)
    val channelName = channelEntry?.first ?: ""

    val processedString = config.formats.minecraftChatFormat
        .replace("{author}", payload.author)
        .replace("{content}", payload.content)
        .replace("{role}", payload.role)
        .replace("{channel}", channelName)

    val miniMessage = MiniMessage.miniMessage()
    return miniMessage.deserialize(processedString)
}

fun createMessage(
    config: DForBlockConfig,
    channel: ChannelConfig,
    scope: CoroutineScope,
    kord: Kord,
    payload: MinecraftMessageData
) {
    sendDiscordMessage(
        config.formats.discordChatFormat
            .replace("{player}", payload.player)
            .replace("{content}", payload.messageContent)
            .replace("{prefix}", payload.prefix)
            .replace("{suffix}", payload.suffix),
        Snowflake(channel.channelId),
        scope,
        kord,
    )
}

fun createWebhookMessage(
    config: DForBlockConfig,
    channel: ChannelConfig,
    scope: CoroutineScope,
    kord: Kord,
    payload: MinecraftMessageData
) {
    if (channel.webhookId == null || channel.webhookToken == null) {
        return LOGGER.error {
            "Attempted to create a webhook message with a misconfigured webhookId or webhookToken for channel '${payload.channelName}' with id '${channel.channelId}'."
        }
    }

    val webhookId = Snowflake(channel.webhookId)

    scope.launch {
        try {
            kord.rest.webhook.executeWebhook(webhookId, channel.webhookToken) {
                content = config.formats.discordWebhookChatFormat
                    .replace("{player}", payload.player)
                    .replace("{content}", payload.messageContent)
                    .replace("{prefix}", payload.prefix)
                    .replace("{suffix}", payload.suffix)
                username = payload.player
                if (payload.skinHint != null)
                    avatarUrl = when (payload.skinHint) {
                        is SkinHint.Minecraft -> {
                            config.minecraftAvatarProviderUrl
                                .replace("{username}", payload.skinHint.username)
                                .replace("{uuid}", payload.skinHint.uuid.toString())
                        }

                        is SkinHint.Hytale -> {
                            config.hytaleAvatarProviderUrl
                                .replace("{username}", payload.skinHint.username)
                                .replace("{id}", payload.skinHint.id)
                        }
                    }
            }
        } catch (e: Exception) {
            LOGGER.warn { "Could not create webhook message: ${e.stackTraceToString()}" }
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
            LOGGER.warn { "Could not create message: ${e.stackTraceToString()}" }
        }
    }
}