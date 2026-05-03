package dev.forb.dforblock

import com.akuleshov7.ktoml.file.TomlFileReader

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import java.io.File

@Serializable
data class DForBlockConfig(
    val discordToken: String,
    val formats: FormatsConfig,
    val useDefaultChannelTopic: Boolean,
    val defaultChannelTopic: String,
    val channels: Set<ChannelConfig>,
    val useWebhooks: Boolean = false,
    val minecraftAvatarProviderUrl: String = "https://api.mineatar.io/head/{uuid}?size=16",
    val hytaleAvatarProviderUrl: String = "https://hyvatar.io/render/{username}?size=128&rotate=22"
)


@Serializable
data class FormatsConfig(
    val toDiscordChatFormat: String,
    val toMinecraftChatFormat: String,
    val toHytaleChatFormat: String,
    val serverStartedMessage: String,
    val serverStoppedMessage: String,
    val playerJoinedMessage: String,
    val playerLeftMessage: String,
    val playerDeathMessage: String,
    val mcTaskAdvancementMessage: String,
    val mcGoalAdvancementMessage: String,
    val mcChallengeAdvancementMessage: String,
)
@Serializable
data class ChannelConfig(
    val channelName: String,
    val channelId: ULong,
    val webhookId: ULong? = null,
    val webhookToken: String? = null,
)

fun loadConfig(file: File): DForBlockConfig = TomlFileReader.decodeFromFile(serializer(), file.path)