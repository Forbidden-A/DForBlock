package dev.forb.dforblock

import dev.kord.common.entity.Snowflake
import java.io.File

data class DForBlockConfig(
    val discordToken: String,
    val discordChatFormat: String,
    val minecraftChatFormat: String,
    val channels: Set<ChannelConfig>,
    val useWebhooks: Boolean = false,
    val minecraftAvatarProviderUrl: String = "https://mineatar.io/player/{uuid}",
    val hytaleAvatarProviderUrl: String = "https://hyvatar.io/render/{username}?size=128&rotate=22"
)

data class ChannelConfig(
    val channelName: String,
    val channelId: ULong,
    val webhookId: ULong?,
    val webhookToken: String?,
)

fun loadConfig(file: File): DForBlockConfig {
    TODO("Implement config loading")
}