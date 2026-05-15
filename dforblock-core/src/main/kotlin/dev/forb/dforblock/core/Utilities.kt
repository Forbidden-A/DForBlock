package dev.forb.dforblock.core

import dev.forb.dforblock.core.config.*
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.MessageFlags
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.interaction.GuildInteraction
import dev.kord.rest.builder.component.separator
import dev.kord.rest.builder.component.textDisplay
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.container
import dev.kord.rest.builder.message.embed
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

fun PermissionNode.isAllowed(interaction: GuildInteraction): Boolean = allowEveryone || interaction.user.id.value in allowedUsers || interaction.user.roleIds.any { it.value in allowedRoles }
fun PermissionNode.isProhibited(interaction: GuildInteraction): Boolean = !allowEveryone && interaction.user.id.value !in allowedUsers && interaction.user.roleIds.none { it.value in allowedRoles }
fun PermissionNode.check(interaction: GuildInteraction, reversed: Boolean = false): Boolean =  if (reversed) isProhibited(interaction) else isAllowed(interaction)

val Instant.duration: Duration get() = Clock.System.now() - this
val Duration.beautify: String get() = this.toComponents { days, hours, minutes, seconds, _ ->
    buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0) append("${hours}h ")
        if (minutes > 0) append("${minutes}m ")
        append("${seconds}s")
    }.trim()
}

fun prepareMinecraftMiniMessage(payload: DiscordMessageData, channelName: String, template: String): Component {
    val content = if (!payload.isAttachment) payload.content else "<click:open_url:'${payload.attachmentLink?:""}'><hover:show_text:'<gray>Click to open attachment'><aqua>${payload.content}</aqua></hover></click>"
    val processedString = template
        .replace("{author}", payload.author)
        .replace("{content}", content)
        .replace("{role}", payload.role)
        .replace("{channel}", channelName)
    val miniMessage = MiniMessage.miniMessage()
    return miniMessage.deserialize(processedString)
}

fun CommandExecutionConfig.isAllowed(command: String): Boolean {
    if (blacklist != null)
        return !blacklist.contains(command)

    if (whitelist != null)
        return whitelist.contains(command)

    return false
}

suspend fun ChannelConfig.createMessage(kord: Kord, template: MessageTemplate, messageBuilder: MessageBuilder.() -> Unit): Boolean {
    return try {
        if (template.asWebhook) {
            if (!allowsWebhooks)
                return false.also { LOGGER.error { "Could not create webhook message as channel '${template.targetChannel}' does not allow webhooks." } }
            kord.rest.webhook.executeWebhook(Snowflake(webhookId!!), webhookToken!!) {
                if (template.webhookPersonaName != null)
                    username = template.webhookPersonaName
                if (template.webhookPersonaAvatarUrl != null)
                    avatarUrl = template.webhookPersonaAvatarUrl
                messageBuilder()
            }
        }
        kord.rest.channel.createMessage(Snowflake(channelId), messageBuilder)
        true
    } catch (e: Exception) {
        LOGGER.error { "Could not create message in channel '${template.targetChannel}': ${e.message}\n${e.stackTraceToString()}" }
        false
    }
}


fun buildCommonPlaceholders(communicator: IBlockyCommunicator): Map<String, String> = buildCommonPlaceholders(communicator.serverStatistics())

fun buildCommonPlaceholders(statistics: GameStatistics): Map<String, String> {
    return mapOf(
        "{game}" to statistics.gameType.name,
        "{gameVersion}" to statistics.gameVersion,
        "{onlinePlayers}" to statistics.onlinePlayers.toString(),
        "{playerLimit}" to statistics.playerLimit.toString(),
        "{mspt}" to "%.2f".format(statistics.mspt),
        "{tps}" to "%.2f".format(statistics.tps),
        "{targetTps}" to "%.2f".format(statistics.targetTps),
        "{uptime}" to statistics.startup.duration.beautify
    )
}

fun buildPlayerPlaceholders(name: String, uuid: String, prefix: String?, suffix: String?): Map<String, String> {
    return mapOf(
        "{playerName}" to name,
        "{playerUuid}" to uuid,
        "{prefix}" to (prefix ?: ""),
        "{suffix}" to (suffix ?: ""),
    )
}

fun String.withPlaceholders(placeholders: Map<String, String>): String {
    var result = this
    for ((key, value) in placeholders) {
        result = result.replace(key, value)
    }

    return result
}

fun constructMessage(messageTemplate: MessageTemplate, placeholders: Map<String, String>): MessageBuilder.() -> Unit = {
    if (messageTemplate.container != null) {
        flags = MessageFlags(MessageFlag.IsComponentsV2)
        container {
            accentColor = messageTemplate.container.color
            messageTemplate.container.elements.forEach { element ->
                when (element) {
                    is ContainerElementTextDisplay -> textDisplay {
                        content = element.text.withPlaceholders(placeholders)
                    }

                    is ContainerElementSeparator -> separator {
                        spacing = element.spacingSize
                        divider = element.divider
                    }
                }
            }
        }
    }

    if(messageTemplate.standard != null) {
        content = messageTemplate.standard.content?.withPlaceholders(placeholders)
        if (messageTemplate.standard.embed != null) {
            embed {
                color = messageTemplate.standard.embed.color
                author {
                    name = messageTemplate.standard.embed.authorName?.withPlaceholders(placeholders)
                    icon = messageTemplate.standard.embed.authorIcon?.withPlaceholders(placeholders)
                    url = messageTemplate.standard.embed.authorUrl?.withPlaceholders(placeholders)
                }
                if (messageTemplate.standard.embed.footerText != null) {
                    footer {
                        text = messageTemplate.standard.embed.footerText.withPlaceholders(placeholders)
                        icon = messageTemplate.standard.embed.footerIconUrl?.withPlaceholders(placeholders)
                    }
                }
                description = messageTemplate.standard.embed.description?.withPlaceholders(placeholders)
                title = messageTemplate.standard.embed.title?.withPlaceholders(placeholders)
            }
        }
    }
}