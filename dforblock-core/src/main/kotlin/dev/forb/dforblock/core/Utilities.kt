package dev.forb.dforblock.core

import dev.forb.dforblock.core.config.*
import dev.forb.dforblock.core.discord.WebhookRequest
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.MessageFlags
import dev.kord.core.entity.interaction.GuildInteraction
import dev.kord.rest.builder.component.separator
import dev.kord.rest.builder.component.textDisplay
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.container
import dev.kord.rest.builder.message.embed
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

fun PermissionNode.isAllowed(interaction: GuildInteraction): Boolean =
    allowEveryone || interaction.user.id.value in allowedUsers || interaction.user.roleIds.any { it.value in allowedRoles }

fun PermissionNode.isProhibited(interaction: GuildInteraction): Boolean =
    !allowEveryone && interaction.user.id.value !in allowedUsers && interaction.user.roleIds.none { it.value in allowedRoles }

fun PermissionNode.check(interaction: GuildInteraction, reversed: Boolean = false): Boolean =
    if (reversed) isProhibited(interaction) else isAllowed(interaction)

fun CommandExecutionConfig.isAllowed(command: String): Boolean {
    if (blacklist != null)
        return !blacklist.contains(command)

    if (whitelist != null)
        return whitelist.contains(command)

    return false
}

val Instant.duration: Duration get() = Clock.System.now() - this
val Duration.beautify: String
    get() = this.toComponents { days, hours, minutes, seconds, _ ->
        buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            append("${seconds}s")
        }.trim()
    }

fun String.withoutFormatting(): String {
    val strippedMiniMessage = MiniMessage.miniMessage().stripTags(this)
    val component = LegacyComponentSerializer.legacyAmpersand().deserialize(strippedMiniMessage)
    return PlainTextComponentSerializer.plainText().serialize(component)
}

fun String.withPlaceholders(placeholders: Map<String, String>?): String {
    if (placeholders.isNullOrEmpty() || !this.contains("{")) {
        return this
    }

    var result = this
    for ((key, value) in placeholders) {
        if (result.contains(key)) {
            result = result.replace(key, value)
        }
    }

    return result
}

fun String.withPlaceholders(vararg placeholders: Pair<String, String>): String = withPlaceholders(mapOf(*placeholders))

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

suspend fun buildCommonPlaceholders(communicator: IBlockyCommunicator): Map<String, String> =
    buildCommonPlaceholders(communicator.serverStatistics())

fun buildPlayerPlaceholders(
    playerIdentity: PlayerData,
    configManager: ConfigManager,
    communicator: IBlockyCommunicator
): Map<String, String> {
    return mapOf(
        "{playerName}" to playerIdentity.name,
        "{playerDisplayName}" to (playerIdentity.displayName ?: ""),
        "{playerQualifiedName}" to playerIdentity.qualifiedName(configManager, communicator),
        "{playerUuid}" to playerIdentity.uuid.toString(),
        "{playerAvatar}" to (playerIdentity.buildAvatarUrl(configManager) ?: "")
    )
}

fun prepareMiniMessage(payload: DiscordMessageData, channelName: String, template: String): Component {
    val content =
        if (!payload.isAttachment) payload.content else "<click:open_url:'${payload.attachmentLink ?: ""}'><hover:show_text:'<gray>Click to open attachment'><aqua>${payload.content}</aqua></hover></click>"
    val processedString = template.withPlaceholders(
        "{author}" to payload.author,
        "{content}" to content,
        "{role}" to payload.role,
        "{channel}" to channelName
    )
    val miniMessage = MiniMessage.miniMessage()
    return miniMessage.deserialize(processedString)
}

fun MessageTemplate.webhookRequest(configManager: ConfigManager, communicator: IBlockyCommunicator, playerIdentity: PlayerData?): WebhookRequest? {
    return if (asWebhook) {
        val username = webhookPersonaName ?: playerIdentity?.qualifiedName(configManager, communicator) ?: configManager.core.serverPersonaName
        val avatarUrl = webhookPersonaAvatarUrl ?: playerIdentity?.qualifiedName(configManager, communicator) ?: configManager.core.serverPersonaAvatarUrl
        WebhookRequest(username, avatarUrl, container != null)
    } else null
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

    if (messageTemplate.standard != null) {
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
                messageTemplate.standard.embed.fields?.forEach { field ->
                    field(field.name, field.inline) { field.value ?: EmbedBuilder.ZERO_WIDTH_SPACE }
                }
            }
        }
    }
}