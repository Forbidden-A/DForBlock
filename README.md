# DForBlock
### This README file was first written using AI then changed a little, if you notice a mistake please open an issue. The README file is likely to be rewritten when 1.0.0 is out.
DForBlock is a highly configurable Discord-to-Game bridge powered by the Kord library. It connects your game server with
your Discord community, bridging chat, advancements, player deaths, and other various server events in real-time.

The following platforms are supported:

* [x] **Minecraft**
    * [x] Fabric
    * [x] Neoforge
    * [x] Paper
* [x] **Hytale**

Features include:

* **Two-Way Chat:** Chat in Discord and see it in-game, and vice versa.
* **Event Broadcasting:** Sends messages to Discord when players join, leave, die, or earn advancements(mc-only).
* **Discord Server Panel:** Generate an interactive control panel in Discord with buttons to check server status, view online players, run console commands, and stop the server.
* **Rich Presence:** Automatically updates the bot's status to show the current game, uptime, TPS, and online player count.
* **LuckPerms Integration:** Automatically fetches user prefixes and suffixes.

Configuration guide:

DForBlock uses **JSON5** for its configuration files. This is just like normal JSON, but it allows for comments (using `//`) and is more forgiving if you accidentally leave a trailing comma.

When you first run the server, the mod will generate a `dforblock` folder in your server's config/mods/plugins directory containing four config files.

Please use the default config files available in the config folder for reference.

**Tip:** To get Discord IDs for your channels, roles, or users, you need to enable **Developer Mode** in Discord. Go to User Settings > Advanced > turn on Developer Mode. Then, you can right-click any channel, user, or role and select "Copy Channel ID" / "Copy User ID"!

## Getting Started

1. Place the mod/plugin `.jar` into your server's `mods` or `plugins` folder.
2. Start the server once to generate the default configuration files OR copy the config files beforehand to skip this step.
3. Open `dforblock/*.json5` and configure the mod to your liking.
4. Restart the server. The bot should come online and bridge your server!