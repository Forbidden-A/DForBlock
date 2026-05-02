package dev.forb.dforblock

import com.akuleshov7.ktoml.file.TomlFileReader

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import java.io.File

@Serializable
data class DForBlockConfig(
    val discordToken: String,
    val discordChatFormat: String,
    val minecraftChatFormat: String,
    val channels: Set<ChannelConfig>,
    val useWebhooks: Boolean = false,
    val minecraftAvatarProviderUrl: String = "https://mineatar.io/player/{uuid}",
    val hytaleAvatarProviderUrl: String = "https://hyvatar.io/render/{username}?size=128&rotate=22"
)

@Serializable
data class ChannelConfig(
    val channelName: String,
    val channelId: ULong,
    val webhookId: ULong?,
    val webhookToken: String?,
)

fun loadConfig(file: File): DForBlockConfig = TomlFileReader.decodeFromFile(serializer(), file.path)