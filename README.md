# IaF: Control Ur Dragon

Command, protect and recover your Ice and Fire CE dragons.

Minecraft 1.21.1 · NeoForge 21.1 · Ice and Fire CE 2.1.4

## Why this mod exists

I'm 0sole. I was playing a big modpack with two tamed Ice and Fire dragons, one of them stage 4. We raided a dark forest camp full of barbarians, with Herobrine cultists casting blood magic around them. I turned around and my stage 4 dragon was just gone. Not dead, no corpse, no death message. A bit later the second one vanished the same way. I went to their last positions with the Dragon Care dragon phone and found nothing.

I dug through the logs and the mods' source code. The best I could prove was that the dragons had been deleted from the world without dying. I couldn't prove which mod did it, and even if I could, there was no guarantee the right repo would ever fix it. I had already wanted a proper dragon control mod for Ice and Fire anyway, so I decided to build one myself: something that keeps a backup of every dragon, tells you who deleted it, lets you bring it back, and gives you real control over your dragons.

### About AI

I built this with AI. The idea, the features, the design decisions and all the testing in game are mine, and a lot of the code was written by Claude (Anthropic) while I directed it. I'm not sorry about that. It let me turn a bad evening in Minecraft into a mod that solves the problem for me and hopefully for you. If that is a dealbreaker for you, that's fair, and now you know.

### Türkçe

Ben 0sole. Büyük bir mod paketinde iki evcil Ice and Fire ejderhamla oynuyordum, biri 4. evredeydi. Kara ormanda barbarların kampına baskın yaptık, yanlarında kan büyüsü yapan Herobrine müritleri vardı. Arkamı döndüğümde 4. evre ejderham yoktu. Ölmemişti, ceset yoktu, ölüm mesajı yoktu. Biraz sonra diğeri de aynı şekilde kayboldu. Dragon Care'in dragon phone'u ile son konumlarına gittim, hiçbir şey yoktu.

Logları ve modların kodlarını inceledim. Kanıtlayabildiğim tek şey ejderhaların ölmeden dünyadan silindiğiydi; hangi modun yaptığını kanıtlayamadım, bulsam bile ilgili reponun düzelteceğinin garantisi yoktu. Zaten Ice and Fire için düzgün bir ejderha kontrol modu yapmak istiyordum, ben de kendim yapmaya karar verdim.

Bu modu yapay zekâ ile yaptım. Fikir, özellikler, tasarım kararları ve oyundaki bütün testler benim; kodun büyük kısmını benim yönlendirmemle Claude (Anthropic) yazdı. Bundan pişman değilim.

## Features

**Dragon Whistle.** Keep it anywhere in your inventory and press `V` (or right click it) to open the dragon wheel. Pick a dragon, then a category:

| Category | Orders |
| --- | --- |
| Movement | Sit, Follow, Wander, Bring dragon |
| Combat | Attack what you looked at, Stop attacking, Guard area |
| Behavior | Stance, attack style, what to do when badly hurt, stand up when hit, break blocks |
| Other | Carry / Drop a creature, Rename |

Behavior settings are saved per dragon:

- **Stance:** Normal, Defensive (only fights what attacks it or you), Passive (never starts a fight), Aggressive (attacks anything it sees, except you and your pets)
- **Attack style:** Mixed, Breath only, Bite only
- **When badly hurt:** Keep fighting, Flee, Come to me, or go into an empty Dragon Horn in your inventory
- **Stand up when hit:** a sitting dragon gets up and fights back
- **Break blocks:** turn Ice and Fire's block breaking off for this dragon

The wheel does not pause or blur the game, and you can keep walking while it is open. Right click goes back one step. Targets are picked with a little aim assist, and grass or flowers don't block the aim.

With Curios installed the whistle also works from the necklace slot.

**Bring dragon** brings a dragon to you from anywhere, even from unloaded chunks or another dimension. A dragon in an unloaded chunk is brought back from the backup taken when its chunk unloaded, and the old copy is dropped if that chunk loads again, so it never duplicates. It asks for a second click and has a 30 minute cooldown.

**Carry** makes the dragon pick up whatever is under it (or fly to the creature you look at) and hold it under its feet. Players can be carried too. Carried creatures take no damage, and keep that protection for 2 seconds after being dropped; players don't get this protection. Works while riding too.

**Hotkeys** for every dragon of yours within 128 blocks:

| Key | Order |
| --- | --- |
| `G` | Attack the creature you look at |
| `H` | Follow |
| `J` | Guard the area you stand in |
| `N` | Stop attacking |
| `K` | Carry what is under your dragon / drop it |

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
