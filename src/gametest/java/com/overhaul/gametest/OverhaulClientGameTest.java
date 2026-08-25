package com.overhaul.gametest;

import java.util.List;
import java.util.Optional;

import com.overhaul.module.backpack.BackpackItem;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerConnection connection = singleplayer.getConnection();
			connection.waitForChunksRender();

			checkContentRegistered(singleplayer);
			checkEloteCraftsWithBothFlavours(singleplayer);
			checkBackpackOpens(context, singleplayer);
			checkOverburdenedSlowsYouDown(context, singleplayer);

			context.takeScreenshot("overhaul-world");
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
