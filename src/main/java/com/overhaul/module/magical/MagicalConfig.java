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
			+ "books you put in them.";

	public AnvilSettings anvil = new AnvilSettings();
	public BookshelfSettings bookshelves = new BookshelfSettings();
	public EnchantingSettings enchanting = new EnchantingSettings();

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
