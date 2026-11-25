# Cozy Cafe+

**English** | [Русский](README.ru.md)

**Download:** [CurseForge](https://www.curseforge.com/minecraft/mc-mods/cozy-cafe-plus) · [GitHub Releases](https://github.com/bloeiendelgtbbt/cozy-cafe-plus/releases)

A Fabric fork of [Cozy Cafe](https://github.com/Chakyl/Cozy-Cafe) by Nitbe and Chakyl, built for multiplayer:
run your own cafe on a server with waiters, player and NPC guests, a priced menu, bills, ratings and debts.
A real-life job simulator. In English and Russian.

## Features

- **A cafe is a Privit claim.** One Cafe Manager per claim. The owner hires waiters,
  and serving is only possible while wearing the apron.
- **A menu of any items** priced in a currency the owner picks. Settings, cash register, debtors list, rating.
- **Player guests** sit down at a table and order themselves. Orders can be served in parts and cancelled.
  Waiting timers, a bill with payment and a 1 to 5 star rating. Leave without paying and you get a debt.
- **NPC guests** walk like villagers and open the cafe's doors. They come by day and only while the owner
  or a waiter is on site; the higher the rating, the more often. They pay by how filling the served food is
  (netherite scrap by default).
- **Shift report** for the owner on closing, an OPEN / CLOSED sign, waypoints for Xaero's Minimap.

## Dependencies

| Mod | |
|---|---|
| Minecraft 1.20.1, Fabric Loader ≥ 0.15, Fabric API | required |
| [Privit](https://modrinth.com/mod/privit) 1.4.6+ | required (claims) |
| [Sit](https://modrinth.com/mod/bl4cks-sit) + Cloth Config | required |
| Trinkets | optional (apron rules for accessories) |
| Xaero's Minimap | optional (cafe waypoints) |

## Building

```
./gradlew build          # jar in build/libs
./gradlew runGametest    # automated tests
```

Dependencies are downloaded from Modrinth Maven, Ladysnake Maven and Maven Central.

## How it was made

Developed by bloeiende with AI assistance (Claude): the idea, game rules, mechanics decisions and testing are by bloeiende,
most of the code was written with the AI. Based on Cozy Cafe by Nitbe and Chakyl.

## Contact

bloeiende@icloud.com

## License

GPL-3.0, like the original (see [LICENSE.txt](LICENSE.txt)). What was taken from the original and what was changed
is listed in [CREDITS.txt](CREDITS.txt).
