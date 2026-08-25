# Overhaul

A modular Fabric mod for Minecraft **26.2**. Four independent modules — food, backpacks, magic and
mobs — each of which can be switched off, and every item, recipe and number in them can be retuned
from JSON without touching code.

## Building and running

```sh
./gradlew build              # jar lands in build/libs/
./gradlew runClient          # play the mod in a dev client
./gradlew runServer          # dev dedicated server
./gradlew runClientGameTest  # automated client tests, see below
./gradlew vscode             # regenerate VS Code launch configs
```

Requires JDK 25. If you do not have one, Gradle downloads it automatically the first time you build.

`./gradlew vscode` writes `.vscode/launch.json` with entries for the client, the server and both
gametest runs, so they can be started from the Run panel or with F5. It is not committed because it
contains absolute paths — regenerate it after cloning. IntelliJ and Eclipse have their own tasks
(`ideaSyncTask`, `genEclipseRuns`).

## Testing

Some of this mod only exists once a world is running: whether a backpack screen actually opens,
whether a meal comes out of the grid with the effects the flavour table promises, whether carrying
too much really slows you down. Those are covered by **client gametests** — a real client, launched
by Gradle, that creates a world and asserts against the live game.

```sh
./gradlew runClientGameTest
```

The tests live in `src/gametest/` as a separate mod, and currently check that every module's content
reaches the registry, that cooked corn plus a chilli crafts into an elote carrying both Speed and
Fire Resistance, that an iron backpack opens a 27-slot screen, and that four loaded backpacks apply
Slowness and Mining Fatigue which then expire once the bags are gone. Screenshots land in
`build/run/clientGameTest/screenshots/`.

This needs a display, so it is deliberately **not** part of `./gradlew build`. The build does run the
headless server-side `runGameTest`, which keeps ordinary builds and CI safe; add `-x runGameTest` to
skip even that.

Worth stating plainly: writing these found a real bug. The mod replaced `PackRepository.sources`
with an immutable set, and the Fabric resource loader adds to that same field when the world
creation screen opens — so creating a new singleplayer world crashed. Nothing in compiling the mod,
starting a dedicated server, or opening a client to the title screen goes near that path.

## Configuration

Everything lives in `config/overhaul/`, written on first launch:

| File | What it controls |
| --- | --- |
| `overhaul.json` | Which modules are enabled |
| `tasty.json` | Foods, crops, meals and the flavour family table |
| `backpack.json` | Backpack tiers, sizes and the upgrade ladder |
| `magical.json` | Anvil repair rules and bookshelf behaviour |
| `mob.json` | Mob teams and per-mob tuning |

Two things are worth knowing about how the config works.

**Recipes are real recipes.** Every recipe in the config is a thin mirror of the vanilla recipe
JSON, and Overhaul generates a data pack from it at startup rather than parsing it itself. Changing
a recipe's `type` from `crafting_shaped` to `smelting` produces a genuine smelting recipe; anything
vanilla can express, the config can express. Ingredient fields take an item id
(`minecraft:leather`), a tag (`#c:strings`), or a comma-separated list meaning "any of these".

**Adding entries works, not just editing them.** The `foods` map registers whatever is in it, so a
pack can add its own food with its own recipe and effects and only needs to supply a texture. The
same is true of backpack tiers and mob teams.

Set `rewriteConfigsOnLoad` to `false` in `overhaul.json` if you want to hand-edit the files and keep
your own formatting; the trade is that options added by a future update will not appear on disk
until you delete the file.

---

## Module 1 — Tasty

Four crops, seventeen items, and six meals built on a rule you can learn in one sitting.

### Crops

Seeds drop from grass, ferns and tall grass. A fully grown crop drops its produce, a seed, and up
to a couple of bonus produce.

| Crop | Seed | Produce | Nutrition | Family |
| --- | --- | --- | --- | --- |
| Tomato | Tomato Seeds | Tomato | 2 | Fruit |
| Lettuce | Lettuce Seeds | Lettuce | 1 | Leaf |
| Corn | Corn Seeds | Corn | 2 | Grain |
| Chili Pepper | Chili Pepper Seeds | Chili Pepper | 1 | Spice |

### Foods

