package dev.forb.dforblock.core.config

import dev.kord.common.Color
import dev.kord.common.entity.SeparatorSpacingSize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessagesConfig(
    val serverStarts: MessageTemplate? = null,
    val serverStops: MessageTemplate? = null,
    val playerJoins: MessageTemplate? = null,
    val playerLeaves: MessageTemplate? = null,
    val playerDies: MessageTemplate? = null,
    val mcPlayerAdvances: MessageTemplate? = null,
    val playerChats: MessageTemplate? = null,
    val discordUserChats: String? = null,
)

@Serializable
data class MessageTemplate(
    val isEnabled: Boolean = true,
    val targetChannel: String = "default",
    val asWebhook: Boolean = false,
    val webhookPersonaName: String? = null,
    val webhookPersonaAvatarUrl: String? = null,
    val standard: StandardMessageTemplate? = null,
    val container: Container? = null,
) {
    init {
        require((standard != null) xor (container != null)) {
            "Configuration error: A message template must be either a standard message (v1) or a container message (v2)."
        }
    }
}

@Serializable
data class StandardMessageTemplate(
    val content: String? = null,
    val embed: EmbedConfig? = null,
) {
    init {
        require(!(content == null && embed == null)) {
            "Configuration error: A standard message cannot be empty."
        }
    }
}

@Serializable
data class EmbedConfig(
    val title: String? = null,
    val description: String? = null,
    val color: Color? = null,

    val authorName: String? = null,
    val authorUrl: String? = null,
    val authorIcon: String? = null,

    val footerText: String? = null,
    val footerIconUrl: String? = null,
) {
    init {
        require(authorName != null || title != null || description != null || footerText != null) {
            "Configuration error: An embed cannot be completely empty."
        }

        if (footerIconUrl != null)
            require(footerText != null) {
                "Configuration error: You cannot have a 'footerIconUrl' without also providing 'footer' text."
            }

        if (authorUrl != null || authorIcon != null)
            require(authorName != null) { "Configuration Error: Cannot have 'authorUrl' or 'authorIcon' without 'authorName'." }

    }
}

@Serializable
data class Container(val color: Color? = null, val elements: List<ContainerElement>)

@Serializable
sealed class ContainerElement

@Serializable
@SerialName("textDisplay")
data class ContainerElementTextDisplay(val text: String) : ContainerElement()

@Serializable
@SerialName("separator")
data class ContainerElementSeparator(val spacingSize: SeparatorSpacingSize? = SeparatorSpacingSize.Small, val divider: Boolean? = null): ContainerElement()