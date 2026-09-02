<div align="center">

# Overhaul

### Several mods in one. None of them required.

*A modular Fabric mod for Minecraft **26.2** — food, backpacks, magic, mobs, inventory and multiplayer.*
*Switch any of it off. Retune all of it from JSON.*

<br/>

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19%2B-DBD0B4?style=for-the-badge&logoColor=black)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-4C6EF5?style=for-the-badge)](LICENSE)

**[📖 Read the Wiki](https://github.com/Nexiimil/Overhaul/wiki)** ·
[Getting Started](https://github.com/Nexiimil/Overhaul/wiki/Getting-Started) ·
[Configuration](https://github.com/Nexiimil/Overhaul/wiki/Configuration) ·
[Config Reference](https://github.com/Nexiimil/Overhaul/wiki/Config-Reference)

</div>

<br/>

---

## The modules

<table>
<tr>
<td width="50%" valign="top">

### 🍅 Tasty

**New crops, new items, new meals.**

Put a bowl and a handful of ingredients anywhere in a crafting grid. No fixed shape — the recipe
reads what you gave it and builds the meal from it.

Eating plain food just feeds you. **Effects only come from meals**, and you can predict what a meal
does before you make it.

**[→ Tasty Module](https://github.com/Nexiimil/Overhaul/wiki/Tasty-Module)**

</td>
<td width="50%" valign="top">

### 🎒 Backpack

**Tiers, varying in size**

Leather, string and a chest gets you the first one. Every tier after that is a smithing upgrade — so
the bag keeps its contents when you upgrade it.

Carry more than three loaded bags and you get **Slowness and Mining Fatigue**, which is what makes
one bigger bag the answer instead of six small ones.

**[→ Backpack Module](https://github.com/Nexiimil/Overhaul/wiki/Backpack-Module)**

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 📖 Magical

**Enchanted gear you keep.**

*"Too Expensive!"* is gone, and so is the prior-work penalty that quietly turns your ol' reliable tool into
a consumable. Repairs cost **more material** instead, scaled to how enchanted the item is.

Bookshelves start empty and hold books you put in them — so an enchanting setup is something you
stock, not a wall you build.

The anvil learns two more jobs: **bottling experience** for a known price in points, and **splitting**
one enchantment off a book onto a blank one, so a five-enchantment book becomes five you can use.

Two enchantments come with it. **Shrouded** is a carved pumpkin you can see through. **Vein Mine**
finishes the seam — up to 32 of the same block, and only with the tool that was already right for it.

**[→ Magical Module](https://github.com/Nexiimil/Overhaul/wiki/Magical-Module)**

</td>
<td width="50%" valign="top">

### 💀 Mob

**A horde that stays a horde.**

Mobs belong to factions — Overworld, Nether, Ender, Illager — and will not damage or target their
own. A vanilla horde defuses itself the moment a skeleton clips a creeper. This one doesn't.

And on a bad night one comes for you: a **horde** drawn from a single faction, gated on the local
difficulty that rises in the chunks you actually live in.

Zombies vary in speed and call for help. Skeletons open fire from 26 blocks. Endermen take the solid
bulk out of your walls and leave the stairs and glass alone, so a base is something you **maintain**.
Anything badly wounded runs.

Villagers follow a held **emerald**, so moving one is a walk rather than a boat. Their stock is wider
too. And a dispenser full of wheat **feeds** what is standing in front of it.

**[→ Mob Module](https://github.com/Nexiimil/Overhaul/wiki/Mob-Module)**

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🧰 Inventory

**Quick-stacking, sorting, locked slots and a bin.**

Press one key and every stack you are carrying goes to the chest that **already holds it**. A chest becomes a filing destination by having something filed in it once, so there is nothing to set up per chest.

Sort any container by name or by mod, filling rows or columns. Lock the slots you want left alone.
Bin something into a slot that **holds the last thing you binned**, so a misclick is recoverable.
Open a shulker box straight from your hand.

**[→ Inventory Module](https://github.com/Nexiimil/Overhaul/wiki/Inventory-Module)**

</td>
<td width="50%" valign="top">

### 🚩 Multiplayer

**Land that belongs to someone.**

Claims ride on **vanilla teams** — the ones you already use for name colours and friendly fire — so
there is no second idea of who is with whom to disagree with the first.

Plant a banner and the chunk is your team's. Outsiders can't build in it and can't open anything;
allies can, minus whatever the leader has excluded. Exclusions take **tags**, so a modded door is a
door.

Players form and run their **own teams** — create, invite, kick, hand over, disband — so claims need
an operator exactly never. Vanilla's `/team` stays op-only for everything else.

**Chunk loaders** keep a chunk awake while you're away. An enchanting table's shape, in obsidian,
nether stars and an eye of ender.

**[→ Multiplayer Module](https://github.com/Nexiimil/Overhaul/wiki/Multiplayer-Module)**

</td>
</tr>
</table>

---

## Same rules, different families

Every ingredient belongs to a family, each family has exactly one effect. It should hold for modded food too, because anything unlisted falls back to the conventional `c:foods/*` tags.

```mermaid
flowchart LR
    B["🥣 Bowl<br/>the base"] --> M
    I1["🥩 Cooked Beef"] --> F1["Meat<br/>Strength"] --> M
    I2["🐟 Cooked Cod"] --> F2["Fish<br/>Water Breathing"] --> M
    I3["🥔 Baked Potato"] --> F3["Root<br/>Night Vision"] --> M
    M["🍲 Stew<br/>cooked only · x1.5"] --> O["Strength 45s<br/>Water Breathing 60s<br/>Night Vision 45s"]
```

Cooking an ingredient doubles what it contributes. A golden one triples it and adds a level. Repeat
the same ingredient and each copy is worth half the last — so five different things beat five steaks,
and four distinct ingredients earn a bonus level on everything.

**[→ Meals and Flavours](https://github.com/Nexiimil/Overhaul/wiki/Meals-and-Flavours)** for the full
table and the maths.

---

## Nothing is hardcoded

Everything lives in `config/overhaul/`, written on first launch.

```
config/overhaul/
├── overhaul.json     which modules are enabled
├── tasty.json        foods, crops, meals, flavour families
├── backpack.json     tiers, sizes, the upgrade ladder
├── magical.json      anvil rules, bookshelf behaviour, the two enchantments
├── mob.json          factions, per-mob tuning, dispensers, villager trades
├── inventory.json    quick-stacking, sorting, locked slots, the bin
└── multiplayer.json  claim defaults and limits, chunk loaders
```

> **Recipes are real recipes.** Every recipe in the config is a thin mirror of the vanilla recipe
> JSON, and Overhaul generates a data pack from it at startup. Change a `type` from `crafting_shaped`
> to `smelting` and you get a smelting recipe — anything vanilla can express, the config can
> express.

> **Claims are not in here.** Who owns a chunk, who leads a team and what that team allows all live
> in the world and are set in game with `/overhaul claim` — including forming the team itself, which
> would otherwise need an operator for every player joining one. This file only holds what a team
> starts out with and the limits an operator sets over all of them.

> **Adding new entries works too!** The `foods`, `crops`, `meals`, `tiers`,
> `teams.members` and `villagers.addedTrades` maps register whatever is in them. A pack can add its own food with its own recipe
> and effects, and the only thing it needs from outside the config is a texture.

**[→ Configuration](https://github.com/Nexiimil/Overhaul/wiki/Configuration)** ·
**[→ Config Reference](https://github.com/Nexiimil/Overhaul/wiki/Config-Reference)**

---

## Install

| | |
| --- | --- |
| **Minecraft** | 26.2 |
| **Fabric Loader** | 0.19.0+ |
| **Java** | 25+ |
| **Requires** | Fabric API |

Drop Fabric API and Overhaul into `mods/`, launch once, and the config appears.
Works on clients and dedicated servers — needed on both.

**[→ Getting Started](https://github.com/Nexiimil/Overhaul/wiki/Getting-Started)**

## Build

```sh
./gradlew build              # jar lands in build/libs/
./gradlew runClient          # play the mod in a dev client
./gradlew runServer          # dev dedicated server
./gradlew runClientGameTest  # a real client, launched by Gradle, asserting against a live world
```

Needs JDK 25 — Gradle will download one if you don't have it.

**[→ Building and Testing](https://github.com/Nexiimil/Overhaul/wiki/Building-and-Testing)** ·
**[→ Architecture](https://github.com/Nexiimil/Overhaul/wiki/Architecture)**

---

<div align="center">

**[📖 Wiki](https://github.com/Nexiimil/Overhaul/wiki)** ·
[FAQ](https://github.com/Nexiimil/Overhaul/wiki/FAQ) ·
[Compatibility](https://github.com/Nexiimil/Overhaul/wiki/Compatibility) ·
[Issues](https://github.com/Nexiimil/Overhaul/issues)

MIT licensed.

</div>