| Item | Nutrition | Made from | Family / quality |
| --- | --- | --- | --- |
| Cooked Corn | 5 | Corn, smelted, smoked or on a campfire | Grain, cooked |
| Popcorn | 3 | Corn, blasted | Grain, cooked |
| Flour | — | 2 wheat | Grain, raw |
| Dough | — | Flour + water bucket | Grain, raw |
| Pasta | 5 | Dough + flour | Grain, cooked |
| Toast | 6 | Bread, smoked or on a campfire | Grain, cooked |
| Cheese | 4 | Milk bucket + flour (makes 2) | Dairy, cooked |
| Tomato Sauce | 4 | 2 tomato + chili + bowl | Fruit, cooked |
| Jam | 4 | 2 sweet berries + sugar + bottle | Fruit, cooked |
| Chocolate | 3 | Cocoa + sugar + milk (makes 2) | Sweet, cooked |
| Trail Mix | 6 | Glow berries + sweet berries + cocoa + seeds (makes 3) | Fruit, cooked |
| Fried Egg | 3 | Egg, smelted or on a campfire | Meat, cooked |
| Honey Glazed Ham | 10 | Cooked porkchop + honey bottle + sugar | Meat, **golden** |

Flour and dough are not edible — they are crafting ingredients that still carry a flavour, so they
count in a meal.

### Meals

Put a base plus ingredients anywhere in a crafting grid. There is no fixed shape; the recipe reads
what you gave it.

| Meal | Base | Ingredients | Effect duration | Returns |
| --- | --- | --- | --- | --- |
| Salad | Bowl | 2–4, any | ×1.0 | Bowl |
| Stew | Bowl | 2–5, **cooked only** | ×1.5 | Bowl |
| Sandwich | Bread | 1–3, any | ×1.0 | — |
| Skewer | Stick | 2–4, **cooked only** | ×0.75 | — |
| Pie | Dough | 2–3, any | ×2.0 | — |
| Elote | Cooked Corn | 1 chili pepper, fixed | ×1.5 | — |

A meal's nutrition and saturation come from what went in; its effects come from the flavour rules
below. Two rules stop meals becoming a stat dump: repeating an ingredient contributes half as much
each time, so five different things beat five steaks, and a meal of four or more distinct
ingredients gets a **variety bonus** of one extra effect level.

**Elote is a named dish rather than an open-ended one.** Its `allowedIngredients` names a chili
pepper and nothing else, so the recipe is exactly cooked corn plus a chili. It also sets
`baseCountsAsIngredient`: a bowl or a stick is only a container, but the cob is the larger half of
an elote, so it contributes its own Speed alongside the chili's Fire Resistance. Nothing about that
is elote-specific in code — both effects still come out of the flavour families.

Either option is available to any meal. Give a meal an `allowedIngredients` list to turn it into a
fixed dish, or leave it empty for something open-ended; the list takes item ids and, with a leading
`#`, tags.

### Flavour families — the pattern

**Every ingredient belongs to a family, and every family has exactly one effect.** That is the whole
rule. Meat means strength whether it is beef, mutton or a modded steak; grain means speed whether it
is corn, bread or pasta.

| Family | Effect | Base | What is in it |
| --- | --- | --- | --- |
| **Meat** | Strength | 15s | All meat and eggs |
| **Fish** | Water Breathing | 20s | Cod, salmon, tropical fish |
| **Grain** | Speed | 15s | Corn, wheat, bread, pasta, popcorn, toast, flour, dough |
| **Root** | Night Vision | 15s | Carrot, potato, beetroot |
| **Leaf** | Jump Boost | 15s | Lettuce, kelp |
| **Fruit** | Regeneration | 6s | Tomato, apple, melon, berries, jam, trail mix |
| **Sweet** | Absorption | 20s | Sugar, cocoa, honey, chocolate, cookies, pumpkin pie |
| **Dairy** | Haste | 20s | Milk, cheese |
| **Spice** | Fire Resistance | 15s | Chili pepper |
| **Fungus** | Resistance | 15s | Brown and red mushrooms |
| **Foul** | Hunger | 15s | Rotten flesh, poisonous potato |

**Preparing an ingredient makes it stronger; it never changes what it does.**

