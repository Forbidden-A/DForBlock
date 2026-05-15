package dev.forb.dforblock.core.config

import dev.forb.dforblock.core.LOGGER
import kotlinx.serialization.json.Json
import li.songe.json5.decodeFromJson5String
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

class ConfigManager(val configDir: Path, val json: Json) {
    lateinit var core: Core
        private set
    lateinit var channels: Map<String, ChannelConfig>
        private set
    lateinit var permissions: PermissionsConfig
        private set
    lateinit var messages: MessagesConfig
        private set

    fun load(): Boolean {
        return try {
            LOGGER.info { "Loading DForBlock configurations..." }
            configDir.createDirectories()

            val coreFile = ensureFile("core.json5")
            val channelsFile = ensureFile("channels.json5")
            val permissionsFile = ensureFile("permissions.json5")
            val messagesFile = ensureFile("messages.json5")

            core = loadFile(coreFile)
            channels = loadFile(channelsFile)
            require(channels.isNotEmpty()) { "Configuration error: Must include a channel." }
            permissions = loadFile(permissionsFile)
            messages = loadFile(messagesFile)

            LOGGER.info { "Configurations loaded successfully." }
            true
        } catch (e: Exception) {
            LOGGER.error { "Failed to load configurations: ${e.message}\n${e.stackTraceToString()}" }
            false
        }
    }

    private fun ensureFile(fileName: String): File {
        val file = (configDir / fileName).toFile()

        if (!file.exists()) {
            val resourceStream = this::class.java.getResourceAsStream("/$fileName")
            if (resourceStream != null) {
                file.outputStream().use { output ->
                    resourceStream.copyTo(output)
                }
                LOGGER.warn { "Generated default configuration file: $fileName" }
            } else {
                throw IllegalStateException("Missing default resource for $fileName! Is the mod modified?")
            }
        }

        return file
    }

    private inline fun <reified T> loadFile(file: File): T {
        val fileContent = file.readText(Charsets.UTF_8)
        return json.decodeFromJson5String<T>(fileContent)
    }

}