package com.overhaul.module.magical;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@code config/overhaul/magical.json}.
 *
 * <p>Two separate changes share this module because they are two halves of the same idea: making
 * enchanted gear something you keep and maintain rather than something you discard once the anvil
 * says "Too Expensive!".
 */
public class MagicalConfig {
	public String _comment = "Anvil repairs no longer hit a level cap; instead a heavily enchanted item "
			+ "costs proportionally more raw material to repair. Bookshelves start empty and hold "
			+ "books you put in them. The anvil also bottles experience and splits enchanted books.";

	public AnvilSettings anvil = new AnvilSettings();
	public BookshelfSettings bookshelves = new BookshelfSettings();
	public EnchantingSettings enchanting = new EnchantingSettings();
	public EnchantmentSettings enchantments = new EnchantmentSettings();
	public XpBottleSettings xpBottles = new XpBottleSettings();
	public BookSplittingSettings bookSplitting = new BookSplittingSettings();

	public static class AnvilSettings {
		/** Removes the "Too Expensive!" wall at 40 levels. */
		public boolean removeTooExpensiveCap = true;

		/**
		 * Removes the doubling prior-work penalty. This is the change that actually makes an item
		 * repairable forever; the level cap alone is only the symptom.
		 */
		public boolean removePriorWorkPenalty = true;

		/** Extra repair material demanded per level of enchantment on the item. */
		public float materialPerEnchantmentLevel = 1.0F;

		/**
		 * Counts each enchantment once instead of once per level, so Sharpness V costs the same
		 * surcharge as Sharpness I.
		 */
		public boolean countEnchantmentsNotLevels = false;

		/** Upper bound on the surcharge, so a fully enchanted item stays repairable in one go. */
		public int maxExtraMaterial = 32;
	}

	/**
	 * Knobs shared by every enchantment this module defines.
	 *
	 * <p>These are exactly the fields of a vanilla enchantment definition, because that is what
	 * they end up as: the module writes each enchantment out as ordinary data pack JSON built from
	 * these numbers. Turning one off removes its file rather than leaving a disabled enchantment in
	 * the registry, so a world that never had it never sees it.
	 */
	public static class EnchantmentEntry {
		public boolean enabled = true;

		public int maxLevel = 1;

		/** How often it comes up relative to other enchantments. Vanilla runs from 1 to 10. */
		public int weight = 2;

		/** Levels added to an anvil job that carries this enchantment along. */
		public int anvilCost = 8;

		/** Enchanting power range this can appear in. Vanilla tops out around 65. */
		public int minCost = 20;
		public int maxCost = 55;
		public int costPerLevelAboveFirst = 0;

		/** Offered by an enchanting table. Turning this off leaves it to loot and trades. */
		public boolean inEnchantingTable = true;

		/** Villagers may sell it as a book. */
		public boolean tradeable = true;

		/** Appears on randomly enchanted loot. */
		public boolean inLoot = true;
	}

	public static class EnchantmentSettings {
		public ShroudedSettings shrouded = new ShroudedSettings();
		public VeinMineSettings veinMine = new VeinMineSettings();
	}

	/**
	 * A helmet that endermen do not react to being looked at through.
	 *
	 * <p>Vanilla already has this in the form of a carved pumpkin, and the reason nobody wears one
	 * is that it costs you the helmet slot and most of the screen. Making it an enchantment keeps
	 * the answer to endermen the same and stops it being a choice between seeing and not being
	 * attacked.
	 */
	public static class ShroudedSettings extends EnchantmentEntry {
		public ShroudedSettings() {
			weight = 4;
			anvilCost = 4;
			minCost = 10;
			maxCost = 40;
		}

		/** An enderman you have already hit stays angry, the same as one you hit wearing a pumpkin. */
		public boolean calmsProvokedEndermen = false;
	}

	/**
	 * Mines the rest of the vein when you break one block of it.
	 *
	 * <p>The correct-tool requirement is the whole balance of this enchantment rather than a
	 * detail. Without it the enchantment strip-mines anything at all; with it, it only ever
	 * finishes a job the tool in your hand was already the right one for.
	 */
	public static class VeinMineSettings extends EnchantmentEntry {
		public VeinMineSettings() {
			weight = 2;
			anvilCost = 8;
			minCost = 20;
			maxCost = 55;
		}

