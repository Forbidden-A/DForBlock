package dev.forb.dforblock.core

import net.luckperms.api.LuckPermsProvider
import java.util.*

fun luckPermsQualifiedName(format: String, uuid: UUID, username: String, displayName: String?): String {
    val provider = LuckPermsProvider.get()
    val luckyUser = provider.userManager.getUser(uuid) ?: return displayName ?: username
    return format.withPlaceholders(
        mapOf(
            "{playerName}" to username,
            "{playerQualifiedName}" to (displayName ?: username),
            "{prefix}" to luckyUser.cachedData.metaData.prefix.orEmpty(),
            "{suffix}" to luckyUser.cachedData.metaData.suffix.orEmpty()
        )
    )
}