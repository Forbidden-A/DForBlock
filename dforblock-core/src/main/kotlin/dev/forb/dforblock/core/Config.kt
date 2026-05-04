package dev.forb.dforblock.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import li.songe.json5.decodeFromJson5String

@Serializable
data class DForBlockConfig(
    val discordToken: String,
    val useDefaultChannelTopic: Boolean,
    val minecraftAvatarProviderUrl: String = "https://api.mineatar.io/head/{uuid}?size=16",
    val hytaleAvatarProviderUrl: String = "https://hyvatar.io/render/{username}?size=16&rotate=22",
    val formats: FormatsConfig,
    val channels: Map<String, ChannelConfig>,
)


@Serializable
data class FormatsConfig(
    val minecraftChatFormat: String,
    val hytaleChatFormat: String,
    val playerJoinMessage: String,
    val playerLeaveMessage: String,
    val playerDeathMessage: String,
    val mcChallengeAdvancementMessage: String,
    val mcGoalAdvancementMessage: String,
    val mcTaskAdvancementMessage: String,
    val serverStartMessage: String,
    val serverStopMessage: String,
    val discordChatFormat: String,
    val defaultChannelTopic: String,
)
@Serializable
data class ChannelConfig(
    val channelId: ULong,
    val useWebhooks: Boolean = false,
    val webhookId: ULong? = null,
    val webhookToken: String? = null,
)

fun loadConfig(file: File, json: Json): DForBlockConfig = json.decodeFromJson5String(file.readText(Charsets.UTF_8))