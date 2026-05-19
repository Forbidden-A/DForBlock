package dev.forb.dforblock.core.discord

import dev.forb.dforblock.core.*
import dev.forb.dforblock.core.config.ConfigManager
import dev.kord.common.Color
import dev.kord.common.entity.*
import dev.kord.common.exception.RequestException
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.effectiveName
import dev.kord.core.entity.interaction.ActionInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.entity.interaction.GuildInteraction
import dev.kord.core.entity.interaction.GuildModalSubmitInteraction
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.component.actionRow
import dev.kord.rest.builder.message.container
import kotlin.random.Random
import kotlin.random.nextInt

class DiscordEventHandler(
    private val configManager: ConfigManager,
    private val communicator: IBlockyCommunicator
) {

    suspend fun GuildChatInputCommandInteractionCreateEvent.onDiscordChatCommand(
    ) {
        when (interaction.invokedCommandName) {
            "playerlist" -> showOnlinePlayers(interaction)
            "panel" -> sendNewPanel(interaction)
        }
    }

    suspend fun GuildButtonInteractionCreateEvent.onDiscordButtonPress(
    ) {
        when (interaction.componentId) {
            "BUTTON_STOP_SERVER" -> {
                handleStopServerButton(interaction)
            }

            "BUTTON_RUN_COMMAND" -> {
                handleRunCommandButton(interaction)
            }

            "BUTTON_SERVER_PLAYERS" -> {
                showOnlinePlayers(interaction)
            }

            "BUTTON_SERVER_STATUS" -> {
                handleServerStatusButton(interaction)
            }
        }
    }

    suspend fun GuildModalSubmitInteractionCreateEvent.onDiscordModalSubmit(
    ) {
        if (interaction.modalId == "MODAL_RUN_COMMAND")
            handleCommandRunModalSubmission(interaction)
    }

    suspend fun MessageCreateEvent.onDiscordMessageReceive() {
        if (message.author?.isBot ?: true)
            return

        if (message.content.isEmpty() && message.attachments.isEmpty()) {
            if (message.stickers.isEmpty())
                return

            LOGGER.warn { "detected empty discord message, are you sure you enabled the message content intent?" }
            return
        }

        try {
            message.getGuildOrNull() ?: return
        } catch (exception: RequestException) {
            LOGGER.warn { "Unexpected exception while getting guild: ${exception.stackTraceToString()}" }
            return
        }

        val member = message.getAuthorAsMemberOrNull() ?: message.author
        val name = member?.effectiveName ?: "Unknown"

        val payloads: MutableSet<DiscordMessageData> = mutableSetOf()

        if (message.content.isNotEmpty()) {
            payloads.add(
                DiscordMessageData(
                    author = name,
                    content = message.content,
                    channelId = message.channelId.value,
                    messageID = message.id.value,
                )
            )
        }

        if (message.attachments.isNotEmpty()) {
            message.attachments.forEachIndexed { index, attachment ->
                val payload = DiscordMessageData(
                    author = name,
                    content = "[Attachment #${index + 1}]",
                    channelId = message.channelId.value,
                    messageID = message.id.value,
                    isAttachment = true,
                    attachmentLink = attachment.url
                )
                payloads.add(payload)
            }
        }

        payloads.forEach { communicator.broadcastMessage(it) }
    }

    private suspend fun <I> showOnlinePlayers(
        interaction: I,
    ) where I : ActionInteraction, I : GuildInteraction {
        val response = interaction.deferEphemeralResponse()
        val permission = configManager.permissions.playerlistCommand
        if (permission.check(interaction, reversed = true)
        ) {
            response.respond {
                content = "**You do not have permission to use this command!**"
            }
            return
        }

        val onlinePlayerlist = communicator.onlinePlayers()
        val statistics = communicator.serverStatistics()
        val body = if (onlinePlayerlist.isEmpty()) "**Server is empty.**" else onlinePlayerlist.joinToString(
            separator = ", ",
            prefix = "**Online players (${statistics.onlinePlayers}/${statistics.playerLimit}): `",
            postfix = "`**"
        )

        response.respond {
            flags = MessageFlags(MessageFlag.IsComponentsV2)
            container {
                accentColor = Color(Random.nextInt(0..0xFFFF))
                textDisplay(body)
            }
        }
    }

    private suspend fun <I> handleServerStatusButton(
        interaction: I,
    ) where I : ActionInteraction, I : GuildInteraction {
        val response = interaction.deferEphemeralResponse()
        val permission = configManager.permissions.statusButton
        if (permission.check(interaction, reversed = true)) {
            response.respond {
                content = "**You do not have permission to use this button!**"
            }
            return
        }
        val statistics = communicator.serverStatistics()
        val mspt = "%.2f".format(statistics.mspt)
        val tps = "${"%.2f".format(statistics.tps)}/${"%.1f".format(statistics.targetTps)}"
        val body = """
            **Game**: ${statistics.gameType.name} ${statistics.gameVersion}
            **Players**: ${statistics.onlinePlayers}/${statistics.playerLimit}
            **Uptime**: ${statistics.startup.duration.beautify}
            **Running at**: ${mspt}mspt @ ${tps}tps
        """.trimIndent()
        response.respond {
            flags = MessageFlags(MessageFlag.IsComponentsV2)
            container {
                accentColor = Color(Random.nextInt(0..0xFFFF))
                textDisplay(body)
            }
        }
    }

    private suspend fun <I> sendNewPanel(
        interaction: I,
    ) where I : ActionInteraction, I : GuildInteraction {
        val response = interaction.deferPublicResponse()
        if (configManager.permissions.panelCommand.check(interaction, reversed = true)) {
            response.respond {
                content = "**You do not have permission to use this command!**"
            }
            return
        }

        try {
            response.respond {
                flags = MessageFlags(MessageFlag.IsComponentsV2)

                container {
                    accentColor = Color(Random.nextInt(0..0xFFFF))
                    actionRow {
                        interactionButton(style = ButtonStyle.Primary, customId = "BUTTON_SERVER_STATUS") {
                            label = "Status"
                        }
                        interactionButton(style = ButtonStyle.Primary, customId = "BUTTON_SERVER_PLAYERS") {
                            label = "Players"
                        }
                        interactionButton(style = ButtonStyle.Secondary, customId = "BUTTON_RUN_COMMAND") {
                            label = "Run Command"
                        }
                    }
                    actionRow {
                        interactionButton(style = ButtonStyle.Danger, customId = "BUTTON_STOP_SERVER") {
                            label = "Stop"
                        }
                        if (configManager.core.serverPanelUrl != null)
                            linkButton(url = configManager.core.serverPanelUrl ?: "") { label = "Open Panel" }
                    }
                }
            }
        } catch (exception: Exception) {
            LOGGER.error { "Failed to create panel: ${exception.stackTraceToString()}" }
        }
    }

    private suspend fun <I> handleStopServerButton(
        interaction: I,
    ) where I : ActionInteraction, I : GuildInteraction {
        val response = interaction.deferEphemeralResponse()

        if (configManager.permissions.stopButton.check(interaction, reversed = true)) {
            response.respond {
                content = "**You do not have permission to use this button!**"
            }
            return
        }

        response.respond {
            flags = MessageFlags(MessageFlag.IsComponentsV2)
            container {
                accentColor = Color(Random.nextInt(0..0xFFFF))
                textDisplay("**Stopping Server.. Goodbye.**")
            }
        }
        communicator.stopServer()
    }

    private suspend fun <I> handleRunCommandButton(
        interaction: I,
    ) where I : ComponentInteraction, I : GuildInteraction {
        if (configManager.permissions.runCommandButton.check(interaction, reversed = true)) {
            interaction.respondEphemeral {
                content = "**You do not have permission to use this button!**"
            }
            return
        }

        interaction.modal("Run Command", "MODAL_RUN_COMMAND") {
            label("Command") {
                textInput(TextInputStyle.Short, "MODAL_INPUT_COMMAND") {
                    placeholder = "time set day"
                    required = true
                    allowedLength = 1..1000
                }
            }
        }
    }

    private suspend fun handleCommandRunModalSubmission(
        interaction: GuildModalSubmitInteraction,
    ) {
        val response = interaction.deferEphemeralResponse()
        val commandContent = interaction.textInputs["MODAL_INPUT_COMMAND"]?.value ?: return
        val command = commandContent.split(" ").firstOrNull() ?: return
        if (!configManager.permissions.allowedCommands.isAllowed(command)) {
            response.respond {
                flags = MessageFlags(MessageFlag.IsComponentsV2)
                container {
                    accentColor = Color(Random.nextInt(0..0xFFFF))
                    textDisplay("**This command is not allowed.**")
                }
            }
            return
        }

        val commandResult = communicator.executeCommand(commandContent)
        response.respond {
            flags = MessageFlags(MessageFlag.IsComponentsV2)
            container {
                accentColor = Color(Random.nextInt(0..0xFFFF))
                val execution = "**Executed:** ${commandContent.take(50)}"
                textDisplay(execution)
                separator(SeparatorSpacingSize.Small)
                val resultTitle = "**Result:** ```txt"
                val commandResult = commandResult.take(((4000 - execution.length) - resultTitle.length) - 3)
                textDisplay("$resultTitle\n$commandResult```")
            }
        }
    }
}