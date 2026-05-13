package dev.forb.dforblock.core.config

import dev.forb.dforblock.core.LOGGER
import kotlinx.serialization.json.Json
import li.songe.json5.decodeFromJson5String
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
            core = loadOrGenerate("core.json5")

            channels = loadOrGenerate("channels.json5")

            require(channels.isNotEmpty()) { "Configuration error: Must include a channel." }

            permissions = loadOrGenerate("permissions.json5")

            messages = loadOrGenerate("messages.json5")

            LOGGER.info { "Configurations loaded successfully." }
            true
        } catch (e: Exception) {
            LOGGER.error { "Failed to load configurations: ${e.message}\n${e.stackTraceToString()}" }
            false
        }
    }

    private inline fun <reified T> loadOrGenerate(fileName: String): T {
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

        val fileContent = file.readText(Charsets.UTF_8)
        return json.decodeFromJson5String<T>(fileContent)
    }

}