# Changelog

Versions are `<minecraft version>.<major>.<minor>`, so `26.2.0.1` is the first minor release for
Minecraft 26.2. A backport to another Minecraft version keeps its own line — `1.16.1.0.1` would be
the same release on 1.16.1.

## 26.2.0.1 — beta

First release of the multiplayer module, two enchantments, two new anvil jobs, and a handful of
additions to the mob and food modules.

### Added

**Multiplayer module** — new, module id `multiplayer`.

- **Chunk claims built on vanilla teams.** A chunk belongs to a team rather than a player, so land
  outlives whoever claimed it. Plant a banner or run `/overhaul claim here`.
- **Three classes of visitor.** Members are unrestricted; allies may build and interact by default;
  everyone else may do neither. Each class has a list of exceptions that *reverse* the rule for what
  they name, taking ids or `#tags` — so a modded door is a door with nothing configured.
- **Players run their own teams** with `/overhaul claim team`: create, invite, join, leave, kick,
  transfer, disband. Vanilla's `/team` is operator-only from its root down, so without these a
  claim would need an operator for every player joining a team.
- **Explosions, pistons and fire** are all stopped at a claim boundary. Each is a way to change the
  world with nobody to ask about, so each is answered by where the blocks are rather than by who is
  doing it.
- **Deleting a team releases its land**, in every dimension. That includes vanilla `/team remove`.
- **Chunk loaders.** An enchanting table's shape in obsidian, nether stars and an eye of ender,
  holding chunks through vanilla's own forceload. They do not raise local difficulty and do not
  spawn mobs — inhabited time only accrues near a player.

**Magical module**

- **Shrouded**, a helmet enchantment endermen do not react to being looked at through. A carved
  pumpkin without the cost of wearing one.
- **Vein Mine**, up to 32 of the same block from the one you broke, on any mining tool. An axe takes
  that wood's own leaves with it. Requires the tool that actually gets drops from the block, which
  is the whole balance of it.
- **Bottling experience at an anvil** from a water bottle and lapis, priced in exact experience
  points plus a surcharge.
- **Splitting enchanted books at an anvil**: the first enchantment moves onto a blank book and the
  source keeps the rest.

**Mob module**

- **Dispensers feed animals** in front of them. What counts as food is asked of the animal, so
  modded animals and modded feed work with nothing configured.
- **Villagers follow a held emerald**, so moving one is a walk rather than a boat.
- **Eighteen added villager trades** across eight professions, emitted as real trade files into the
  vanilla pools.

**Tasty module**

- **Glistering melon slices are edible** — four hunger and richly saturating. Still a brewing
  ingredient.
- **Golden Baked Potato**, a baked potato ringed with gold nuggets.
- **`vanillaFoods`**, a config map that gives food data to any item that already exists, retunes one
  that has it, or takes it away.

### Changed

- **Endermen no longer take stairs, slabs, glass, walls or fences.** They still take the solid bulk
  of a build, which is the intended amount of damage; the partial blocks are the ones whose absence
  reads as broken rather than as weathered. `carryPartialBlocks` in `mob.json`, now `false` by
  default — an existing config keeps whatever it already had.
- **Experience bottles are worth a fixed 10** rather than a random 3 to 11, so the anvil can quote a
  price that means something. `xpBottles.fixThrownBottleValue` in `magical.json` turns this off.
- **`/overhaul` no longer requires operator permissions at its root.** `difficulty` and `moon` carry
  the requirement themselves; `claim` is open to everyone.
- **Mod versions are now `<minecraft version>.<major>.<minor>`.**

### Known issues

Claims answer for players, explosions, pistons and fire. They do **not** stop:

- **Fluids** — lava or water poured outside a claim still flows in.
- **Mob griefing other than explosions** — endermen, ravagers and villager farming ignore claims.
  Endermen taking blocks out of a claimed build is deliberate; the rest is not yet handled.

Operators and creative-mode players bypass claims by default, so a solo tester will see claims do
nothing. Set `claims.bypassPermission` to `none` to test them properly.
