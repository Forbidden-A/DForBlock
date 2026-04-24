package dev.forb.dforblock

import java.io.File

data class DForBlockConfig(
    val discordToken: String,
    val discordPrefix: String,
    val channels: HashMap<String, ULong>,
)

fun loadConfig(file: File): DForBlockConfig {
    TODO("Implement config loading")
}