		/** Blocks broken in one go, counting the one you actually mined. */
		public int maxBlocks = 32;

		/** Added to that limit per level above the first, for packs that raise {@code maxLevel}. */
		public int extraBlocksPerLevel = 16;

		/**
		 * Which items may carry it. The mining tag covers pickaxes, axes, shovels and hoes, which
		 * is every tool that breaks blocks for a living.
		 */
		public String supportedItems = "#minecraft:enchantable/mining";

		/**
		 * Requires the tool to be the one that actually gets drops from the block. This is what
		 * stops a pickaxe clearing thirty-two blocks of dirt.
		 */
		public boolean requiresCorrectTool = true;

		/** Counts blocks touching at a corner, not just face to face. */
		public boolean includeDiagonals = true;

		/** Felling a log takes the leaves that belong to that wood with it. */
		public boolean axeIncludesLeaves = true;

		/** Never vein mined, whatever else is true. */
		public List<String> blocked = new ArrayList<>();
	}

	/**
	 * Water bottles and lapis into experience bottles, at an anvil.
	 *
	 * <p>Experience is the one thing in the game you cannot put in a chest, which makes it the one
	 * thing a death actually takes from you for good. Bottling it is the answer, and the anvil is
	 * where it belongs because the anvil is already the block that charges in experience.
	 */
	public static class XpBottleSettings {
		public boolean enabled = true;

		/**
		 * Experience a bottle is worth, both when it is made and when it is thrown.
		 *
		 * <p>Vanilla bottles give a random 3 to 11, which makes an exact price impossible to quote.
		 * Fixing the payout is what lets the cost below mean anything. Set
		 * {@link #fixThrownBottleValue} to false to leave vanilla's roll alone, in which case this
		 * is only the price and the two stop matching.
		 */
		public int experiencePerBottle = 10;

		/** Applies the fixed value to every experience bottle, including brewed and looted ones. */
		public boolean fixThrownBottleValue = true;

		/**
		 * Charged on top of the value of the bottle, as a percentage. Above zero, bottling is for
		 * moving experience around rather than for storing it indefinitely at no cost.
		 */
		public float surchargePercent = 10.0F;

		public int lapisPerBottle = 1;
	}

	/**
	 * Splitting one enchantment off a book and onto a blank one.
	 *
	 * <p>A multi-enchantment book is close to unusable as it stands: applying it means taking every
	 * enchantment on it, whether or not the item wants them. Splitting turns it into the pile of
	 * single books it should have been, at a level cost per split so that a librarian's stock still
	 * costs something to unpack.
	 */
	public static class BookSplittingSettings {
		public boolean enabled = true;

		/** Levels charged per split. */
		public int levelCost = 3;

		/**
		 * Books with fewer enchantments than this are left alone. At two, splitting is only ever
		 * useful, because splitting a single-enchantment book would just hand it back to you.
		 */
		public int minEnchantments = 2;
	}

	public static class EnchantingSettings {
		/**
		 * Leaves the lapis in the enchanting table when you close it, so a table you use often is
		 * stocked once rather than carried to and from. The item being enchanted still comes back.
		 */
		public boolean keepLapis = true;
	}

	public static class BookshelfSettings {
		public boolean enabled = true;

		/** How many books one shelf holds. */
		public int slots = 6;

		/** Replaces the vanilla bookshelf texture with an empty one. */
		public boolean useEmptyTexture = true;

		/** Drops the bookshelf itself instead of three books, since shelves no longer contain any. */
		public boolean dropsSelf = true;

		/** Removes books from the bookshelf crafting recipe, for the same reason. */
		public boolean craftWithoutBooks = true;

		/** A shelf only powers an enchanting table once it holds at least this many books. */
		public int booksForEnchantingPower = 1;

		/**
		 * Item ids accepted by a shelf. Anything in this list goes in, so a pack can allow modded
		 * tomes and scrolls without code changes.
		 */
		public List<String> acceptedItems = new ArrayList<>(List.of(
				"minecraft:book",
				"minecraft:written_book",
				"minecraft:writable_book",
				"minecraft:enchanted_book",
				"minecraft:knowledge_book",
				"minecraft:recovery_compass"));

		/** Also accepts anything in this item tag, without the leading {@code #}. */
		public @Nullable String acceptedTag = "minecraft:bookshelf_books";
	}
}
