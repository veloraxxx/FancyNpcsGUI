# FancyNpcsGUI

<a href="https://modrinth.com/plugin/fancynpcsgui">
  <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg" height="56" alt="Available on Modrinth">
</a>

A GUI for [FancyNpcs](https://modrinth.com/plugin/fancynpcs). Open `/npcgui` to find, create and edit NPCs in-game.

Made by Veloraxx. This is an unofficial addon for FancyNpcs.

![NPC editor overview](assets/editor-overview.png)

## Features

- Search, create, copy and delete NPCs, or teleport to them
- Set display names with MiniMessage, `\n` line breaks and an optional background
- Change skins, mirror a player's skin and see skin previews in the GUI
- Change entity type, size, glow and glow color
- Equip both hands and all four armor slots
- Set visibility, viewing distance, collision, turn-to-player and click cooldown
- Edit position, rotation and entity attributes
- Add, edit and reorder left-click, right-click and shared actions
- Sneak + right-click an NPC to open its editor (enabled by default)

## Showcase

https://github.com/user-attachments/assets/4da70d60-f1d6-4367-be25-2506029c9b22

## Requirements

| Component | Version |
| --- | --- |
| FancyNpcsGUI | 1.1.0 |
| Minecraft / Paper | 1.21.6–1.21.11, 26.1.2, 26.2, 26.3 |
| FancyNpcs | [2.12.1](https://modrinth.com/plugin/fancynpcs/version/2.12.1) |
| Server Java | 25 or newer |

One JAR covers the versions above. Startup and shutdown were tested on each with FancyNpcs 2.12.1. GUI navigation, chat inputs, NPC creation/editing/copying/deletion, skins, equipment, attributes, actions, permissions and persistence after a restart were tested on Paper 1.21.6 (build 48) and 26.3 (build 140), using Java 25.

The automated test player on 26.3 connected through ViaVersion and ViaBackwards 5.12.0. Native 26.3 client rendering has not been manually checked. The 1.21.9 and 26.3 startup tests used Paper's available alpha (build 59) and beta (build 140) builds, respectively.

26.1 could not be tested because Paper has no downloadable build for it. FancyNpcs 2.12.1 refuses to load on 26.1.1, so that version is not supported. Spigot and Folia are not supported.

FancyNpcs 2.12.1 requires Java 25 or newer. It includes FancySitula, so you only need to install FancyNpcs and this addon.

## Installation

1. Set up a Paper server on a supported version above, with Java 25 or newer.
2. Install FancyNpcs 2.12.1.
3. Download `FancyNpcsGUI-1.1.0.jar` from the [release](https://github.com/veloraxxx/FancyNpcsGUI/releases/tag/v1.1.0) and place it in `plugins/`.
4. Restart the server.

To update from 1.0.1, replace the old addon JAR with 1.1.0 before restarting. Keep your existing FancyNpcs files and NPC data; don't leave both addon versions in `plugins/`.

## Usage

Run `/npcgui` to open the browser, or sneak + right-click an NPC to open its editor. The shortcut can be disabled for each NPC under Behaviour.

When a menu asks for text or a number, enter it in chat. Type `cancel` to go back. The prompt times out after 60 seconds.

Display names accept `\n` for line breaks and `@none` to hide the name. This works in the GUI and with `/npc displayname`.

## Permissions

Give `fancynpcsgui.admin` for full access. It includes `use` and all the other permissions below.

For limited access, give `fancynpcsgui.use` **plus** the permissions you want. `create`, `edit`, `delete` and `actions` each need `use` to work in the GUI. `use` on its own only lets players browse NPCs and view settings.

Operators have all permissions by default. `create` and `edit` also grant FancyNpcs' copy and skin command permissions, respectively.

| Permission | What it does | Also needs |
| --- | --- | --- |
| `fancynpcsgui.admin` | Everything below | — |
| `fancynpcsgui.use` | Open `/npcgui` and view NPC settings | — |
| `fancynpcsgui.create` | Create and copy NPCs | `use` |
| `fancynpcsgui.edit` | Edit settings, skins and equipment; teleport; use the sneak-click shortcut | `use` |
| `fancynpcsgui.delete` | Delete NPCs | `use` |
| `fancynpcsgui.actions` | Add, edit, remove and reorder actions | `use` |

## Building

Use JDK 26. The included Gradle 9.6.0 wrapper downloads the dependencies.

```sh
git clone https://github.com/veloraxxx/FancyNpcsGUI.git
cd FancyNpcsGUI
./gradlew build
```

On Windows, use `gradlew.bat build`. The JAR is written to `build/libs/FancyNpcsGUI-1.1.0.jar`. It compiles against Paper 1.21.6 and targets Java 21 bytecode; the server still needs Java 25 or newer to load FancyNpcs 2.12.1.

## Development

This project was developed by Veloraxx with assistance from AI tools.

## License

[MIT](LICENSE). The Gradle wrapper uses Apache 2.0. FancyNpcs and FancySitula are maintained by [FancyInnovations](https://github.com/FancyInnovations/FancyPlugins) and have their own licenses.