| Quality | Duration | Amplifier | Example |
| --- | --- | --- | --- |
| Raw | ×1 | — | Beef → 15s Strength |
| Cooked | ×2 | — | Cooked beef → 30s Strength |
| Golden | ×3 | **+1** | Golden carrot → 45s Night Vision II |

So the final duration of one ingredient's effect is
`family base × quality × meal multiplier`, and repeats of the same ingredient halve each time.
A stew of cooked beef, cooked cod and baked potato gives Strength 30s × 1.5, Water Breathing 40s ×
1.5 and Night Vision 30s × 1.5. Add a fourth distinct ingredient and every one of those goes up a
level.

Three ingredients ignore the families because what they do to you *is* the point: spider eye gives
poison, pufferfish gives nausea and poison, and suspicious stew sometimes blinds you.

**Modded food works without configuration.** Anything not listed by name falls back to the
conventional food tags — `c:foods/cooked_meat`, `c:foods/berry`, `c:foods/vegetable` and so on — so
a modded steak lands in Meat and a modded berry in Fruit on its own.

Eating any of these plain just feeds you. Effects only ever come from meals, which is what makes
cooking worth the trouble rather than an optional flourish.

## Module 2 — Backpack

Six tiers of portable storage, from a nine-slot leather bag to a fifty-four-slot netherite one.

- The first backpack is crafted from leather, string and a chest.
- Each tier after that is a **smithing table** upgrade: backpack + upgrade template + copper, iron,
  gold, diamond or netherite. Because it is a smithing transform, the upgraded backpack keeps its
  contents automatically.
- The **upgrade template** is found in loot chests, duplicated with leather and string, or turned
  back into four leather by putting it alone in a crafting grid.

Right-click to open, or press **B** to open the first backpack in your inventory. Row counts are
capped at six because backpacks reuse the vanilla chest screens — one to six rows map exactly onto
the vanilla `generic_9x1` through `generic_9x6` menus, so there is no custom GUI to break when a
resource pack changes.

**Overburdening.** Carrying more than **three loaded backpacks** at once gives you Slowness and
Mining Fatigue, and every bag past that raises both by a level up to a cap of III. Without a limit
there is no reason not to carry a bag for every kind of loot, which makes the upgrade ladder
pointless — six leather bags would beat one netherite one. This makes the ladder the answer: one
bigger bag rather than more bags.

| Option | Default | What it does |
| --- | --- | --- |
| `maxCarried` | 3 | Bags you can carry before it starts to weigh on you |
| `effects` | Slowness, Mining Fatigue | What being overburdened does, each with a base amplifier |
| `amplifierPerExtra` | 1 | Levels added per bag past the limit |
| `maxAmplifier` | 3 | Ceiling on that escalation |
| `ignoreEmpty` | true | Empty bags are just leather and do not count |
| `weighBySize` | false | Count rows instead of bags, so a netherite bag weighs six |
| `showParticles` | false | Effect particles, off because the effect is near-permanent |
| `checkIntervalTicks` | 20 | How often the check runs |
| `enabled` | true | Turn the whole thing off |

The effects are topped up on a timer rather than tracked, so they simply fade a few seconds after
you drop below the limit — nothing to clean up on death, logout or a config change. Creative and
spectator players are exempt, and the count covers your whole inventory including armour and
offhand slots.

**Accessory mod support.** Curios and its Fabric equivalents assign items to slots through item
tags, so Overhaul publishes `curios:back`, `accessories:back` and `trinkets:back` tags containing
every backpack. With such a mod installed a backpack drops into the back slot on its own; without
one the tags sit unused and nothing depends on them. No accessory mod is required, and none is
compiled against.

## Module 3 — Magical

**Anvils.** The "Too Expensive!" wall is gone, and so is the prior-work penalty that doubles an
item's repair cost after every use — that penalty is what actually makes well-enchanted gear
disposable. In its place, repairing costs more raw material the more enchanted the item is: one
extra ingot, diamond or plank per level of enchantment on it, capped so a fully enchanted item is
still repairable in one go. Both halves and the scaling are config options.

