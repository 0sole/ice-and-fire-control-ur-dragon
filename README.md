# IaF: Control Ur Dragon

Command, protect and recover your Ice and Fire CE dragons.

Minecraft 1.21.1 · NeoForge 21.1 · Ice and Fire CE 2.1.4

## Features

**Dragon Whistle.** Keep it anywhere in your inventory and press `V` (or right click it) to open the dragon wheel. Pick a dragon, then a category:

| Category | Orders |
| --- | --- |
| Movement | Sit, Follow, Wander, Bring dragon |
| Combat | Attack what you looked at, Stop attacking, Guard area |
| Behavior | Normal, Defensive (only fights back), Passive (never attacks) |
| Other | Carry / Drop a creature, Rename |

The wheel does not pause or blur the game, and you can keep walking while it is open. Right click goes back one step.

**Bring dragon** teleports a dragon to you from anywhere, even from unloaded chunks or another dimension. It asks for a second click and has a 30 minute cooldown.

**Carry** makes the dragon pick up a creature and hold it under its feet. The carried creature takes no damage, so it can't suffocate or die from the fall when it is dropped. Works while riding too.

**Hotkeys** for every dragon of yours within 128 blocks:

| Key | Order |
| --- | --- |
| `G` | Attack the creature you look at |
| `H` | Follow |
| `J` | Guard the area you stand in |
| `N` | Stop attacking |
| `K` | Carry what you look at / drop it |

All keys can be changed under Options → Controls.

**Better following.** Dragons keep a distance that fits their size, run when they fall behind, fly straight instead of zigzagging, and land next to you.

**Lost dragon detection.** Every tamed dragon is backed up regularly. If one is deleted from the world without dying, the owner gets a chat message with the code that removed it and a button to bring it back. Ice and Fire's `kill()` is also fixed so it no longer silently deletes living dragons.

## Crafting

```
String     Dragon bone   -
Dragon bone    -        Dragon bone
   -       Dragon bone  Iron ingot
```

## Commands

- `/cud list` lists your dragons and their state
- `/cud info <uuid>` shows details, including what removed a lost dragon
- `/cud restore <uuid>` restores a lost dragon from its backup

## Languages

English, Türkçe, Русский, Español, Português, Deutsch.

## Configuration

`config/controlurdragon-common.toml`: cooldowns, guard radius, group range, follow behaviour, chat alerts.

## Building

1. Put the Ice and Fire CE jar and its libraries in `libs/` (see `libs/README.md`).
2. Run `./gradlew build`. The jar ends up in `build/libs/`.
3. `./gradlew runClient` starts a development client.
