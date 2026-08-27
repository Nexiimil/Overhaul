package com.overhaul.gametest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.overhaul.module.backpack.BackpackItem;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.MoonPhase;

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
			checkMoonBendsWithoutMovingTheClock(context, singleplayer);
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
	 * that. This also pins down the property the whole
	 * design exists for: bending the moon must not move the world clock, because world time feeds
	 * local difficulty and moving it would quietly retune the hordes these commands are for.
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
