package com.overhaul.module.magical;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.overhaul.Overhaul;
import com.overhaul.core.ModuleManager;
import com.overhaul.core.OverhaulModule;
import com.overhaul.core.config.ConfigManager;
import com.overhaul.core.config.RecipeSpec;
import com.overhaul.core.data.DataPackBuilder;
import com.overhaul.core.data.RuntimeDataPack;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Anvil repairs without a level wall, and bookshelves you stock yourself.
 *
 * <p>Both halves push in the same direction: enchanted gear becomes something you maintain, and
 * the enchanting setup around it becomes something you build up deliberately rather than a wall of
 * identical blocks.
 */
public class MagicalModule implements OverhaulModule {
	private static @Nullable MagicalConfig config;

	public static boolean removeTooExpensiveCap() {
		return config != null && config.anvil.removeTooExpensiveCap;
	}

	public static boolean removePriorWorkPenalty() {
		return config != null && config.anvil.removePriorWorkPenalty;
	}

	public static boolean bookshelvesEnabled() {
		return config != null && config.bookshelves.enabled && ModuleManager.isEnabled("magical");
	}

	public static int booksForEnchantingPower() {
		return config == null ? 0 : config.bookshelves.booksForEnchantingPower;
	}

	/**
	 * Extra units of repair material demanded by the enchantments already on an item.
	 *
	 * @return zero when the module is off, the item is unenchanted, or the surcharge is disabled
	 */
	public static int repairSurcharge(ItemStack stack) {
		if (config == null || !ModuleManager.isEnabled("magical") || stack.isEmpty()) {
			return 0;
		}

		float perLevel = config.anvil.materialPerEnchantmentLevel;

		if (perLevel <= 0.0F) {
			return 0;
		}

		ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		int weight = 0;

		for (var entry : enchantments.entrySet()) {
			weight += config.anvil.countEnchantmentsNotLevels ? 1 : entry.getIntValue();
		}

		return Math.min(config.anvil.maxExtraMaterial, Math.round(weight * perLevel));
	}

	@Override
	public String id() {
		return "magical";
	}

	@Override
	public String displayName() {
		return "Magical Module";
	}

	@Override
	public void loadConfig() {
		MagicalConfig loaded = ConfigManager.load(id(), MagicalConfig.class);
		ConfigManager.save(id(), loaded);
		config = loaded;
	}

	@Override
	public void registerContent() {
		if (config.bookshelves.enabled) {
			// Touching the class registers the attachment type before the first chunk loads.
			Bookshelves.init();
		}
	}

	@Override
	public void registerBehaviour() {
		if (!config.bookshelves.enabled) {
			return;
		}

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			BlockPos pos = hit.getBlockPos();

			if (!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BOOKSHELF)) {
				return InteractionResult.PASS;
			}

			return interact(player, level, pos, player.getItemInHand(hand));
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (!state.is(net.minecraft.world.level.block.Blocks.BOOKSHELF)) {
				return;
			}

			for (ItemStack book : Bookshelves.clear(level, pos)) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), book);
			}
		});
	}

	private InteractionResult interact(net.minecraft.world.entity.player.Player player, Level level, BlockPos pos, ItemStack held) {
		boolean takingOut = player.isShiftKeyDown() || !Bookshelves.accepts(held, config.bookshelves);

		if (level.isClientSide()) {
			// Let the client predict a swing only when something will actually happen.
			boolean willDoSomething = takingOut
					? Bookshelves.bookCount(level, pos) > 0
					: Bookshelves.bookCount(level, pos) < config.bookshelves.slots;
			return willDoSomething ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}

		if (takingOut) {
			ItemStack removed = Bookshelves.removeLast(level, pos);

			if (removed.isEmpty()) {
				return InteractionResult.PASS;
			}

			player.getInventory().placeItemBackInInventory(removed);
			level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1.1F);
			return InteractionResult.CONSUME;
		}

		ItemStack single = held.copyWithCount(1);

		if (!Bookshelves.insert(level, pos, single, config.bookshelves.slots)) {
			return InteractionResult.PASS;
		}

		if (!player.hasInfiniteMaterials()) {
			held.shrink(1);
		}

		level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 0.8F, 0.9F);
		return InteractionResult.CONSUME;
	}

	@Override
	public void buildRecipes(DataPackBuilder pack) {
		if (!config.bookshelves.enabled) {
			return;
		}

		if (config.bookshelves.craftWithoutBooks) {
			// Overriding the vanilla recipe id replaces it outright, because the generated pack sits
			// above the vanilla data pack in the stack.
			pack.addRecipe(Identifier.withDefaultNamespace("bookshelf"),
					RecipeSpec.shaped("minecraft:bookshelf", 1,
							List.of("PPP", "PPP", "PPP"),
							java.util.Map.of("P", "#minecraft:planks"))
							.category("building"));
		}

		if (config.bookshelves.dropsSelf) {
			pack.addFile(Identifier.withDefaultNamespace("loot_table/blocks/bookshelf.json"), dropsSelfLootTable());
		}

		if (config.bookshelves.useEmptyTexture) {
			addEmptyBookshelfTexture();
		}
	}

	private static JsonObject dropsSelfLootTable() {
		JsonObject entry = new JsonObject();
		entry.addProperty("type", "minecraft:item");
		entry.addProperty("name", "minecraft:bookshelf");

		JsonObject condition = new JsonObject();
		condition.addProperty("condition", "minecraft:survives_explosion");

		JsonArray conditions = new JsonArray();
		conditions.add(condition);

		JsonArray entries = new JsonArray();
		entries.add(entry);

		JsonObject pool = new JsonObject();
		pool.addProperty("rolls", 1);
		pool.addProperty("bonus_rolls", 0);
		pool.add("entries", entries);
		pool.add("conditions", conditions);

		JsonArray pools = new JsonArray();
		pools.add(pool);

		JsonObject table = new JsonObject();
		table.addProperty("type", "minecraft:block");
		table.add("pools", pools);
		return table;
	}

	/**
	 * Serves an empty shelf texture in place of vanilla's book-filled one, but only while the
	 * feature is switched on — which is why it goes through the generated pack instead of simply
	 * sitting in the mod jar, where it would apply unconditionally.
	 */
	private void addEmptyBookshelfTexture() {
		try (InputStream source = Overhaul.class.getResourceAsStream("/assets/overhaul/textures/block/empty_bookshelf.png")) {
			if (source == null) {
				Overhaul.LOGGER.warn("Empty bookshelf texture is missing from the mod jar");
				return;
			}

			RuntimeDataPack.instance().addAsset(
					Identifier.withDefaultNamespace("textures/block/bookshelf.png"), source.readAllBytes());
		} catch (IOException e) {
			Overhaul.LOGGER.error("Could not load the empty bookshelf texture", e);
		}
	}
}
