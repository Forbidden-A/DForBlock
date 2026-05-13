package dev.forb.dforblock.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.songe.json5.decodeFromJson5String
import java.io.File

@Serializable
data class DForBlockConfig(
    val discordToken: String,
    val guildIds: Set<ULong>,
    val panelLink: String? = null,
    val useDefaultChannelTopic: Boolean,
    val useRichPresence: Boolean,
    val useStateOnly: Boolean,
    val richPresenceType: Int,
    val discordStatus: Int,
    val discordStreamUrl: String = "https://minecraft.net",
    val minecraftAvatarProviderUrl: String = "https://api.mineatar.io/head/{uuid}?size=16",
    val hytaleAvatarProviderUrl: String = "https://hyvatar.io/render/{username}?size=16&rotate=22",
    val formats: FormatsConfig,
    val channels: Map<String, ChannelConfig>,
    val permissions: PermissionsConfig,
    val runCommandList: CommandListConfig
)


@Serializable
data class FormatsConfig(
    val minecraftChatFormat: String,
    val hytaleChatFormat: String,
    val playerJoinMessage: String,
    val playerLeaveMessage: String,
    val playerDeathMessage: String,
    val mcAdvancementMadeMessage: String,
    val serverStartMessage: String,
    val serverStopMessage: String,
    val discordChatFormat: String,
    val discordWebhookChatFormat: String,
    val defaultChannelTopic: String,
    val discordPresenceText: String,
    val discordStateText: String,
)

@Serializable
data class ChannelConfig(
    val channelId: ULong,
    val useWebhooks: Boolean = false,
    val webhookId: ULong? = null,
    val webhookToken: String? = null,
)

@Serializable
data class PermissionConfig(val allowAll: Boolean = false, val roles: Set<ULong>, val users: Set<ULong>)

@Serializable
data class CommandListConfig(val blacklist: Set<String>? = null, val whitelist: Set<String>? = null)

@Serializable
data class PermissionsConfig(
    val playerlistCommand: PermissionConfig? = null,
    val panelCommand: PermissionConfig,
    val stopButton: PermissionConfig,
    val runCommandButton: PermissionConfig,
    val playersButton: PermissionConfig? = null,
    val statusButton: PermissionConfig? = null,
)

fun loadConfig(file: File, json: Json): DForBlockConfig = json.decodeFromJson5String(file.readText(Charsets.UTF_8))