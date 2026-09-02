package com.overhaul.module.magical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.overhaul.Overhaul;
import com.overhaul.core.data.DataPackBuilder;
import com.overhaul.module.magical.MagicalConfig.EnchantmentEntry;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * The two enchantments this module adds, and the data pack files that define them.
 *
 * <p>Enchantments have been pure data since 1.21, so these are emitted as ordinary JSON into the
 * generated pack rather than registered from code. That is what lets the config switch one off,
 * retune its weight and cost, or keep it out of the enchanting table, without any of it needing
 * support here — and it means a pack author can override either file the way they would override
 * a vanilla enchantment.
 *
 * <p>Neither enchantment carries a vanilla {@code effects} block, because neither does anything
 * vanilla can express: one suppresses a hard-coded mob reaction and the other breaks blocks the
 * player never clicked. Both are therefore inert data whose behaviour lives in Java, which is why
 * everything here is about the definition and nothing here is about what they do.
 */
public final class OverhaulEnchantments {
	public static final ResourceKey<Enchantment> SHROUDED = key("shrouded");
	public static final ResourceKey<Enchantment> VEIN_MINE = key("vein_mine");

	/** Decides which enchantment a book lists first, and so which one splitting takes off it. */
	private static final TagKey<Enchantment> TOOLTIP_ORDER =
			TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("tooltip_order"));

	private OverhaulEnchantments() {
	}

	private static ResourceKey<Enchantment> key(String path) {
		return ResourceKey.create(Registries.ENCHANTMENT, Overhaul.id(path));
	}

	/**
	 * Level of one of ours on a stack.
	 *
	 * <p>Read straight off the item's own component rather than through
	 * {@code EnchantmentHelper.getItemEnchantmentLevel}, which wants a {@link Holder} and so a
	 * registry lookup. Every caller here is on a hot path — a mob's targeting check, a block
	 * break — and none of them otherwise needs registry access at all.
	 */
	public static int levelOn(ItemStack stack, ResourceKey<Enchantment> enchantment) {
		if (stack.isEmpty() || !stack.isEnchanted()) {
			return 0;
		}

		for (var entry : stack.getEnchantments().entrySet()) {
			if (entry.getKey().is(enchantment)) {
				return entry.getIntValue();
			}
		}

		return 0;
	}

	/**
	 * The enchantment a book shows on its first tooltip line.
	 *
	 * <p>"The first enchantment" has to mean the first one the player can see, so this follows the
	 * same {@code tooltip_order} tag the tooltip itself does. Anything the tag does not mention —
	 * a modded enchantment, most likely — sorts after everything it does, alphabetically, so the
	 * answer is still stable rather than falling back on hash order.
	 */
	public static @Nullable Holder<Enchantment> firstListed(ItemEnchantments enchantments,
			HolderLookup.Provider registries) {
		if (enchantments.isEmpty()) {
			return null;
		}

		List<Holder<Enchantment>> order = new ArrayList<>(enchantments.keySet());
		List<Holder<Enchantment>> tooltipOrder = registries.lookup(Registries.ENCHANTMENT)
				.flatMap(lookup -> lookup.get(TOOLTIP_ORDER))
				.map(HolderSet::stream)
				.map(java.util.stream.Stream::toList)
				.orElse(List.of());

		order.sort(Comparator
				.comparingInt((Holder<Enchantment> holder) -> {
					int index = tooltipOrder.indexOf(holder);
					return index < 0 ? Integer.MAX_VALUE : index;
				})
				.thenComparing(holder -> holder.unwrapKey()
						.map(key -> key.identifier().toString())
						.orElse(""), Comparator.naturalOrder()));

		return order.getFirst();
	}

	/** The stored enchantments on an enchanted book, which are not the same component as worn ones. */
	public static ItemEnchantments storedOn(ItemStack stack) {
		return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	// Data pack ----------------------------------------------------------------------------------

	/** Emits both enchantment definitions and adds the enabled ones to the vanilla pools. */
	public static void build(DataPackBuilder pack, MagicalConfig config) {
		List<ResourceKey<Enchantment>> inTable = new ArrayList<>();
		List<ResourceKey<Enchantment>> tradeable = new ArrayList<>();
		List<ResourceKey<Enchantment>> loot = new ArrayList<>();
		List<ResourceKey<Enchantment>> all = new ArrayList<>();

		record Definition(ResourceKey<Enchantment> key, EnchantmentEntry entry, String supportedItems, String slot) {
		}

		List<Definition> definitions = List.of(
				new Definition(SHROUDED, config.enchantments.shrouded,
						"#minecraft:enchantable/head_armor", "head"),
				new Definition(VEIN_MINE, config.enchantments.veinMine,
						config.enchantments.veinMine.supportedItems, "mainhand"));

		for (Definition definition : definitions) {
			if (!definition.entry().enabled) {
				continue;
			}

			pack.addFile(definitionFile(definition.key()),
					toJson(definition.key(), definition.entry(), definition.supportedItems(), definition.slot()));

			all.add(definition.key());

			if (definition.entry().inEnchantingTable) {
				inTable.add(definition.key());
			}

			if (definition.entry().tradeable) {
				tradeable.add(definition.key());
			}

			if (definition.entry().inLoot) {
				loot.add(definition.key());
			}
		}

		// Tag files merge across data packs unless they ask to replace, so declaring only our own
		// entries adds to the vanilla pools rather than wiping them.
		addTag(pack, "non_treasure", inTable);
		addTag(pack, "tradeable", tradeable);
		addTag(pack, "on_random_loot", loot);
		addTag(pack, "tooltip_order", all);
	}

	private static Identifier definitionFile(ResourceKey<Enchantment> key) {
		return Identifier.fromNamespaceAndPath(key.identifier().getNamespace(),
				"enchantment/" + key.identifier().getPath() + ".json");
	}

	private static void addTag(DataPackBuilder pack, String tag, List<ResourceKey<Enchantment>> entries) {
		if (entries.isEmpty()) {
			return;
		}

		JsonArray values = new JsonArray();
		entries.forEach(key -> values.add(key.identifier().toString()));

		JsonObject json = new JsonObject();
		json.add("values", values);

		pack.addFile(Identifier.withDefaultNamespace("tags/enchantment/" + tag + ".json"), json);
	}

	private static JsonObject toJson(ResourceKey<Enchantment> key, EnchantmentEntry entry,
			String supportedItems, String slot) {
		JsonObject description = new JsonObject();
		description.addProperty("translate",
				"enchantment." + key.identifier().getNamespace() + "." + key.identifier().getPath());

		JsonArray slots = new JsonArray();
		slots.add(slot);

		JsonObject json = new JsonObject();
		json.add("description", description);
		json.addProperty("supported_items", supportedItems);
		json.addProperty("weight", Math.max(1, entry.weight));
		json.addProperty("max_level", Math.max(1, entry.maxLevel));
		json.addProperty("anvil_cost", Math.max(0, entry.anvilCost));
		json.add("min_cost", cost(entry.minCost, entry.costPerLevelAboveFirst));
		json.add("max_cost", cost(entry.maxCost, entry.costPerLevelAboveFirst));
		json.add("slots", slots);
		return json;
	}

	private static JsonObject cost(int base, int perLevelAboveFirst) {
		JsonObject cost = new JsonObject();
		cost.addProperty("base", base);
		cost.addProperty("per_level_above_first", perLevelAboveFirst);
		return cost;
	}
}
