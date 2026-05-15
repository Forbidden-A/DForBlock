package dev.forb.dforblock.core.config

import kotlinx.serialization.Serializable

@Serializable
data class PermissionsConfig(
    // commands
    val playerlistCommand: PermissionNode = PermissionNode(true),
    val panelCommand: PermissionNode = PermissionNode(),

    // buttons
    val playersButton: PermissionNode = PermissionNode(true),
    val statusButton: PermissionNode = PermissionNode(true),
    val stopButton: PermissionNode = PermissionNode(),
    val runCommandButton: PermissionNode = PermissionNode(),

    // game commands
    val allowedCommands: CommandExecutionConfig = CommandExecutionConfig(),
)


@Serializable
data class PermissionNode(
    val allowEveryone: Boolean = false,
    val allowedUsers: Set<ULong> = emptySet(),
    val allowedRoles: Set<ULong> = emptySet(),
)

@Serializable
data class CommandExecutionConfig(
    val blacklist: Set<String>? = null,
    val whitelist: Set<String>? = null
) {
    init {
        require(!(blacklist != null && whitelist != null)) {
            "Configuration error: Cannot define both a whitelist and a blacklist for in-game commands."
        }
    }
}