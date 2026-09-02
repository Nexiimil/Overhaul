package com.overhaul.module.magical;

import com.overhaul.core.ModuleManager;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * The two jobs this module adds to the anvil.
 *
 * <p>Both are things the anvil is already the right shape for — two inputs, a preview, a price in
 * experience — and neither is expressible as a recipe, because both charge experience by the point
 * and both leave something behind in an input slot rather than consuming it.
 *
 * <p>Everything here is pure: matching produces a description of what would happen, and nothing
 * changes until {@link Recipe#take} is called. That is what lets the same code drive the preview
 * the player is looking at and the transaction they eventually commit to, with no chance of the
 * two disagreeing.
 */
public final class AnvilRecipes {

	/** A job the anvil can do that vanilla has no idea about. */
	public sealed interface Recipe {
		/** What appears in the result slot. */
		ItemStack output();

		/** Levels the player will visibly lose, which is the number the anvil screen shows. */
		int displayCost();

		boolean canAfford(Player player);

		/** Charges the player and rewrites the input slots. Only ever called on the server. */
		void take(Player player, Container inputs);
	}

	/**
	 * Water bottles and lapis into experience bottles.
	 *
	 * <p>Priced in points rather than levels so that what a bottle costs and what it gives back are
	 * the same currency. The surcharge on top is what stops this being a lossless battery: bottling
	 * experience is for carrying it somewhere or handing it to someone, not for holding it.
	 */
	public record XpBottles(ItemStack output, int displayCost, int experienceCost, int bottles, int lapis)
			implements Recipe {
		@Override
		public boolean canAfford(Player player) {
			return Experience.total(player) >= experienceCost;
		}

		@Override
		public void take(Player player, Container inputs) {
			// Creative pays nothing but still uses up what it was given, which is what the anvil
			// does with a repair: the free part of creative mode is the price, not the materials.
			if (!player.hasInfiniteMaterials()) {
				player.giveExperiencePoints(-experienceCost);
			}

			inputs.removeItem(AnvilMenu.INPUT_SLOT, bottles);
			inputs.removeItem(AnvilMenu.ADDITIONAL_SLOT, lapis);
		}
	}

	/**
	 * Lifts one enchantment off a book and onto a blank one.
	 *
	 * <p>The source book stays in its slot with the rest of its enchantments, which is the whole
	 * point: a five-enchantment book from a dungeon becomes five books you can actually use, one
	 * anvil use at a time, instead of one book you have to apply all of at once.
	 */
	public record SplitEnchantment(ItemStack output, int displayCost, ItemStack remainder) implements Recipe {
		@Override
		public boolean canAfford(Player player) {
			return player.experienceLevel >= displayCost;
		}

		@Override
		public void take(Player player, Container inputs) {
			if (!player.hasInfiniteMaterials()) {
				player.giveExperienceLevels(-displayCost);
			}

			inputs.setItem(AnvilMenu.INPUT_SLOT, remainder.copy());
			inputs.removeItem(AnvilMenu.ADDITIONAL_SLOT, 1);
		}
	}

	private AnvilRecipes() {
	}

	/**
	 * Works out whether the two input slots describe one of our jobs.
	 *
	 * @return null when they do not, in which case the anvil's own result stands untouched
	 */
	public static @Nullable Recipe match(Player player, ItemStack input, ItemStack material, MagicalConfig config) {
		if (!ModuleManager.isEnabled("magical") || input.isEmpty() || material.isEmpty()) {
			return null;
		}

		Recipe bottles = matchXpBottles(player, input, material, config.xpBottles);

		if (bottles != null) {
			return bottles;
		}

		return matchSplit(player, input, material, config.bookSplitting);
	}

	private static @Nullable Recipe matchXpBottles(Player player, ItemStack input, ItemStack material,
			MagicalConfig.XpBottleSettings settings) {
		if (!settings.enabled || !isWaterBottle(input) || !material.is(Items.LAPIS_LAZULI)) {
			return null;
		}

		int lapisPerBottle = Math.max(1, settings.lapisPerBottle);
		int bottles = Math.min(input.getCount(), material.getCount() / lapisPerBottle);

		if (bottles <= 0) {
			return null;
		}

		// A water bottle does not stack in vanilla, so this is one at a time unless something else
		// has changed that. Working from the counts rather than assuming one means it simply scales
		// if it ever does.
		bottles = Math.min(bottles, new ItemStack(Items.EXPERIENCE_BOTTLE).getMaxStackSize());

		int perBottle = costPerBottle(settings);
		int cost = perBottle * bottles;

		if (cost <= 0) {
			return null;
		}

		return new XpBottles(new ItemStack(Items.EXPERIENCE_BOTTLE, bottles),
				Experience.levelsLostPaying(player, cost), cost, bottles, bottles * lapisPerBottle);
	}

	/** What one bottle costs to make: what it will give back, plus the surcharge. */
	public static int costPerBottle(MagicalConfig.XpBottleSettings settings) {
		float tax = 1.0F + Math.max(0.0F, settings.surchargePercent) / 100.0F;
		return Math.max(1, Mth.ceil(Math.max(1, settings.experiencePerBottle) * tax));
	}

	private static boolean isWaterBottle(ItemStack stack) {
		return stack.is(Items.POTION)
				&& stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
	}

	private static @Nullable Recipe matchSplit(Player player, ItemStack input, ItemStack material,
			MagicalConfig.BookSplittingSettings settings) {
		// One book at a time: the remainder goes back into the input slot, and a stack there would
		// mean writing one book over several.
		if (!settings.enabled || !input.is(Items.ENCHANTED_BOOK) || input.getCount() != 1 || !material.is(Items.BOOK)) {
			return null;
		}

		ItemEnchantments stored = OverhaulEnchantments.storedOn(input);

		if (stored.size() < Math.max(1, settings.minEnchantments)) {
			return null;
		}

		Holder<Enchantment> first = OverhaulEnchantments.firstListed(stored, player.level().registryAccess());

		if (first == null) {
			return null;
		}

		int level = stored.getLevel(first);

		ItemEnchantments.Mutable taken = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		taken.set(first, level);

		ItemStack output = new ItemStack(Items.ENCHANTED_BOOK);
		output.set(DataComponents.STORED_ENCHANTMENTS, taken.toImmutable());

		ItemEnchantments.Mutable left = new ItemEnchantments.Mutable(stored);
		left.removeIf(first::equals);
		ItemEnchantments remaining = left.toImmutable();

		// A book that has given up its last enchantment is a book again, not an enchanted book with
		// nothing on it — which would stack separately and read as a bug.
		ItemStack remainder = remaining.isEmpty() ? new ItemStack(Items.BOOK) : input.copyWithCount(1);

		if (!remaining.isEmpty()) {
			remainder.set(DataComponents.STORED_ENCHANTMENTS, remaining);
		}

		return new SplitEnchantment(output, Math.max(1, settings.levelCost), remainder);
	}
}
