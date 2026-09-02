package com.overhaul.gametest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.overhaul.module.backpack.BackpackItem;
import com.overhaul.module.magical.EnchantingLapis;
import com.overhaul.module.inventory.FillOrder;
import com.overhaul.module.inventory.SlotLocks;
import com.overhaul.module.inventory.ToggleSlotLockPayload;
import com.overhaul.module.inventory.TrashSlot;
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
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.ChunkPos;
import com.overhaul.module.mob.MobModule;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import com.overhaul.module.multiplayer.TeamInvites;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.level.chunk.ChunkAccess;
import com.overhaul.module.multiplayer.Claims;
import com.overhaul.module.multiplayer.MultiplayerContent;
import com.overhaul.module.multiplayer.Protection;
import com.overhaul.module.multiplayer.TeamClaims;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.overhaul.module.magical.Experience;
import com.overhaul.module.mob.MobModule;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.VillagerTradeTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

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
			checkEnchantingTableKeepsItsLapis(singleplayer);
			checkNewEnchantmentsLoaded(singleplayer);
			checkVeinMineTakesTheSeamAndStops(singleplayer);
			checkAnvilBottlesExperience(singleplayer);
			checkThrownBottlesGiveBackWhatTheyCost(context, singleplayer);
			checkAnvilSplitsAnEnchantedBook(singleplayer);
			checkGlisteringMelonIsWorthEating(singleplayer);
			checkGoldenBakedPotatoCrafts(singleplayer);
			checkDispenserFeedsWhatIsInFrontOfIt(singleplayer);
			checkVillagerWalksTowardsAnEmerald(singleplayer);
			checkAddedTradesJoinTheVanillaPools(singleplayer);
			checkClaimsSortVisitorsFromMembers(singleplayer);
			checkBannerClaimsTheChunk(singleplayer);
			checkExplosionsSpareClaimedChunks(singleplayer);
			checkPistonsStayInTheirOwnClaim(singleplayer);
			checkFireDoesNotTakeClaimedBlocks(singleplayer);
			checkEndermenLeaveTheDetailAlone(singleplayer);
			checkChunkLoaderHoldsItsChunk(singleplayer);
			checkChunkLoaderCrafts(singleplayer);
			checkDeletingATeamReleasesItsLand(singleplayer);
			checkVanillaTeamCommandIsStillOperatorOnly(singleplayer);
			checkPlayersCanRunTheirOwnTeams(singleplayer);
			checkJoiningNeedsAnInvitation(singleplayer);
			checkMoonBendsWithoutMovingTheClock(context, singleplayer);
			checkDifficultyCeilingIsDerivedNotAssumed(singleplayer);
			bendTheMoonAndLeaveIt(context, singleplayer, rotated, held);
			binSomethingAndLeaveIt(context, singleplayer);
			claimSomethingAndLeaveIt(singleplayer);

			context.takeScreenshot("overhaul-world");
			save = singleplayer.getWorldSave();
		}

		// Reopening the save starts a new server against the same level.dat, which is a restart in
		// every way that matters to state meant to outlive one.
		try (TestSingleplayerContext reopened = save.open()) {
			reopened.getConnection().waitForChunksRender();
			checkMoonSurvivedTheRestart(context, reopened, rotated, held);
			checkBinSurvivedTheRestart(reopened);
			checkClaimSurvivedTheRestart(reopened);
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

	/** Opens the inventory so the bin, and whatever it is holding, can be looked at. */
	private static void trashScreenshot(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getConnection().waitForClientboundPackets();
		openOwnInventory(context);
		context.takeScreenshot("overhaul-trash-slot");
		context.setScreen(() -> null);
		context.waitTicks(2);
	}

	private static void openOwnInventory(ClientGameTestContext context) {
		context.setScreen(() -> {
			LocalPlayer player = Minecraft.getInstance().player;

			if (player == null) {
				throw new AssertionError("No client player to open an inventory for");
			}

			return new InventoryScreen(player);
		});

		context.waitTicks(5);
	}

	/** Opens the inventory with a slot locked so the mark it draws can be looked at. */
	private static void lockScreenshot(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getConnection().waitForClientboundPackets();
		openOwnInventory(context);
		context.takeScreenshot("overhaul-locked-slot");
		context.setScreen(() -> null);
		context.waitTicks(2);
	}

	/**
	 * Puts a stack in the bin and takes it back again. Holding the last item is the reason the bin
	 * is safe to click at all, so it is worth an assertion rather than a comment — and the item
	 * being visible in the slot is the whole point of it being a slot.
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
			ServerPlayer player = onlyPlayer(server);

			if (!player.containerMenu.getCarried().isEmpty()) {
				throw new AssertionError("The bin should have taken the held pickaxe");
			}

			expect(TrashSlot.contents(player), "minecraft:diamond_pickaxe", 1, "the bin itself");
		});

		trashScreenshot(context, singleplayer);

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
	 * Right-clicks a shulker box held in hand and checks what opens is the real thing: the right
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
			player.setItemInHand(InteractionHand.MAIN_HAND, box);

			// The real use interaction rather than a call straight into the module, so what is
			// under test includes the hook actually firing for a shulker box.
			ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
			InteractionResult result = player.gameMode.useItem(player, player.level(), held, InteractionHand.MAIN_HAND);

			if (result == InteractionResult.PASS) {
				throw new AssertionError("Using a shulker box in hand should have been handled");
			}
		});

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
			ItemStack box = onlyPlayer(server).getItemInHand(InteractionHand.MAIN_HAND);
			expect(box, "minecraft:shulker_box", 1, "the hand the shulker box was opened from");

			List<ItemStack> contents = box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
					.nonEmptyItemCopyStream().toList();

			if (contents.size() != 2) {
				throw new AssertionError("The shulker box should have kept both stacks, had " + contents.size());
			}

			ServerPlayer player = onlyPlayer(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.getInventory().clearContent();
		});
	}

	/** Leaves something in the bin for the reopened save to find. */
	private static void binSomethingAndLeaveIt(ClientGameTestContext context,
			TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server ->
				onlyPlayer(server).containerMenu.setCarried(new ItemStack(item("minecraft:golden_apple"), 3)));

		context.runOnClient(client -> ClientPlayNetworking.send(TrashPayload.INSTANCE));
		singleplayer.getConnection().waitForServerboundPackets();
		context.waitTicks(2);
	}

	/**
	 * The bin is meant to be the thing that saves you from a misclick, and a crash is exactly when
	 * you find out you wanted something back — so its contents have to outlive the session that
	 * binned them, or the recovery is worth nothing when it matters most.
	 */
	private static void checkBinSurvivedTheRestart(TestSingleplayerContext reopened) {
		reopened.getServer().runOnServer(server -> expect(TrashSlot.contents(onlyPlayer(server)),
				"minecraft:golden_apple", 3, "the bin after the world was reopened"));
	}

	/**
	 * Puts lapis in an enchanting table, closes it, and checks the table kept it rather than the
	 * player getting it back — then that reopening hands it over again, and that breaking the table
	 * gives it up rather than swallowing it.
	 *
	 * <p>Driven through the real menu, because what is under test is a mixin: one that failed to
	 * apply would leave vanilla behaving exactly as it always did, which is indistinguishable from
	 * the feature being off unless something actually opens the screen.
	 */
	private static void checkEnchantingTableKeepsItsLapis(TestSingleplayerContext singleplayer) {
		AtomicReference<BlockPos> table = new AtomicReference<>();

		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();
			BlockPos pos = player.blockPosition().offset(0, 0, 3);
			table.set(pos);

			level.setBlockAndUpdate(pos, Blocks.ENCHANTING_TABLE.defaultBlockState());
			player.getInventory().clearContent();

			openTable(player, pos);
			player.containerMenu.getSlot(1).set(new ItemStack(item("minecraft:lapis_lazuli"), 3));
			player.closeContainer();

			expect(EnchantingLapis.take(level, pos), "minecraft:lapis_lazuli", 3, "the table after closing it");

			// take() emptied the table, so put it back the way closing the screen would have.
			EnchantingLapis.store(level, pos, new ItemStack(item("minecraft:lapis_lazuli"), 3));

			if (player.getInventory().contains(new ItemStack(item("minecraft:lapis_lazuli")))) {
				throw new AssertionError("The lapis should have stayed in the table, not come back to the player");
			}
		});

		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			openTable(player, table.get());

			expect(player.containerMenu.getSlot(1).getItem(), "minecraft:lapis_lazuli", 3,
					"the lapis slot when the table is reopened");

			// Held by the menu now, so the table itself must be empty: a table that still had it
			// would hand a second copy to the next player to open it.
			if (!EnchantingLapis.take(player.level(), table.get()).isEmpty()) {
				throw new AssertionError("An open table should not still be holding the lapis it handed over");
			}

			player.closeContainer();
		});

		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();
			BlockPos pos = table.get();

			player.gameMode.destroyBlock(pos);

			List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0)).stream()
					.filter(entity -> entity.getItem().getItem() == item("minecraft:lapis_lazuli"))
					.toList();

			if (dropped.size() != 1 || dropped.getFirst().getItem().getCount() != 3) {
				throw new AssertionError("Breaking the table should drop the 3 lapis it was holding, dropped "
						+ dropped.size() + " stack(s)");
			}

			dropped.forEach(ItemEntity::discard);
			player.getInventory().clearContent();
		});
	}

	private static void openTable(ServerPlayer player, BlockPos pos) {
		player.openMenu(player.level().getBlockState(pos).getMenuProvider(player.level(), pos));

		if (!(player.containerMenu instanceof net.minecraft.world.inventory.EnchantmentMenu)) {
			throw new AssertionError("Expected an enchanting menu, got " + player.containerMenu.getClass());
		}
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

	// New in this pass: enchantments, anvil jobs, food and the two mob-module additions ----------

	/**
	 * The two enchantments are pure data pack files, which means they can fail to arrive without
	 * anything in the mod noticing — the code that reads them just never finds a level above zero.
	 * Looking them up in the live registry is the only way to know the generated pack actually
	 * landed, and the tag and supported-item checks are what say they are reachable in play rather
	 * than merely present.
	 */
	private static void checkNewEnchantmentsLoaded(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			TagKey<Enchantment> inTable = TagKey.create(Registries.ENCHANTMENT,
					Identifier.withDefaultNamespace("in_enchanting_table"));

			Holder<Enchantment> shrouded = enchantment(server, "overhaul:shrouded");
			Holder<Enchantment> veinMine = enchantment(server, "overhaul:vein_mine");

			if (!shrouded.is(inTable) || !veinMine.is(inTable)) {
				throw new AssertionError("Both enchantments should be offered by an enchanting table");
			}

			if (!shrouded.value().isSupportedItem(new ItemStack(Items.DIAMOND_HELMET))) {
				throw new AssertionError("Shrouded should go on a helmet");
			}

			if (!veinMine.value().isSupportedItem(new ItemStack(Items.DIAMOND_PICKAXE))) {
				throw new AssertionError("Vein Mine should go on a pickaxe");
			}
		});
	}

	/**
	 * Vein Mine, from both ends: a deposit smaller than the limit goes entirely, and one larger
	 * than it stops at the limit rather than running away down a seam. The second half is the one
	 * worth having a test for — an off-by-one there is the difference between thirty-two blocks
	 * and the whole chunk.
	 */
	private static void checkVeinMineTakesTheSeamAndStops(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();
			player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);

			ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
			pickaxe.enchant(enchantment(server, "overhaul:vein_mine"), 1);
			player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);

			// Well above the ground, so nothing the world generated can join the vein and make the
			// counts below mean something other than what was placed.
			BlockPos centre = player.blockPosition().offset(6, 12, 6);
			fill(level, centre.offset(-3, -3, -3), centre.offset(3, 3, 3), Blocks.AIR);
			fill(level, centre.offset(-1, -1, -1), centre.offset(1, 1, 1), Blocks.COAL_ORE);

			player.gameMode.destroyBlock(centre);
			int leftInCube = count(level, centre.offset(-1, -1, -1), centre.offset(1, 1, 1), Blocks.COAL_ORE);

			if (leftInCube != 0) {
				throw new AssertionError("A 27 block deposit is inside the limit and should go entirely, "
						+ leftInCube + " left");
			}

			BlockPos start = centre.offset(0, 8, 0);
			BlockPos end = start.offset(39, 0, 0);
			fill(level, start.offset(-1, -1, -1), end.offset(1, 1, 1), Blocks.AIR);
			fill(level, start, end, Blocks.COAL_ORE);

			player.gameMode.destroyBlock(start);
			int leftInSeam = count(level, start, end, Blocks.COAL_ORE);

			// Thirty-two counts the block the player actually broke, so a forty block seam keeps
			// eight of them.
			if (leftInSeam != 8) {
				throw new AssertionError("A 40 block seam should stop at the 32 block limit, leaving 8; "
						+ leftInSeam + " left");
			}

			fill(level, start, end, Blocks.AIR);
		});
	}

	/**
	 * Bottling experience at an anvil, priced in points rather than levels.
	 *
	 * <p>The number that matters is the exact experience taken, because the whole feature is a
	 * promise that a bottle costs what it gives back plus the surcharge. Charging in levels, which
	 * is what the anvil would do left to itself, would quietly break that at every level boundary.
	 */
	private static void checkAnvilBottlesExperience(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);

			AnvilMenu menu = anvilFor(player);
			menu.getSlot(AnvilMenu.INPUT_SLOT).set(PotionContents.createItemStack(Items.POTION, Potions.WATER));
			menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.LAPIS_LAZULI, 4));

			ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();

			if (result.getItem() != Items.EXPERIENCE_BOTTLE || result.getCount() != 1) {
				throw new AssertionError("A water bottle and lapis should make one experience bottle, got " + result);
			}

			player.giveExperienceLevels(30);
			int before = Experience.total(player);

			take(menu, player);

			int spent = before - Experience.total(player);

			// Ten experience a bottle, plus the ten percent surcharge, rounded up.
			if (spent != 11) {
				throw new AssertionError("A bottle should cost 11 experience, cost " + spent);
			}

			if (!menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().isEmpty()) {
				throw new AssertionError("The water bottle should have been used up");
			}

			expect(menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem(), "minecraft:lapis_lazuli", 3, "the anvil");
		});
	}

	/**
	 * A thrown bottle gives back exactly what the anvil charged for it, less the surcharge.
	 *
	 * <p>Vanilla rolls 3 to 11 per bottle, which is fine while bottles only come from witches and
	 * makes a quoted price meaningless the moment you can buy one. This is the other half of that
	 * feature and the half a player would notice going wrong, since the anvil would keep charging
	 * eleven for something worth an average of seven.
	 */
	private static void checkThrownBottlesGiveBackWhatTheyCost(ClientGameTestContext context,
			TestSingleplayerContext singleplayer) {
		AtomicReference<BlockPos> chamber = new AtomicReference<>();

		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();

			// Vein mining a coal seam a few checks ago left experience lying around, and orbs are
			// exactly what this counts, so the slate has to be clean before anything is thrown.
			level.getEntitiesOfClass(ExperienceOrb.class, new AABB(player.blockPosition()).inflate(64.0))
					.forEach(ExperienceOrb::discard);

			// Sealed and well away from the player, so nothing wanders in and no orb is collected
			// before it can be counted.
			BlockPos floor = player.blockPosition().offset(-20, 8, -20);
			chamber.set(floor);
			fill(level, floor.offset(-2, 0, -2), floor.offset(2, 6, 2), Blocks.AIR);
			fill(level, floor.offset(-2, 0, -2), floor.offset(2, 0, 2), Blocks.STONE);

			ThrownExperienceBottle bottle = new ThrownExperienceBottle(level, player,
					new ItemStack(Items.EXPERIENCE_BOTTLE));
			bottle.snapTo(floor.getX() + 0.5, floor.getY() + 4.0, floor.getZ() + 0.5, 0.0F, 90.0F);
			bottle.setDeltaMovement(0.0, -0.4, 0.0);
			level.addFreshEntity(bottle);
		});

		context.waitTicks(40);

		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = onlyPlayer(server).level();
			int total = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(chamber.get()).inflate(6.0))
					.stream()
					.mapToInt(ExperienceOrb::getValue)
					.sum();

			if (total != 10) {
				throw new AssertionError("A bottle should be worth exactly 10 experience, gave " + total);
			}

			level.getEntitiesOfClass(ExperienceOrb.class, new AABB(chamber.get()).inflate(6.0))
					.forEach(ExperienceOrb::discard);
		});
	}

	/**
	 * Splitting the first enchantment off a book and onto a blank one.
	 *
	 * <p>"First" has to mean the one the player sees at the top of the tooltip, so this puts
	 * Sharpness and Unbreaking on one book and expects Sharpness — which is the earlier of the two
	 * in the game's own tooltip order — to be the one that comes off. The source book keeping the
	 * rest, rather than being consumed the way the anvil consumes everything else, is the other
	 * half of what makes the feature worth using.
	 */
	private static void checkAnvilSplitsAnEnchantedBook(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);

			Holder<Enchantment> sharpness = enchantment(server, "minecraft:sharpness");
			Holder<Enchantment> unbreaking = enchantment(server, "minecraft:unbreaking");

			ItemEnchantments.Mutable both = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			both.set(sharpness, 3);
			both.set(unbreaking, 2);

			ItemStack source = new ItemStack(Items.ENCHANTED_BOOK);
			source.set(DataComponents.STORED_ENCHANTMENTS, both.toImmutable());

			AnvilMenu menu = anvilFor(player);
			menu.getSlot(AnvilMenu.INPUT_SLOT).set(source);
			menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.BOOK));

			ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
			ItemEnchantments split = result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);

			if (result.getItem() != Items.ENCHANTED_BOOK || split.size() != 1 || split.getLevel(sharpness) != 3) {
				throw new AssertionError("Splitting should give a book carrying only Sharpness III, got " + result);
			}

			player.giveExperienceLevels(10);
			int levelsBefore = player.experienceLevel;

			take(menu, player);

			if (levelsBefore - player.experienceLevel != 3) {
				throw new AssertionError("A split should cost three levels, cost "
						+ (levelsBefore - player.experienceLevel));
			}

			ItemEnchantments left = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem()
					.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);

			if (left.size() != 1 || left.getLevel(unbreaking) != 2) {
				throw new AssertionError("The source book should be left holding Unbreaking II, held " + left);
			}

			if (!menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty()) {
				throw new AssertionError("The blank book should have been used up");
			}
		});
	}

	/**
	 * A glistering melon slice is made of a melon slice and eight gold nuggets and has never been
	 * edible, which is the whole reason this exists. Checking the component on a fresh stack is
	 * also the only way to know the default-component rewrite ran at all.
	 */
	private static void checkGlisteringMelonIsWorthEating(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			FoodProperties food = new ItemStack(Items.GLISTERING_MELON_SLICE).get(DataComponents.FOOD);

			if (food == null) {
				throw new AssertionError("A glistering melon slice should be edible");
			}

			if (food.nutrition() != 4 || food.saturation() < 9.0F) {
				throw new AssertionError("Glistering melon should be four hunger and richly saturating, was "
						+ food.nutrition() + " hunger and " + food.saturation() + " saturation");
			}
		});
	}

	/** The golden baked potato exists and is reachable through the recipe manager, not just registered. */
	private static void checkGoldenBakedPotatoCrafts(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ItemStack nugget = new ItemStack(Items.GOLD_NUGGET);
			ItemStack potato = new ItemStack(Items.BAKED_POTATO);

			CraftingInput input = CraftingInput.of(3, 3, List.of(
					nugget, nugget, nugget,
					nugget, potato, nugget,
					nugget, nugget, nugget));

			ItemStack result = server.getRecipeManager()
					.getRecipeFor(RecipeType.CRAFTING, input, server.overworld())
					.orElseThrow(() -> new AssertionError("No recipe matched a baked potato ringed with nuggets"))
					.value()
					.assemble(input);

			if (result.getItem() != item("overhaul:golden_baked_potato")) {
				throw new AssertionError("Expected a golden baked potato, got " + result);
			}
		});
	}

	/**
	 * A dispenser pointed at a cow feeds it rather than throwing wheat on the floor.
	 *
	 * <p>Driven through the module's own entry point instead of a redstone pulse, because the
	 * mixin that calls it is already guaranteed to have applied — the game refuses to start if it
	 * has not — and a test that waits on a scheduled block tick is a test that fails on a slow
	 * machine for no reason.
	 */
	private static void checkDispenserFeedsWhatIsInFrontOfIt(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();

			BlockPos pos = player.blockPosition().offset(-6, 0, 6);
			fill(level, pos.offset(-1, -1, -1), pos.offset(3, 2, 1), Blocks.AIR);
			fill(level, pos.offset(-1, -1, -1), pos.offset(3, -1, 1), Blocks.STONE);

			level.setBlockAndUpdate(pos, Blocks.DISPENSER.defaultBlockState()
					.setValue(DispenserBlock.FACING, Direction.EAST));

			if (!(level.getBlockEntity(pos) instanceof DispenserBlockEntity dispenser)) {
				throw new AssertionError("No dispenser where one was just placed");
			}

			dispenser.setItem(0, new ItemStack(Items.WHEAT, 4));

			Cow cow = EntityTypes.COW.spawn(level, pos.east(), EntitySpawnReason.COMMAND);

			if (cow == null) {
				throw new AssertionError("Could not spawn a cow in front of the dispenser");
			}

			try {
				if (!MobModule.dispenserFed(level, level.getBlockState(pos), pos)) {
					throw new AssertionError("The dispenser should have fed the cow in front of it");
				}

				if (!cow.isInLove()) {
					throw new AssertionError("The cow was fed but is not in love");
				}

				expect(dispenser.getItem(0), "minecraft:wheat", 3, "the dispenser");

				// A cow already in love is not hungry, so the second pulse has nothing to do and the
				// dispenser goes back to throwing what it holds.
				if (MobModule.dispenserFed(level, level.getBlockState(pos), pos)) {
					throw new AssertionError("A cow already in love should not be fed again");
				}
			} finally {
				cow.discard();
				level.removeBlock(pos, false);
			}
		});
	}

	/**
	 * A villager walks towards a held emerald, and stops caring the moment it is put away.
	 *
	 * <p>Asserted on the brain's walk target rather than on the villager having moved, because
	 * "did it get closer" is a question about pathfinding and frame timing, while "was it told to
	 * come here" is the thing this feature actually decides.
	 */
	private static void checkVillagerWalksTowardsAnEmerald(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();

			BlockPos pos = player.blockPosition().offset(6, 0, 0);
			fill(level, pos.below(), pos.below(), Blocks.STONE);
			fill(level, pos, pos.above(), Blocks.AIR);

			Villager villager = EntityTypes.VILLAGER.spawn(level, pos, EntitySpawnReason.COMMAND);

			if (villager == null) {
				throw new AssertionError("Could not spawn a villager");
			}

			try {
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD));
				MobModule.leadVillager(villager, level);

				WalkTarget target = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
						.orElseThrow(() -> new AssertionError("A villager shown an emerald should be walking to it"));

				if (!(target.getTarget() instanceof EntityTracker tracker) || tracker.getEntity() != player) {
					throw new AssertionError("The villager is walking somewhere that is not the player");
				}

				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				MobModule.leadVillager(villager, level);

				if (villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
					throw new AssertionError("A villager should stop following once the emerald is put away");
				}
			} finally {
				villager.discard();
			}
		});
	}

	/**
	 * The added trades have to be both loaded and in the right pool: a trade file the game parsed
	 * but no profession's tag mentions is a trade no villager will ever offer.
	 */
	private static void checkAddedTradesJoinTheVanillaPools(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			Holder<VillagerTrade> trade = server.registryAccess()
					.lookupOrThrow(Registries.VILLAGER_TRADE)
					.get(ResourceKey.create(Registries.VILLAGER_TRADE, Identifier.parse("overhaul:farmer/1/tomato_emerald")))
					.map(holder -> (Holder<VillagerTrade>) holder)
					.orElseThrow(() -> new AssertionError("The added farmer trade was not loaded"));

			if (!trade.is(VillagerTradeTags.FARMER_LEVEL_1)) {
				throw new AssertionError("The added farmer trade is not in the level one farmer pool");
			}
		});
	}

	// Helpers for the checks above -----------------------------------------------------------

	/** An anvil menu over a real anvil, so taking a result behaves the way it does in play. */
	private static AnvilMenu anvilFor(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition().offset(-6, 0, -6);
		fill(level, pos, pos.above(), Blocks.AIR);
		level.setBlockAndUpdate(pos, Blocks.ANVIL.defaultBlockState());
		return new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(level, pos));
	}

	/** Takes whatever is in the result slot, the way clicking it would. */
	private static void take(AnvilMenu menu, ServerPlayer player) {
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();

		if (result.isEmpty()) {
			throw new AssertionError("Nothing in the anvil's result slot to take");
		}

		if (!menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player)) {
			throw new AssertionError("The anvil would not let the result be taken");
		}

		menu.getSlot(AnvilMenu.RESULT_SLOT).set(ItemStack.EMPTY);
		menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, result);
	}

	private static Holder<Enchantment> enchantment(net.minecraft.server.MinecraftServer server, String id) {
		return server.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id)))
				.map(holder -> (Holder<Enchantment>) holder)
				.orElseThrow(() -> new AssertionError("Enchantment was not loaded: " + id));
	}

	private static void fill(ServerLevel level, BlockPos from, BlockPos to, Block block) {
		BlockPos.betweenClosed(from, to).forEach(pos -> level.setBlock(pos, block.defaultBlockState(), 2));
	}

	private static int count(ServerLevel level, BlockPos from, BlockPos to, Block block) {
		int found = 0;

		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			if (level.getBlockState(pos).is(block)) {
				found++;
			}
		}

		return found;
	}

	// Multiplayer: claims, banners, explosions and chunk loaders --------------------------------

	/**
	 * The whole access ladder in one go: stranger, ally, member.
	 *
	 * <p>Run against one player by moving the claim rather than the player — the chunk is given to
	 * a team the player is not on, then to one they are, then their own team is made an ally of the
	 * owner. That covers all three rungs without a second client, and it exercises the one method
	 * every rule in the module funnels through.
	 */
	private static void checkClaimsSortVisitorsFromMembers(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();
			BlockPos pos = player.blockPosition().offset(0, 20, 0);
			ChunkPos chunk = ChunkPos.containing(pos);

			run(server, "team add landlords");
			run(server, "team add tenants");
			run(server, "team join tenants " + player.getScoreboardName());

			Claims.claim(level, chunk, "landlords");

			try {
				if (Protection.mayBuild(player, level, pos)) {
					throw new AssertionError("A stranger should not be able to build in a claim");
				}

				if (Protection.mayInteract(player, level, pos, Blocks.OAK_DOOR.defaultBlockState())) {
					throw new AssertionError("A stranger should not be able to open a door in a claim");
				}

				// An ally may build, and may use anything the owner has not named as an exception.
				TeamClaims.update(server, "landlords", settings -> settings.withAlly("tenants", true));

				if (!Protection.mayBuild(player, level, pos)) {
					throw new AssertionError("An ally should be able to build");
				}

				TeamClaims.update(server, "landlords", settings -> settings.withAllies(
						settings.allies().withException("#minecraft:doors", true)));

				if (Protection.mayInteract(player, level, pos, Blocks.OAK_DOOR.defaultBlockState())) {
					throw new AssertionError("An ally should be stopped by an exception the owner named");
				}

				if (!Protection.mayInteract(player, level, pos, Blocks.CRAFTING_TABLE.defaultBlockState())) {
					throw new AssertionError("The exception should apply to doors only");
				}

				// And a member of the owning team is subject to none of it.
				run(server, "team join landlords " + player.getScoreboardName());

				if (!Protection.mayBuild(player, level, pos)
						|| !Protection.mayInteract(player, level, pos, Blocks.OAK_DOOR.defaultBlockState())) {
					throw new AssertionError("A member of the owning team should be unrestricted");
				}
			} finally {
				Claims.release(level, chunk);
				run(server, "team join tenants " + player.getScoreboardName());
			}
		});
	}

	/**
	 * Planting a banner takes the chunk, which is the route most players will ever use. Goes
	 * through the real item so the placement, the mixin that notices it, and the claim rules that
	 * decide whether it counts are all in the loop.
	 */
	private static void checkBannerClaimsTheChunk(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();
			player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);

			BlockPos floor = player.blockPosition().offset(0, 24, 0);
			ChunkPos chunk = ChunkPos.containing(floor);
			fill(level, floor.offset(-1, 0, -1), floor.offset(1, 2, 1), Blocks.AIR);
			level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());

			Claims.release(level, chunk);
			run(server, "team join tenants " + player.getScoreboardName());

			ItemStack banner = new ItemStack(Items.BANNER.white());
			player.setItemInHand(InteractionHand.MAIN_HAND, banner);

			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
			InteractionResult result = player.getItemInHand(InteractionHand.MAIN_HAND)
					.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

			try {
				if (!result.consumesAction()) {
					throw new AssertionError("The banner was not placed at all: " + result);
				}

				if (!"tenants".equals(Claims.ownerOf(level, chunk))) {
					throw new AssertionError("Planting a banner should have claimed the chunk, owner is "
							+ Claims.ownerOf(level, chunk));
				}
			} finally {
				Claims.release(level, chunk);
				level.removeBlock(floor.above(), false);
			}
		});
	}

	/**
	 * TNT is the hole every claim system is tested for: everything else asks who is doing something
	 * and an explosion has nobody to ask. Checked against an unclaimed chunk in the same breath, so
	 * a pass cannot come from the explosion having done nothing.
	 */
	private static void checkExplosionsSpareClaimedChunks(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();

			// Centred inside a chunk rather than on the player, who spawns on a chunk corner: a
			// five-wide cube built there would straddle four chunks and only one of them claimed,
			// which would make this test fail for a reason that has nothing to do with claims.
			ChunkPos home = ChunkPos.containing(player.blockPosition());
			BlockPos claimed = new BlockPos(home.getMiddleBlockX(), player.blockPosition().getY() + 30,
					home.getMiddleBlockZ());
			BlockPos open = claimed.offset(32, 0, 0);

			for (BlockPos centre : List.of(claimed, open)) {
				fill(level, centre.offset(-3, -3, -3), centre.offset(3, 3, 3), Blocks.AIR);
				fill(level, centre.offset(-2, -2, -2), centre.offset(2, 2, 2), Blocks.DIRT);
			}

			Claims.claim(level, ChunkPos.containing(claimed), "landlords");
			Claims.release(level, ChunkPos.containing(open));

			try {
				level.explode(null, claimed.getX() + 0.5, claimed.getY() + 0.5, claimed.getZ() + 0.5,
						4.0F, Level.ExplosionInteraction.TNT);
				level.explode(null, open.getX() + 0.5, open.getY() + 0.5, open.getZ() + 0.5,
						4.0F, Level.ExplosionInteraction.TNT);

				int leftInClaim = count(level, claimed.offset(-2, -2, -2), claimed.offset(2, 2, 2), Blocks.DIRT);
				int leftOutside = count(level, open.offset(-2, -2, -2), open.offset(2, 2, 2), Blocks.DIRT);

				if (leftInClaim != 125) {
					throw new AssertionError("An explosion should take nothing from a claim; "
							+ (125 - leftInClaim) + " blocks went");
				}

				if (leftOutside == 125) {
					throw new AssertionError("The same explosion took nothing outside a claim either, "
							+ "so this test proves nothing");
				}
			} finally {
				Claims.release(level, ChunkPos.containing(claimed));
				fill(level, claimed.offset(-3, -3, -3), claimed.offset(3, 3, 3), Blocks.AIR);
				fill(level, open.offset(-3, -3, -3), open.offset(3, 3, 3), Blocks.AIR);
			}
		});
	}

	/**
	 * A chunk loader holds its chunk while it stands there and lets go when it comes down.
	 *
	 * <p>Asserted against vanilla's own force-load set rather than against this module's
	 * bookkeeping, because the bookkeeping agreeing with itself would prove nothing: what matters
	 * is whether the game is actually holding the chunk.
	 */
	private static void checkChunkLoaderHoldsItsChunk(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();
			Block loader = MultiplayerContent.chunkLoader();

			if (loader == null) {
				throw new AssertionError("The chunk loader block was never registered");
			}

			BlockPos pos = player.blockPosition().offset(0, 40, 0);
			long chunk = ChunkPos.containing(pos).pack();

			if (level.getForceLoadedChunks().contains(chunk)) {
				throw new AssertionError("That chunk was already force loaded before the test started");
			}

			level.setBlockAndUpdate(pos, loader.defaultBlockState());

			try {
				if (!level.getForceLoadedChunks().contains(chunk)) {
					throw new AssertionError("A placed chunk loader should hold its own chunk");
				}
			} finally {
				level.removeBlock(pos, false);
			}

			if (level.getForceLoadedChunks().contains(chunk)) {
				throw new AssertionError("Breaking the loader should have released the chunk");
			}
		});
	}

	/** The loader is reachable in survival, not just registered. */
	private static void checkChunkLoaderCrafts(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ItemStack obsidian = new ItemStack(Items.OBSIDIAN);
			ItemStack star = new ItemStack(Items.NETHER_STAR);
			ItemStack eye = new ItemStack(Items.ENDER_EYE);
			ItemStack nothing = ItemStack.EMPTY;

			CraftingInput input = CraftingInput.of(3, 3, List.of(
					nothing, eye, nothing,
					star, obsidian, star,
					obsidian, obsidian, obsidian));

			ItemStack result = server.getRecipeManager()
					.getRecipeFor(RecipeType.CRAFTING, input, server.overworld())
					.orElseThrow(() -> new AssertionError("No recipe matched the chunk loader pattern"))
					.value()
					.assemble(input);

			if (result.getItem() != item("overhaul:chunk_loader")) {
				throw new AssertionError("Expected a chunk loader, got " + result);
			}
		});
	}

	/**
	 * Deleting a team gives its land back to nobody rather than locking it forever.
	 *
	 * <p>Building claims on vanilla teams means vanilla can delete the owner out from under a
	 * claim, and land owned by a name that no longer refers to anything would be unbuildable by
	 * everyone and releasable by no one.
	 */
	private static void checkDeletingATeamReleasesItsLand(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();
			ChunkPos chunk = ChunkPos.containing(player.blockPosition().offset(0, 60, 0));

			run(server, "team add doomed");
			Claims.claim(level, chunk, "doomed");
			run(server, "team remove doomed");

			String owner = Claims.ownerOf(level, chunk);

			if (owner != null) {
				throw new AssertionError("Land held by a deleted team should have been released, "
						+ "still owned by " + owner);
			}
		});
	}

	/**
	 * The premise these commands exist for: vanilla's own {@code /team} is shut to ordinary players.
	 *
	 * <p>Asserted rather than assumed, because if Mojang ever opened it up the wrappers would be
	 * redundant, and if the requirement moved off the root literal the gate would quietly be
	 * somewhere else. Either way this is the test that should start failing.
	 */
	private static void checkVanillaTeamCommandIsStillOperatorOnly(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			CommandSourceStack ordinary = ordinarySource(server);

			if (server.getCommands().getDispatcher().getRoot().getChild("team").canUse(ordinary)) {
				throw new AssertionError("/team is open to ordinary players now, so the wrappers are moot");
			}

			if (!server.getCommands().getDispatcher().getRoot().getChild("overhaul").canUse(ordinary)) {
				throw new AssertionError("/overhaul should be reachable without permissions");
			}
		});
	}

	/**
	 * A player forming a team, taking land with it, and closing it down again — all through a
	 * command source with no permissions at all, which is the entire point of these commands.
	 */
	private static void checkPlayersCanRunTheirOwnTeams(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();
			CommandSourceStack ordinary = ordinarySource(server);

			run(server, "team leave " + player.getScoreboardName());

			server.getCommands().performPrefixedCommand(ordinary, "overhaul claim team create settlers");

			PlayerTeam team = player.getTeam();

			if (team == null || !"settlers".equals(team.getName())) {
				throw new AssertionError("Creating a team should have put the player on it, they are on " + team);
			}

			if (!TeamClaims.settings(server, "settlers").isLeader(player)) {
				throw new AssertionError("Whoever creates a team should lead it");
			}

			// And leading it is enough to claim, with no operator having touched anything.
			ChunkPos chunk = ChunkPos.containing(player.blockPosition().offset(0, 70, 0));
			Claims.release(level, chunk);
			server.getCommands().performPrefixedCommand(ordinary.withPosition(
					Vec3.atCenterOf(chunk.getMiddleBlockPosition(player.blockPosition().getY()))),
					"overhaul claim here");

			if (!"settlers".equals(Claims.ownerOf(level, chunk))) {
				throw new AssertionError("A team leader should be able to claim, owner is "
						+ Claims.ownerOf(level, chunk));
			}

			// Disbanding needs the name typed out, and takes the land with it when it gets it.
			server.getCommands().performPrefixedCommand(ordinary, "overhaul claim team disband wrongname");

			if (server.getScoreboard().getPlayerTeam("settlers") == null) {
				throw new AssertionError("Disband should refuse a name that does not match");
			}

			server.getCommands().performPrefixedCommand(ordinary, "overhaul claim team disband settlers");

			if (server.getScoreboard().getPlayerTeam("settlers") != null) {
				throw new AssertionError("Disband should have closed the team down");
			}

			if (Claims.ownerOf(level, chunk) != null) {
				throw new AssertionError("Disbanding should have released the team's land");
			}
		});
	}

	/**
	 * Joining by invitation, and the refusal when there is no invitation to redeem. This is the
	 * half that replaces {@code /team join}, which is the command an operator would otherwise be
	 * running once per player forever.
	 */
	private static void checkJoiningNeedsAnInvitation(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			CommandSourceStack ordinary = ordinarySource(server);

			run(server, "team leave " + player.getScoreboardName());
			run(server, "team add homesteaders");

			try {
				server.getCommands().performPrefixedCommand(ordinary,
						"overhaul claim team join homesteaders");

				if (player.getTeam() != null) {
					throw new AssertionError("Joining without an invitation should have been refused");
				}

				TeamInvites.offer(server, player.getUUID(), "homesteaders");
				server.getCommands().performPrefixedCommand(ordinary,
						"overhaul claim team join homesteaders");

				PlayerTeam team = player.getTeam();

				if (team == null || !"homesteaders".equals(team.getName())) {
					throw new AssertionError("An invited player should have joined, they are on " + team);
				}

				// One invitation, one join: a redeemed invite must not let them back in later.
				if (!TeamInvites.pending(server, player.getUUID()).isEmpty()) {
					throw new AssertionError("The invitation should have been used up");
				}
			} finally {
				run(server, "team leave " + player.getScoreboardName());
				run(server, "team remove homesteaders");
			}
		});
	}

	/** A command source for the player with no permissions at all, the way an ordinary player has none. */
	private static CommandSourceStack ordinarySource(net.minecraft.server.MinecraftServer server) {
		return onlyPlayer(server).createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS);
	}

	/**
	 * A piston outside a claim cannot reach into it, and one wholly inside still works.
	 *
	 * <p>The second half is the one that makes this a test rather than a switch: refusing every
	 * piston near a claim would pass the first assertion and ruin the feature.
	 */
	private static void checkPistonsStayInTheirOwnClaim(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();

			// On a chunk boundary on purpose: the piston sits in one chunk and the block it wants
			// in the next, which is exactly the geometry the rule exists for.
			ChunkPos home = ChunkPos.containing(player.blockPosition());
			int y = player.blockPosition().getY() + 34;
			BlockPos inside = new BlockPos(home.getMinBlockX(), y, home.getMiddleBlockZ());
			BlockPos outside = inside.west();

			fill(level, outside.offset(-2, -1, -2), inside.offset(2, 2, 2), Blocks.AIR);

			Claims.claim(level, home, "landlords");
			Claims.release(level, ChunkPos.containing(outside));

			try {
				// A piston in the neighbouring chunk, facing east into the claim.
				level.setBlockAndUpdate(outside, Blocks.PISTON.defaultBlockState()
						.setValue(DirectionalBlock.FACING, Direction.EAST));
				level.setBlockAndUpdate(inside, Blocks.STONE.defaultBlockState());

				if (resolves(level, outside, Direction.EAST)) {
					throw new AssertionError("A piston outside a claim should not be able to push into it");
				}

				// The same push, entirely inside the claim, is nobody else's business.
				BlockPos within = inside.east();
				BlockPos target = within.east();
				level.setBlockAndUpdate(within, Blocks.PISTON.defaultBlockState()
						.setValue(DirectionalBlock.FACING, Direction.EAST));
				level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());

				if (!resolves(level, within, Direction.EAST)) {
					throw new AssertionError("A piston inside a claim should still push blocks inside it");
				}
			} finally {
				Claims.release(level, home);
				fill(level, outside.offset(-2, -1, -2), inside.offset(2, 2, 2), Blocks.AIR);
			}
		});
	}

	/** Asks a piston at this position whether it could extend, without extending it. */
	private static boolean resolves(ServerLevel level, BlockPos piston, Direction facing) {
		return new PistonStructureResolver(level, piston, facing, true).resolve();
	}

	/**
	 * Fire does not take claimed blocks, but a fire already burning inside one still goes out.
	 *
	 * <p>Checked through the rule rather than by waiting for fire to tick, which is random and
	 * would make this a test that fails on a slow machine.
	 */
	private static void checkFireDoesNotTakeClaimedBlocks(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();

			ChunkPos home = ChunkPos.containing(player.blockPosition());
			BlockPos claimed = new BlockPos(home.getMiddleBlockX(), player.blockPosition().getY() + 38,
					home.getMiddleBlockZ());
			BlockPos open = claimed.offset(32, 0, 0);

			Claims.claim(level, home, "landlords");
			Claims.release(level, ChunkPos.containing(open));

			try {
				if (Protection.fireMayChange(level, claimed)) {
					throw new AssertionError("Fire should not be able to take a block inside a claim");
				}

				if (!Protection.fireMayChange(level, open)) {
					throw new AssertionError("Fire outside a claim should be left alone");
				}
			} finally {
				Claims.release(level, home);
			}
		});
	}

	/**
	 * Endermen take the solid bulk of a build and leave the detail.
	 *
	 * <p>Stairs, glass and fences being safe is a deliberate line rather than an accident of which
	 * tags vanilla happened to use: those are the pieces whose absence reads as broken rather than
	 * as weathered.
	 */
	private static void checkEndermenLeaveTheDetailAlone(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			if (!MobModule.endermanCanHold(Blocks.STONE.defaultBlockState())) {
				throw new AssertionError("Endermen should still take solid blocks");
			}

			for (Block safe : List.of(Blocks.OAK_STAIRS, Blocks.GLASS, Blocks.OAK_FENCE,
					Blocks.STONE_SLAB, Blocks.COBBLESTONE_WALL, Blocks.LADDER)) {
				if (MobModule.endermanCanHold(safe.defaultBlockState())) {
					throw new AssertionError("Endermen should leave " + safe + " alone");
				}
			}

			// The blocked list is the other half of the promise, and it is what keeps them off
			// anything holding your things.
			if (MobModule.endermanCanHold(Blocks.OBSIDIAN.defaultBlockState())
					|| MobModule.endermanCanHold(Blocks.CHEST.defaultBlockState())) {
				throw new AssertionError("Endermen should never take obsidian or a block entity");
			}
		});
	}

	/** Leaves a claim and a team setting behind, to be looked for after the restart. */
	private static void claimSomethingAndLeaveIt(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ServerLevel level = player.level();

			Claims.claim(level, ChunkPos.containing(player.blockPosition().offset(0, 50, 0)), "landlords");
			TeamClaims.update(server, "landlords", settings -> settings.withAnyMemberMayClaim(false));
		});
	}

	/**
	 * Claims live on the level and team settings live on the server, and the two use different
	 * halves of the attachment API. Both have to come back, and neither is checkable any other way
	 * than by shutting the world down and opening it again.
	 */
	private static void checkClaimSurvivedTheRestart(TestSingleplayerContext reopened) {
		reopened.getServer().runOnServer(server -> {
			ServerPlayer player = onlyPlayer(server);
			ChunkPos chunk = ChunkPos.containing(player.blockPosition().offset(0, 50, 0));
			String owner = Claims.ownerOf(player.level(), chunk);

			if (!"landlords".equals(owner)) {
				throw new AssertionError("The claim did not survive the restart, owner is now " + owner);
			}

			if (TeamClaims.settings(server, "landlords").anyMemberMayClaim()) {
				throw new AssertionError("The team's claim settings did not survive the restart");
			}
		});
	}
}
