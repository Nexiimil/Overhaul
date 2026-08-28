package com.overhaul.gametest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.overhaul.module.backpack.BackpackItem;
import com.overhaul.module.inventory.FillOrder;
import com.overhaul.module.inventory.OpenCarriedPayload;
import com.overhaul.module.inventory.SlotLocks;
import com.overhaul.module.inventory.ToggleSlotLockPayload;
import com.overhaul.module.inventory.TrashPayload;
import com.overhaul.module.inventory.QuickStackPayload;
import com.overhaul.module.inventory.SortMode;
import com.overhaul.module.inventory.SortPayload;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Drives a real client through the parts of Overhaul that only exist once a world is running.
 *
 * <p>Most of the mod can be checked by compiling it or by watching a server start, but the things
 * most likely to be subtly wrong — whether a backpack actually opens, whether a meal comes out of
 * the crafting grid with the effects the flavour table promises, whether carrying too much really
 * slows you down — only show up in play. This test does those in order, against a live game.
 */
public class OverhaulClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		AtomicReference<MoonPhase> rotated = new AtomicReference<>();
		AtomicReference<MoonPhase> held = new AtomicReference<>();

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerConnection connection = singleplayer.getConnection();
			connection.waitForChunksRender();

			checkContentRegistered(singleplayer);
			checkEloteCraftsWithBothFlavours(singleplayer);
			checkBackpackOpens(context, singleplayer);
			checkOverburdenedSlowsYouDown(context, singleplayer);
			checkQuickStackFollowsWhatIsAlreadyThere(context, singleplayer);
			checkSortObeysBothToggles(context, singleplayer);
			checkLockedSlotsAreLeftAlone(context, singleplayer);
			checkTrashVoidsAndGivesBack(context, singleplayer);
			checkShulkerBoxOpensWhereItSits(context, singleplayer);
			checkMoonBendsWithoutMovingTheClock(context, singleplayer);
			checkDifficultyCeilingIsDerivedNotAssumed(singleplayer);
			bendTheMoonAndLeaveIt(context, singleplayer, rotated, held);

			context.takeScreenshot("overhaul-world");
			save = singleplayer.getWorldSave();
		}

		// Reopening the save starts a new server against the same level.dat, which is a restart in
		// every way that matters to state meant to outlive one.
		try (TestSingleplayerContext reopened = save.open()) {
			reopened.getConnection().waitForChunksRender();
			checkMoonSurvivedTheRestart(context, reopened, rotated, held);
		}
	}

	/** The registry is the one place every module's content has to end up, whatever else went on. */
	private static void checkContentRegistered(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			List.of("overhaul:tomato", "overhaul:elote", "overhaul:cooked_corn",
							"overhaul:backpack", "overhaul:netherite_backpack",
							"overhaul:backpack_upgrade_smithing_template")
					.forEach(OverhaulClientGameTest::item);
		});
	}

	/**
	 * Puts a cooked corn and a chilli through the real recipe manager and checks the elote that
	 * comes back carries both families' effects — speed from the cob, fire resistance from the
	 * chilli. This is the whole flavour system end to end: recipe lookup, assembly, and the
	 * components the finished food ends up with.
	 */
	private static void checkEloteCraftsWithBothFlavours(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			CraftingInput input = CraftingInput.of(2, 1, List.of(
					new ItemStack(item("overhaul:cooked_corn")),
					new ItemStack(item("overhaul:chili_pepper"))));

			Optional<RecipeHolder<CraftingRecipe>> match = server.getRecipeManager()
					.getRecipeFor(RecipeType.CRAFTING, input, server.overworld());

			RecipeHolder<CraftingRecipe> holder = match.orElseThrow(
					() -> new AssertionError("No crafting recipe matched cooked corn + chili pepper"));

			ItemStack result = holder.value().assemble(input);

			if (result.getItem() != item("overhaul:elote")) {
				throw new AssertionError("Expected an elote, got " + result);
			}

			Consumable consumable = result.get(DataComponents.CONSUMABLE);

			if (consumable == null) {
				throw new AssertionError("Elote came out with no consumable component");
			}

			List<String> effects = consumable.onConsumeEffects().stream()
					.filter(ApplyStatusEffectsConsumeEffect.class::isInstance)
					.map(ApplyStatusEffectsConsumeEffect.class::cast)
					.flatMap(effect -> effect.effects().stream())
					.map(instance -> instance.getEffect().getRegisteredName())
					.toList();

			if (!effects.contains("minecraft:speed") || !effects.contains("minecraft:fire_resistance")) {
				throw new AssertionError("Elote should carry speed and fire resistance, had " + effects);
			}
		});
	}

	/** A backpack is only useful if the screen actually opens, which needs a real client to see. */
	private static void checkBackpackOpens(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("overhaul:iron_backpack")));

			// Open the stack that is actually held, not the one just handed over: putting an item
			// into an inventory copies it and clears the original, and the backpack rightly refuses
			// to open an empty stack.
			BackpackItem.open(player, player.getItemInHand(InteractionHand.MAIN_HAND));
		});

		singleplayer.getConnection().waitForClientboundPackets();
		context.waitForScreen(AbstractContainerScreen.class);

		context.runOnClient(client -> {
			if (client.player == null) {
				throw new AssertionError("No client player while the backpack screen was open");
			}

			// Three rows of nine, plus the player's own inventory and hotbar.
			int slots = client.player.containerMenu.slots.size();

			if (slots != 27 + 36) {
				throw new AssertionError("Iron backpack should show 27 slots, menu had " + (slots - 36));
			}
		});

		context.takeScreenshot("overhaul-backpack-open");
		context.setScreen(() -> null);
		context.waitTicks(2);
	}

	/**
	 * Fills four backpacks and checks the carrier slows down. The effect is applied on a timer, so
	 * this waits past one check interval rather than expecting it on the same tick.
	 */
	private static void checkOverburdenedSlowsYouDown(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			player.getInventory().clearContent();

			// Loaded, because empty bags are meant to weigh nothing.
			for (int i = 0; i < 4; i++) {
				ItemStack backpack = new ItemStack(item("overhaul:backpack"));
				backpack.set(DataComponents.CONTAINER,
						ItemContainerContents.fromItems(List.of(new ItemStack(item("minecraft:cobblestone"), 8))));
				player.getInventory().add(backpack);
			}
		});

		// One check interval is 20 ticks; give it two so the timing is not a coin flip.
		context.waitTicks(45);

		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);

			if (!player.hasEffect(MobEffects.SLOWNESS)) {
				throw new AssertionError("Carrying four loaded backpacks should apply slowness");
			}

			if (!player.hasEffect(MobEffects.MINING_FATIGUE)) {
				throw new AssertionError("Carrying four loaded backpacks should apply mining fatigue");
			}
		});

		// And that it lets go again once the load is gone.
		singleplayer.getServer().runOnServer(server -> onlyPlayer(server).getInventory().clearContent());
		context.waitTicks(80);

		singleplayer.getServer().runOnServer(server -> {
			if (onlyPlayer(server).hasEffect(MobEffects.SLOWNESS)) {
				throw new AssertionError("Slowness should expire once the backpacks are gone");
			}
		});
	}

	/**
	 * The moon is the one thing in the mod where the mechanics and the picture are computed
	 * separately — the server from its clock, the client from its own — so the only way to know
	 * they agree is to ask both in a running game. Single player shares the override between the
	 * two, so what the client half proves is that the mixin reaches the client's own attribute
	 * system, not that the payload carries the value; a dedicated server would be needed for
	 * that. This also pins down the property the whole design exists for: bending the moon must not
	 * move the world clock, because world time feeds local difficulty and moving it would quietly
	 * retune the hordes these commands are for.
	 */
	private static void checkMoonBendsWithoutMovingTheClock(ClientGameTestContext context,
			TestSingleplayerContext singleplayer) {
		AtomicReference<MoonPhase> expected = new AtomicReference<>();

		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos pos = onlyPlayer(server).blockPosition();

			// Midday, so that the couple of ticks that pass later in this test cannot drift the
			// day index and make the assertions depend on where the world happened to start.
			run(server, "time set noon");

			MoonPhase before = phaseAt(level, pos);
			MoonPhase target = shift(before, 3);
			long clockBefore = level.getOverworldClockTime();

			run(server, "overhaul moon " + target.getSerializedName());

			MoonPhase after = phaseAt(level, pos);

			// Landing on target + 3 instead would mean the rotation was applied twice, which is
			// what would happen if getValue and getDimensionValue ever started delegating to one
			// another. A pin would survive that; a rotation does not.
			if (after != target) {
				throw new AssertionError("Asked for " + target.getSerializedName() + ", moon reads "
						+ after.getSerializedName() + " (was " + before.getSerializedName() + ")");
			}

			if (level.getOverworldClockTime() != clockBefore) {
				throw new AssertionError("Bending the moon moved the world clock from " + clockBefore
						+ " to " + level.getOverworldClockTime() + ", which drags local difficulty with it");
			}

			expected.set(target);
		});

		// A day on, the cycle should have carried on from the phase we set rather than snapping
		// back to the clock's own or staying stuck where it was put.
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			Holder<WorldClock> clock = level.dimensionType().defaultClock()
					.orElseThrow(() -> new AssertionError("The overworld has no clock"));

			server.clockManager().addTicks(clock, MoonPhase.PHASE_LENGTH);
		});

		context.waitTicks(2);

		singleplayer.getServer().runOnServer(server -> {
			MoonPhase wanted = shift(expected.get(), 1);
			MoonPhase now = phaseAt(server.overworld(), onlyPlayer(server).blockPosition());

			if (now != wanted) {
				throw new AssertionError("A day after setting " + expected.get().getSerializedName()
						+ " the moon should read " + wanted.getSerializedName() + ", not " + now.getSerializedName());
			}

			expected.set(wanted);
		});

		singleplayer.getConnection().waitForClientboundPackets();

		context.runOnClient(client -> {
			if (client.level == null || client.player == null) {
				throw new AssertionError("No client level while checking the moon");
			}

			MoonPhase drawn = client.level.environmentAttributes()
					.getValue(EnvironmentAttributes.MOON_PHASE, client.player.position());

			if (drawn != expected.get()) {
				throw new AssertionError("Server is running a " + expected.get().getSerializedName()
						+ " but the client would draw a " + drawn.getSerializedName());
			}
		});

		// The pin outranks the rotation, and is picked up by a poll rather than a change callback,
		// so it takes up to a second rather than landing on the same tick. Pinned to the opposite
		// side of the cycle from where the rotation currently has it, so that the assertion cannot
		// pass by the two happening to agree.
		AtomicReference<MoonPhase> pinned = new AtomicReference<>();

		singleplayer.getServer().runOnServer(server -> {
			MoonPhase pin = shift(expected.get(), MoonPhase.COUNT / 2);

			pinned.set(pin);
			run(server, "gamerule overhaul:fixed_moon_phase " + pin.getSerializedName());
		});

		context.waitTicks(25);

		singleplayer.getServer().runOnServer(server -> {
			MoonPhase now = phaseAt(server.overworld(), onlyPlayer(server).blockPosition());

			if (now != pinned.get()) {
				throw new AssertionError("The pin should outrank the rotation: pinned "
						+ pinned.get().getSerializedName() + " over a rotated "
						+ expected.get().getSerializedName() + ", moon reads " + now.getSerializedName());
			}

			run(server, "gamerule overhaul:fixed_moon_phase none");
		});

		context.waitTicks(25);

		// And with the pin off the rotation is still there, then reset puts the cycle back on the
		// clock. Without a reset the only way back would be to know the true phase already.
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos pos = onlyPlayer(server).blockPosition();
			MoonPhase rotated = phaseAt(level, pos);

			if (rotated != expected.get()) {
				throw new AssertionError("Clearing the pin should leave the rotation, moon reads "
						+ rotated.getSerializedName());
			}

			run(server, "overhaul moon reset");

			if (phaseAt(level, pos) == rotated) {
				throw new AssertionError("Reset left the moon where the rotation had put it");
			}
		});
	}

	/**
	 * Leaves both moon rules set to something that is not their default, so that reopening the save
	 * can tell whether they came back.
	 */
	private static void bendTheMoonAndLeaveIt(ClientGameTestContext context,
			TestSingleplayerContext singleplayer, AtomicReference<MoonPhase> rotated,
			AtomicReference<MoonPhase> held) {
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos pos = onlyPlayer(server).blockPosition();
			MoonPhase target = shift(phaseAt(level, pos), 2);

			run(server, "overhaul moon " + target.getSerializedName());
			rotated.set(target);

			// Somewhere else entirely, so that neither rule can be confirmed by the other's value.
			MoonPhase pin = shift(target, MoonPhase.COUNT / 2);

			held.set(pin);
			run(server, "gamerule overhaul:fixed_moon_phase " + pin.getSerializedName());
		});

		context.waitTicks(25);
	}

	/**
	 * Both rules are game rules, so they ride in level.dat and should come back on their own. That
	 * is worth asserting rather than assuming: a mod's rule only round trips while the mod is the
	 * one reading the save, and nothing in the game would complain if a value quietly reverted to
	 * its default — the moon would simply be wrong, which is the failure this whole feature exists
	 * to avoid.
	 */
	private static void checkMoonSurvivedTheRestart(ClientGameTestContext context,
			TestSingleplayerContext reopened, AtomicReference<MoonPhase> rotated,
			AtomicReference<MoonPhase> held) {
		reopened.getServer().runOnServer(server -> {
			MoonPhase now = phaseAt(server.overworld(), onlyPlayer(server).blockPosition());

			if (now != held.get()) {
				throw new AssertionError("The pin should have survived the restart: expected "
						+ held.get().getSerializedName() + ", moon reads " + now.getSerializedName());
			}

			run(server, "gamerule overhaul:fixed_moon_phase none");
		});

		context.waitTicks(25);

		// With the pin out of the way the rotation underneath should still be there, which also
		// means the clock came back where it was — a rotation is only meaningful against it.
		reopened.getServer().runOnServer(server -> {
			MoonPhase now = phaseAt(server.overworld(), onlyPlayer(server).blockPosition());

			if (now != rotated.get()) {
				throw new AssertionError("The rotation should have survived the restart: expected "
						+ rotated.get().getSerializedName() + ", moon reads " + now.getSerializedName());
			}
		});
	}

	/**
	 * The command works out where inhabited time stops buying difficulty by asking vanilla, rather
	 * than holding a copy of vanilla's clamp. This checks it lands on the real knee.
	 *
	 * <p>The 3,600,000 below is deliberately hardcoded <em>here</em> and nowhere in the mod. It is
	 * what vanilla clamps to today, and asserting it in a test is the right place for that: if
	 * Mojang retunes it this fails loudly and the number gets updated, while the command carries on
	 * working either way. A copy of it in the command would instead go quietly wrong — refusing
	 * targets that had become reachable, and reporting a ceiling that was no longer true.
	 */
	private static void checkDifficultyCeilingIsDerivedNotAssumed(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos pos = onlyPlayer(server).blockPosition();
			ChunkAccess chunk = level.getChunk(pos);

			// Hard, so that inhabited time has the widest range to work across.
			run(server, "difficulty hard");

			// Far past any clamp, so this is the most the chunk can ever be worth.
			run(server, "overhaul difficulty inhabited 999999999");
			float most = level.getCurrentDifficultyAt(pos).getEffectiveDifficulty();

			run(server, "overhaul difficulty reset");

			if (chunk.getInhabitedTime() != 0L) {
				throw new AssertionError("reset should zero inhabited time, left " + chunk.getInhabitedTime());
			}

			// Asking for exactly the most it can be worth should be accepted, not refused as
			// over the ceiling, and should solve to the smallest time that gets there.
			run(server, "overhaul difficulty set " + Float.toString(most));

			long landed = chunk.getInhabitedTime();

			if (landed != 3_600_000L) {
				throw new AssertionError("The ceiling should solve to vanilla's clamp of 3600000 ticks, got "
						+ landed + " (if vanilla retuned the clamp, update this number)");
			}

			if (level.getCurrentDifficultyAt(pos).getEffectiveDifficulty() != most) {
				throw new AssertionError("Solving for the ceiling should reach it exactly");
			}

			// And one tick short of the knee must genuinely be worth less, or the search above
			// would have been landing on an arbitrary point in a flat region.
			chunk.setInhabitedTime(landed - 1L);

			if (level.getCurrentDifficultyAt(pos).getEffectiveDifficulty() >= most) {
				throw new AssertionError("One tick below the knee should be worth strictly less");
			}

			run(server, "overhaul difficulty reset");
		});
	}

	/**
	 * Rings the player with chests and checks a quick-stack only reaches the ones that were already
	 * holding the item — not the chest out of range, and not with the backpack, which stays put
	 * even though a chest in range holds one.
	 */
	private static void checkQuickStackFollowsWhatIsAlreadyThere(ClientGameTestContext context,
			TestSingleplayerContext singleplayer) {
		AtomicReference<BlockPos> origin = new AtomicReference<>();

		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();
			BlockPos base = player.blockPosition();
			origin.set(base);

			// The near chest holds a backpack as well, so the run has something the item filter has
			// to refuse rather than something it never had the chance to move.
			placeChest(level, base.offset(2, 0, 0),
					new ItemStack(item("minecraft:cobblestone")), new ItemStack(item("overhaul:backpack")));
			placeChest(level, base.offset(-2, 0, 0), new ItemStack(item("minecraft:dirt")));

			// Eight blocks out, well past the five block radius, and holding the one thing the
			// player is carrying that nothing nearby wants.
			placeChest(level, base.offset(8, 0, 0), new ItemStack(item("minecraft:gold_ingot")));

			Inventory inventory = player.getInventory();
			inventory.clearContent();
			inventory.setItem(9, new ItemStack(item("minecraft:cobblestone"), 32));
			inventory.setItem(10, new ItemStack(item("minecraft:dirt"), 32));
			inventory.setItem(11, new ItemStack(item("minecraft:gold_ingot"), 32));
			inventory.setItem(12, new ItemStack(item("overhaul:backpack")));
		});

		context.runOnClient(client -> ClientPlayNetworking.send(new QuickStackPayload(false)));
		singleplayer.getConnection().waitForServerboundPackets();
		context.waitTicks(2);

		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();
			BlockPos base = origin.get();

			Container near = chestAt(level, base.offset(2, 0, 0));
			Container other = chestAt(level, base.offset(-2, 0, 0));
			Container far = chestAt(level, base.offset(8, 0, 0));

			expect(near.getItem(0), "minecraft:cobblestone", 33, "the chest that already held cobblestone");
			expect(other.getItem(0), "minecraft:dirt", 33, "the chest that already held dirt");
			expect(far.getItem(0), "minecraft:gold_ingot", 1, "the chest out of range");

			if (near.getItem(1).getCount() != 1) {
				throw new AssertionError("A backpack should never be quick-stacked, but the chest ended up with "
						+ near.getItem(1).getCount());
			}

			Inventory inventory = player.getInventory();
			expect(inventory.getItem(11), "minecraft:gold_ingot", 32, "the player, with nothing nearby wanting gold");
			expect(inventory.getItem(12), "overhaul:backpack", 1, "the player, who keeps their backpack");

			if (!inventory.getItem(9).isEmpty() || !inventory.getItem(10).isEmpty()) {
				throw new AssertionError("Cobblestone and dirt should both have left the inventory");
			}
		});
	}

	/**
	 * Sorts the player's own inventory twice, once per setting of each toggle, and checks both
	 * halves of the arrangement: what the order is, and which way it runs across the grid. The two
	 * item sets are chosen so the two modes disagree — grouping by mod puts the modded cheese last,
	 * where sorting by name puts it second.
	 */
	private static void checkSortObeysBothToggles(ClientGameTestContext context,
			TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			Inventory inventory = onlyPlayer(server).getInventory();
			inventory.clearContent();
			inventory.setItem(20, new ItemStack(item("minecraft:diamond")));
			inventory.setItem(24, new ItemStack(item("overhaul:cheese")));
			inventory.setItem(31, new ItemStack(item("minecraft:apple")));

			// Split on purpose: a sort that does not merge partial stacks leaves the mess it was
			// meant to clear up.
			inventory.setItem(14, new ItemStack(item("minecraft:cobblestone"), 10));
			inventory.setItem(35, new ItemStack(item("minecraft:cobblestone"), 10));
		});

		context.runOnClient(client ->
				ClientPlayNetworking.send(new SortPayload(SortMode.BY_MOD, FillOrder.HORIZONTAL, true)));
		singleplayer.getConnection().waitForServerboundPackets();
		context.waitTicks(2);

		singleplayer.getServer().runOnServer(server -> {
			Inventory inventory = onlyPlayer(server).getInventory();

			// Vanilla first, alphabetically, then everything Overhaul added.
			expect(inventory.getItem(9), "minecraft:apple", 1, "the first slot of a mod-grouped sort");
			expect(inventory.getItem(10), "minecraft:cobblestone", 20, "the second slot, merged from two stacks");
			expect(inventory.getItem(11), "minecraft:diamond", 1, "the third slot");
			expect(inventory.getItem(12), "overhaul:cheese", 1, "the fourth slot, where the modded item lands");
		});

		context.runOnClient(client ->
				ClientPlayNetworking.send(new SortPayload(SortMode.ALPHABETICAL, FillOrder.VERTICAL, true)));
		singleplayer.getConnection().waitForServerboundPackets();
		context.waitTicks(2);

		singleplayer.getServer().runOnServer(server -> {
			Inventory inventory = onlyPlayer(server).getInventory();

			// Three rows of nine filled column by column: the first three items run down the left
			// edge, and the fourth starts the next column.
			expect(inventory.getItem(9), "minecraft:apple", 1, "the top of the first column");
			expect(inventory.getItem(18), "overhaul:cheese", 1, "the middle of the first column");
			expect(inventory.getItem(27), "minecraft:cobblestone", 20, "the bottom of the first column");
			expect(inventory.getItem(10), "minecraft:diamond", 1, "the top of the second column");
		});

		singleplayer.getServer().runOnServer(server -> onlyPlayer(server).getInventory().clearContent());
	}

	/**
	 * Locks one slot and checks both bulk operations step over it. The sort assertion is the more
	 * telling of the two: the locked slot is the first of the run, so a sort that merely skipped it
	 * when collecting would still overwrite it when writing back.
	 */
	private static void checkLockedSlotsAreLeftAlone(ClientGameTestContext context,
			TestSingleplayerContext singleplayer) {
		AtomicReference<BlockPos> origin = new AtomicReference<>();

		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			BlockPos base = player.blockPosition();
			origin.set(base);

			placeChest(player.level(), base.offset(3, 0, 0),
					new ItemStack(item("minecraft:cobblestone")), new ItemStack(item("minecraft:dirt")));

			Inventory inventory = player.getInventory();
			inventory.clearContent();
			inventory.setItem(9, new ItemStack(item("minecraft:cobblestone"), 32));
			inventory.setItem(10, new ItemStack(item("minecraft:dirt"), 32));
		});

		context.runOnClient(client -> ClientPlayNetworking.send(new ToggleSlotLockPayload(9)));
		singleplayer.getConnection().waitForServerboundPackets();
		context.waitTicks(2);

		singleplayer.getServer().runOnServer(server -> {
			if (!SlotLocks.isLocked(onlyPlayer(server), 9)) {
				throw new AssertionError("Slot 9 should be locked after the toggle");
			}
		});

		// The quick-stack cooldown is ten ticks and the sort test just before this one does not
		// stack, but the check before that does; wait past it rather than racing.
		context.waitTicks(15);
		context.runOnClient(client -> ClientPlayNetworking.send(new QuickStackPayload(false)));
		singleplayer.getConnection().waitForServerboundPackets();
		context.waitTicks(2);

		singleplayer.getServer().runOnServer(server -> {
			Inventory inventory = onlyPlayer(server).getInventory();
			expect(inventory.getItem(9), "minecraft:cobblestone", 32, "the locked slot after a quick-stack");

			if (!inventory.getItem(10).isEmpty()) {
				throw new AssertionError("The unlocked dirt should still have left the inventory");
			}
		});

		singleplayer.getServer().runOnServer(server ->
				onlyPlayer(server).getInventory().setItem(11, new ItemStack(item("minecraft:diamond"))));

		context.runOnClient(client ->
				ClientPlayNetworking.send(new SortPayload(SortMode.ALPHABETICAL, FillOrder.HORIZONTAL, true)));
		singleplayer.getConnection().waitForServerboundPackets();
		context.waitTicks(2);

		singleplayer.getServer().runOnServer(server -> {
			Inventory inventory = onlyPlayer(server).getInventory();

			// Slot 9 is the first slot of the sorted run, so an unlocked sort would have put the
			// diamond there. It goes to the next slot instead and the cobblestone stays put.
			expect(inventory.getItem(9), "minecraft:cobblestone", 32, "the locked slot after a sort");
			expect(inventory.getItem(10), "minecraft:diamond", 1, "the first slot the sort was allowed to use");
		});

		lockScreenshot(context, singleplayer);

		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			SlotLocks.toggle(player, 9);

			if (SlotLocks.isLocked(player, 9)) {
				throw new AssertionError("Toggling a locked slot should unlock it");
			}

			player.getInventory().clearContent();
		});
	}

	/** Opens the inventory with a slot locked so the mark it draws can be looked at. */
	private static void lockScreenshot(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getConnection().waitForClientboundPackets();

		context.setScreen(() -> {
			LocalPlayer player = Minecraft.getInstance().player;

			if (player == null) {
				throw new AssertionError("No client player to open an inventory for");
			}

			return new InventoryScreen(player);
		});

		context.waitTicks(5);
		context.takeScreenshot("overhaul-locked-slot");
		context.setScreen(() -> null);
		context.waitTicks(2);
	}

	/**
	 * Destroys a stack off the cursor and takes it back again. The undo is the reason the button is
	 * safe to have at all, so it is worth an assertion rather than a comment.
	 */
	private static void checkTrashVoidsAndGivesBack(ClientGameTestContext context,
			TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			player.getInventory().clearContent();
			player.containerMenu.setCarried(new ItemStack(item("minecraft:diamond_pickaxe")));
		});

		context.runOnClient(client -> ClientPlayNetworking.send(TrashPayload.INSTANCE));
		singleplayer.getConnection().waitForServerboundPackets();
		context.waitTicks(2);

		singleplayer.getServer().runOnServer(server -> {
			if (!onlyPlayer(server).containerMenu.getCarried().isEmpty()) {
				throw new AssertionError("The trash should have taken the held pickaxe");
			}
		});

		context.runOnClient(client -> ClientPlayNetworking.send(TrashPayload.INSTANCE));
		singleplayer.getConnection().waitForServerboundPackets();
		context.waitTicks(2);

		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			expect(player.containerMenu.getCarried(), "minecraft:diamond_pickaxe", 1, "the cursor after an undo");
			player.containerMenu.setCarried(ItemStack.EMPTY);
		});
	}

	/**
	 * Opens a shulker box sitting in the inventory and checks it is the real thing: the right
	 * number of slots, holding what the item held, and writing back to the item afterwards.
	 */
	private static void checkShulkerBoxOpensWhereItSits(ClientGameTestContext context,
			TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			player.getInventory().clearContent();

			ItemStack box = new ItemStack(item("minecraft:shulker_box"));
			box.set(DataComponents.CONTAINER,
					ItemContainerContents.fromItems(List.of(new ItemStack(item("minecraft:emerald"), 5))));
			player.getInventory().setItem(9, box);
		});

		context.runOnClient(client -> ClientPlayNetworking.send(new OpenCarriedPayload(9)));
		singleplayer.getConnection().waitForServerboundPackets();
		singleplayer.getConnection().waitForClientboundPackets();
		context.waitForScreen(AbstractContainerScreen.class);

		context.runOnClient(client -> {
			if (client.player == null) {
				throw new AssertionError("No client player while the shulker box screen was open");
			}

			int slots = client.player.containerMenu.slots.size();

			if (slots != 27 + 36) {
				throw new AssertionError("A shulker box should show 27 slots, menu had " + (slots - 36));
			}
		});

		context.takeScreenshot("overhaul-shulker-open");

		// Put something into an empty slot through the open menu, then close it and read the item
		// back: this is what proves the screen writes to the stack rather than to a copy of it.
		singleplayer.getServer().runOnServer(server ->
				onlyPlayer(server).containerMenu.getSlot(1).set(new ItemStack(item("minecraft:diamond"), 2)));

		context.setScreen(() -> null);
		context.waitTicks(5);

		singleplayer.getServer().runOnServer(server -> {
			ItemStack box = onlyPlayer(server).getInventory().getItem(9);
			expect(box, "minecraft:shulker_box", 1, "the slot the shulker box was opened from");

			List<ItemStack> contents = box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
					.nonEmptyItemCopyStream().toList();

			if (contents.size() != 2) {
				throw new AssertionError("The shulker box should have kept both stacks, had " + contents.size());
			}

			onlyPlayer(server).getInventory().clearContent();
		});
	}

	private static void placeChest(ServerLevel level, BlockPos pos, ItemStack... contents) {
		level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
		Container chest = chestAt(level, pos);

		for (int slot = 0; slot < contents.length; slot++) {
			chest.setItem(slot, contents[slot]);
		}

		chest.setChanged();
	}

	private static Container chestAt(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof Container chest)) {
			throw new AssertionError("No chest at " + pos);
		}

		return chest;
	}

	private static void expect(ItemStack stack, String id, int count, String where) {
		if (stack.getItem() != item(id) || stack.getCount() != count) {
			throw new AssertionError("Expected " + count + " " + id + " in " + where
					+ ", found " + stack.getCount() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
		}
	}

	private static MoonPhase phaseAt(ServerLevel level, BlockPos pos) {
		return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos);
	}

	/** The phase {@code steps} along the cycle from this one. */
	private static MoonPhase shift(MoonPhase phase, int steps) {
		int index = Math.floorMod(phase.index() + steps, MoonPhase.COUNT);

		for (MoonPhase candidate : MoonPhase.values()) {
			if (candidate.index() == index) {
				return candidate;
			}
		}

		throw new AssertionError("No moon phase at index " + index);
	}

	private static void run(net.minecraft.server.MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}

	/** Looks an item up by id, failing the test with a useful message if it was never registered. */
	private static Item item(String id) {
		return BuiltInRegistries.ITEM.get(Identifier.parse(id))
				.orElseThrow(() -> new AssertionError("Item was not registered: " + id))
				.value();
	}

	private static ServerPlayer onlyPlayer(net.minecraft.server.MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();

		if (players.isEmpty()) {
			throw new AssertionError("No player on the test server");
		}

		return players.getFirst();
	}
}
