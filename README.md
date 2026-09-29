# FancyNpcsGUI

An in-game editor for [FancyNpcs](https://modrinth.com/plugin/fancynpcs). Browse, create and edit NPCs through inventory menus instead of using a command for every setting. Made by Veloraxx; this is an independent addon, not an official FancyInnovations project.

![NPC editor overview](assets/editor-overview.png)

## Features

- NPC browser with search, teleporting, creation, duplication and deletion
- Display names with MiniMessage formatting, `\n` line breaks and a background toggle
- Player skins, skin mirroring and NPC skin previews in the menus
- Entity type, scale, glow and glow color settings
- Equipment editing for both hands and all four armor slots
- Turn-to-player, collision, visibility modes, viewing distance and interaction cooldown
- Position, rotation and entity-specific attributes
- Left-click, right-click and shared action chains, including editing, removal and reordering
- Sneak + right-click to open an NPC's editor, enabled by default

## Showcase

https://github.com/user-attachments/assets/4da70d60-f1d6-4367-be25-2506029c9b22

## Requirements

| Component | Version |
| --- | --- |
| FancyNpcsGUI | 1.0.0 |
| Minecraft / Paper | 1.21.11 |
| FancyNpcs | [2.12.1](https://modrinth.com/plugin/fancynpcs/version/2.12.1), required |
| Server Java | 25 or newer, required by FancyNpcs 2.12.1 |

Expected to work on Paper 1.21.11 with FancyNpcs 2.12.1 and Java 25 or newer. The build is verified against these API versions. Compatibility with other Minecraft or FancyNpcs versions is unverified and may be tested in the future. Spigot and Folia are not supported by this addon.

FancySitula is supplied by FancyNpcs; it does not need a separate installation. There are no other required plugins.

## Installation

1. Set up a Paper 1.21.11 server with Java 25 or newer.
2. Install FancyNpcs 2.12.1.
3. Download `FancyNpcsGUI-1.0.0.jar` from the [release](https://github.com/veloraxxx/FancyNpcsGUI/releases/tag/v1.0.0) and place it in `plugins/`.
4. Restart the server.

## Usage

Run `/npcgui` to open the browser, or sneak + right-click an NPC to open its editor. The shortcut can be disabled for each NPC under Behaviour.

Text fields use chat input. Type `cancel` to return without changing the value; input expires after 60 seconds. Display names accept `\n` for a new line and `@none` to hide the name, including through `/npc displayname`.

All permissions default to operators. GUI access requires both `fancynpcsgui.admin` and `fancynpcsgui.use`; `admin` does not grant the other permissions automatically.

| Permission | Allows |
| --- | --- |
| `fancynpcsgui.admin` | Access to the administration GUI, together with `use` |
| `fancynpcsgui.use` | Running `/npcgui` and using its menus, together with `admin` |
| `fancynpcsgui.create` | Creating and duplicating NPCs |
| `fancynpcsgui.edit` | Editing settings and equipment, teleporting, and the sneak-click shortcut |
| `fancynpcsgui.delete` | Deleting NPCs |
| `fancynpcsgui.actions` | Managing interaction actions |

Skin changes and duplication also require FancyNpcs' own `fancynpcs.command.npc.skin` and `fancynpcs.command.npc.copy` permissions respectively.

## Building

Install JDK 26. The Gradle wrapper uses Gradle 9.6.0 and downloads the compile-time dependencies.

```sh
git clone https://github.com/veloraxxx/FancyNpcsGUI.git
cd FancyNpcsGUI
./gradlew build
```

On Windows, use `gradlew.bat build`. The JAR is written to `build/libs/FancyNpcsGUI-1.0.0.jar`.

## Development

This project was developed by Veloraxx with assistance from AI tools, including OpenAI Codex.

## License

The addon source is licensed under [MIT](LICENSE). The Gradle wrapper retains its Apache 2.0 license notices. FancyNpcs and FancySitula are separate dependencies maintained by [FancyInnovations](https://github.com/FancyInnovations/FancyPlugins).