**Bookshelves.** Vanilla bookshelves start empty and hold books you put in them — any book, plus
anything else listed in the config. Because shelves no longer contain books, they are crafted from
planks alone and drop themselves when broken. An empty shelf no longer powers an enchanting table,
so reaching level 30 means actually stocking your library.

Contents are stored as a chunk data attachment rather than a block entity. Vanilla's bookshelf is a
plain `Block`, and the only way to give it a block entity is to make every block in the game an
`EntityBlock` and then unpick the consequences — far too large a blast radius for storing six item
stacks. The trade-off is that stored books are not visible on the shelf; the shelf renders empty
whether it holds one book or six.

## Module 4 — Mob

**Teams.** Mobs in the same faction can neither damage nor target one another:

| Team | Members |
| --- | --- |
| Overworld | Zombies, skeletons, creepers, spiders and their variants |
| Nether | Ghasts, wither skeletons, piglins, zombified piglins, hoglins, blazes |
| Ender | Endermen, endermites, shulkers, phantoms |
| Illager | Witches, pillagers, vindicators, evokers, ravagers, vexes |

This is the change that matters most. A vanilla horde defuses itself the moment a skeleton clips a
creeper; keeping a faction from turning on itself means a horde stays a horde. Cancelling the damage
alone is not enough — a mob would still walk over and swing forever — so teammates are refused as
targets too.

**Per-mob behaviour:**

- **Zombies** each pick a movement speed from a range, fixed by their own id so it survives chunk
  reloads. A pack therefore arrives strung out rather than as one wall. A wounded zombie may call
  another up out of the ground nearby, with a per-zombie cap and cooldown. A zombie's corpse
  sometimes gets back up as a skeleton.
- **Skeletons** trade health and speed for reach: frailer and slower, but they open fire from 26
  blocks out and their arrows fly far straighter than vanilla's. Dangerous at distance, weak up
  close.
- **Creepers** carry a random status effect into their explosion, which vanilla then turns into a
  lingering cloud.
- **Endermen** pick up anything that renders as a full solid block, plus stairs, slabs, glass, walls
  and fences — but never anything with a block entity, anything unbreakable, or anything on the
  blocklist. They will rearrange your builds.
- **Spiders** below half health may leave a cobweb where they stood.
- **Everything else:** any mob, hostile or passive, that drops below half health tries to break off
  and run rather than fight to the end. Bosses and a few others are excluded by config.

---

## Null analysis

Minecraft 26.2 ships JSpecify annotations — its packages are `@NullMarked` and its nullable returns
are marked — so an IDE with null analysis on will check every call into it. This mod is null-marked
too: each package has a `package-info.java` declaring `@NullMarked`, and anything genuinely nullable
carries `@Nullable`. That is what makes the analysis useful rather than noisy, because both sides of
every call have a contract.

Two deliberate exceptions:

- **`com.overhaul.mixin` is not null-marked.** Mixin classes are stubs — `@Shadow` fields are never
  assigned by this source and the methods are spliced into vanilla classes at load time, so a null
  contract there would describe code that does not exist yet. Hooks that genuinely take a nullable
  value annotate the parameter directly.
- **`nullUncheckedConversion` is off** in `.settings/org.eclipse.jdt.core.prefs`. It fires whenever
  a value from an un-annotated library crosses into annotated code, and the JDK, Gson and most of
  the Fabric API carry no null annotations — well over a hundred call sites that no change here
  could fix. Everything that finds real problems stays on.

Those settings are committed so the whole project sees the same result. To turn the analysis off
entirely instead, set `"java.compile.nullAnalysis.mode": "disabled"` in VS Code. None of this
affects the build: `javac` ignores the annotations, and the jar is identical either way.

## Layout

```
src/main/java/com/overhaul/
  core/            module lifecycle, config loading, the generated data pack
  module/tasty/    food, crops, meals
  module/backpack/ backpacks and the upgrade template
  module/magical/  anvil rules and bookshelf storage
  module/mob/      teams and mob tuning
  mixin/           the vanilla hooks each module needs
tools/             asset and texture generators
```

`tools/TextureGen.java`, `tools/genassets.sh` and `tools/genlang.sh` regenerate the textures, model
JSON and language file. The art is generated placeholder pixel art — readable at inventory size and
consistent across the set, and easy to replace by dropping real PNGs over the output.
