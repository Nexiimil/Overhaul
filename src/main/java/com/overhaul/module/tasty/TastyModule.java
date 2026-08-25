package com.overhaul.module.tasty;

import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;
import com.overhaul.Overhaul;
import com.overhaul.core.OverhaulModule;
import com.overhaul.core.config.ConfigManager;
import com.overhaul.core.data.DataPackBuilder;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.jspecify.annotations.Nullable;

/**
 * Food, crops and the meal system.
 *
 * <p>The interesting part of this module is that a meal's benefits come from what went into it.
 * Every ingredient carries a set of effects in the config, and cooking several together merges
 * those effects into one dish, so the module's content is really the ingredient table rather than
 * the fixed list of items.
 */
public class TastyModule implements OverhaulModule {
	private static @Nullable TastyConfig config;

	public static @Nullable TastyConfig config() {
		return config;
	}

	@Override
	public String id() {
		return "tasty";
	}

	@Override
	public String displayName() {
		return "Tasty Module";
	}

	@Override
	public void loadConfig() {
		TastyConfig loaded = ConfigManager.load(id(), TastyConfig.class);
		TastyDefaults.fill(loaded);
		ConfigManager.save(id(), loaded);
		config = loaded;
	}

	@Override
	public void registerContent() {
		TastyConfig loaded = config;

		if (loaded != null) {
			TastyContent.register(loaded);
		}
	}

	@Override
	public void registerBehaviour() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
				.register(output -> TastyContent.allItems().forEach(output::accept));

		addSeedsToGrassLoot();
	}

	/**
	 * Seeds have to come from somewhere before the first harvest, so grass drops them the same way
	 * it drops wheat seeds. The chance is deliberately low per crop: with four crops installed the
	 * combined rate stays close to vanilla's single seed drop.
	 */
	private void addSeedsToGrassLoot() {
		Set<ResourceKey<LootTable>> grassTables = java.util.stream.Stream.of(
						Blocks.SHORT_GRASS.getLootTable(),
						Blocks.TALL_GRASS.getLootTable(),
						Blocks.FERN.getLootTable())
				.flatMap(Optional::stream)
				.collect(java.util.stream.Collectors.toSet());

		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!grassTables.contains(key)) {
				return;
			}

			TastyContent.seeds().forEach((crop, seed) -> builder.pool(LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.when(LootItemRandomChanceCondition.randomChance(0.06F))
					.add(LootItem.lootTableItem(seed))
					.build()));
		});
	}

	@Override
	public void buildRecipes(DataPackBuilder pack) {
		config.foods.forEach((name, entry) -> {
			if (!entry.enabled) {
				return;
			}

			entry.recipes.forEach((recipeName, spec) -> pack.addRecipe(name + "_" + recipeName, spec));
		});

		config.crops.forEach((name, entry) -> {
			if (!entry.enabled) {
				return;
			}

			entry.seedRecipes.forEach((recipeName, spec) -> pack.addRecipe(name + "_seeds_" + recipeName, spec));
		});

		config.meals.forEach((name, entry) -> {
			if (!entry.enabled) {
				return;
			}

			Item result = TastyContent.meals().get(name);

			if (result == null) {
				return;
			}

			JsonObject json = new JsonObject();
			json.addProperty("type", Overhaul.id("meal").toString());
			json.addProperty("meal", name);
			json.addProperty("base", entry.base);
			json.addProperty("result", BuiltInRegistries.ITEM.getKey(result).toString());
			pack.addRecipeJson(Overhaul.id(name), json);
		});
	}
}
