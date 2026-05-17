# DForBlock

DForBlock is a highly configurable Discord-to-Game bridge powered by the Kord library. It connects your game server with your Discord community, bridging chat, advancements, player deaths, and other various server events in real-time.

## 🎮 Supported Platforms

DForBlock is designed from the ground up to be platform-agnostic at its core. It plans to support the following platforms:

* [x] **Minecraft**
    * [x] Fabric
    * [ ] Neoforge
    * [ ] Forge
    * [ ] Paper
* [ ] **Hytale**

## ✨ Features

* **Two-Way Chat:** Chat in Discord and see it in-game, and vice versa.
* **Event Broadcasting:** Sends messages to Discord when players join, leave, die, or earn advancements.
* **Discord Server Panel:** Generate an interactive control panel in Discord with buttons to check server status, view online players, run console commands, and stop the server.
* **Rich Presence:** Automatically updates the bot's status to show the current game, uptime, TPS, and online player count.
* **LuckPerms Integration:** Automatically fetches and displays user prefixes and suffixes.

## ⚙️ Configuration Guide

DForBlock uses **JSON5** for its configuration files. This is just like normal JSON, but it allows for comments (using `//`) and is much more forgiving if you accidentally leave a trailing comma.

When you first run the server, the mod will generate a `dforblock` folder in your server's config directory containing four config files. Here is how to set them up:

### 1. `core.json5` (Bot Identity & Status)
This file controls the bot's core connection and what it displays on its Discord profile.

* **`discordToken`**: Your Discord Bot Token. **(Required)**
* **`guildIds`**: A list of the Discord Server IDs where the bot will register its slash commands. **(Required)**
* **`discordStatus`**: The dot colour next to your bot (`Online`, `DoNotDisturb`, `Idle`, `Invisible`).
* **Rich Presence (Status Text)**: Show live server stats right on the bot's profile! *(Choose only one)*:
    * `showThinkingBubble` (and `thinkingBubbleText`): Makes the bot look like it is thinking (e.g., "Playing Minecraft with 5 friends").
    * `showActivity` (and `activityText`): The classic "Watching / Playing" status (e.g., "Watching 5 playing Minecraft 26.1.2").
* **Personas & Avatars**:
    * `serverPersonaName`: The default name the bot uses when sending **Webhook** messages as the "Server" (e.g., "DForBlock").
    * `minecraftAvatarProviderUrl` / `hytaleAvatarProviderUrl`: These are API links used to fetch player head images. Used as fallback for player-related events when a custom url is not provided for the event.

### 2. `channels.json5` (Where the Bot Talks)
Define the Discord channels the bot is allowed to interact with. You can set up multiple channels and each with a custom name (like `staff`).

* **`channelId`**: The Discord ID of your text channel.
* **`topicTemplate`**: Want your channel's description to update automatically? Put variables like `{tps}` or `{onlinePlayers}` here, and the bot will update the channel topic every 5 minutes.
* **Webhooks (Optional)**:
    * **What is a webhook?** Normally, the bot can only send messages as itself. A webhook allows the bot to "shape-shift", changing its name and profile picture to exactly match the player who's talking.
    * Set `allowsWebhooks: true` and provide a `webhookId` and `webhookToken` (which you can generate in your Discord Channel Settings -> Integrations -> Webhooks).

### 3. `messages.json5` (The Look and Feel)
This is where you make things look pretty. You can customise exactly how messages from the game look in Discord, and vice versa.

* **`discordUserChats`**: How Discord messages look inside the game chat (e.g., `[{role}] {author} » {content}`  -> `[Discord] Forbidden » I have no roles ;-;` | `[Staff] 1RSA » I am staff.` )
* **Game Events**: You can configure `playerChats`, `playerJoins`, `playerLeaves`, `playerDies`, `mcPlayerAdvances`, `serverStarts`, and `serverStops`.
    * `isEnabled`: Set to `false` to completely mute an event.
    * `targetChannel`: Tell the bot which channel from `channels.json5` to send this to.
    * `asWebhook`: Set to `true` to use the webhook feature mentioned earlier.

**Message Formatting: Standard vs. Container**
For every event, you must choose **one** of two layout styles:

1.  **`standard`**
    Great for simple text or classic coloured boxes (Embeds).
    * `content`: Plain text sent by the bot (e.g., `**{playerName} has joined the game.**`).
    * `embed`: A coloured box where you can set a `title`, `description`, `color` (you can use hex, like `0xFFCCFF`), and add little icons using `authorIcon` or `footerIconUrl`.

2.  **`container` (Discord Components V2)**
    Containers let you stack User Interface (UI) elements like building blocks.
    * Inside the `elements` list, you can add a `textDisplay` block to show text, or a `separator` block to draw a line between text.
    * *Example:* For advancements, you can stack the player's name, draw a thick separator line (`spacingSize: "2"`), and then show the advancement name below it.

**Placeholders you can use:**
* **Server Info:** These will be available in most if not all contexts: `{game}`, `{gameVersion}`, `{onlinePlayers}`, `{playerLimit}`, `{mspt}`, `{tps}`, `{targetTps}`, `{uptime}`.
* **Player Info:** These will be available in player-related events: `{playerName}`, `{playerUuid}`, `{prefix}`, `{suffix}`.

### 4. `permissions.json5`
Control who is allowed to interact with the bot's commands and panel buttons.

* **Commands/Buttons**: For features like the `panelCommand` or `stopButton`, you can set:
    * `allowEveryone`: `true` means anyone can click it. `false` locks it down.
    * `allowedUsers` / `allowedRoles`: Add specific Discord User IDs or Role IDs here to grant them admin access.
* **`allowedCommands`**: When a user uses the "Run Command" button on the Discord panel, this limits what commands can be run.
    * Use `blacklist` to block specific commands (e.g., `["op", "stop"]`).
    * **OR** use `whitelist` to only allow specific commands (e.g., `["time", "weather"]`). *(You cannot use both blacklist and whitelist).*

---
Please use the default config files available in the config folder for reference.

**💡 Tip:** To get Discord IDs for your channels, roles, or users, you need to enable **Developer Mode** in Discord. Go to User Settings > Advanced > turn on Developer Mode. Then, you can right-click any channel, user, or role and select "Copy Channel ID" / "Copy User ID"!

## 🚀 Getting Started

1. Place the mod `.jar` into your server's `mods` or `plugins` folder.
2. Start the server once to generate the default configuration files.
3. Open `config/dforblock/*.json5` and configure the mod to your liking.
4. Restart the server. The bot should come online and bridge your server!

---

## 🛠️ TODO
* **Discord Developer Panel:** Include images detailing bot creation and intent selection
* **Server Watchdog:** Add optional Watchdog messages where are sent to a target channel if the server becomes unresponsive for X seconds. 
