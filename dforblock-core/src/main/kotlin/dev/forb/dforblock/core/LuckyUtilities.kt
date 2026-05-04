package dev.forb.dforblock.core

import net.luckperms.api.LuckPermsProvider
import java.util.UUID

fun luckpermsPrefixByUUID(player: UUID): String {
    val provider = LuckPermsProvider.get()
    val luckyUser = provider.userManager.getUser(player) ?: return ""
    return luckyUser.cachedData.metaData.prefix.orEmpty()
}

fun luckpermsPrefixByUsername(player: String): String {
    val provider = LuckPermsProvider.get()
    val luckyUser = provider.userManager.getUser(player) ?: return ""
    return luckyUser.cachedData.metaData.prefix.orEmpty()
}

fun luckpermsSuffixByUUID(player: UUID): String {
    val provider = LuckPermsProvider.get()
    val luckyUser = provider.userManager.getUser(player) ?: return ""
    return luckyUser.cachedData.metaData.suffix.orEmpty()
}

fun luckpermsSuffixByUsername(player: String): String {
    val provider = LuckPermsProvider.get()
    val luckyUser = provider.userManager.getUser(player) ?: return ""
    return luckyUser.cachedData.metaData.suffix.orEmpty()
}
