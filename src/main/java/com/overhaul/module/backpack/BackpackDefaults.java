package com.overhaul.module.backpack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.overhaul.core.config.RecipeSpec;
import com.overhaul.module.backpack.BackpackConfig.TierEntry;

/** The shipped tier ladder. Written to {@code backpack.json} on first launch and read back after. */
final class BackpackDefaults {
	private BackpackDefaults() {
	}

	static void fill(BackpackConfig config) {
		TierEntry leather = new TierEntry();
		leather.rows = 1;
		leather.recipes.put("crafting", RecipeSpec.shaped("overhaul:backpack", 1,
				List.of("LSL", "LCL", "LLL"),
				Map.of("L", "minecraft:leather", "S", "#c:strings", "C", "minecraft:chest"))
				.category("equipment"));
		config.tiers.putIfAbsent("backpack", leather);

		tier(config, "copper_backpack", 2, "overhaul:backpack", "minecraft:copper_ingot", false);
		tier(config, "iron_backpack", 3, "overhaul:copper_backpack", "minecraft:iron_ingot", false);
		tier(config, "gold_backpack", 4, "overhaul:iron_backpack", "minecraft:gold_ingot", false);
		tier(config, "diamond_backpack", 5, "overhaul:gold_backpack", "minecraft:diamond", false);
		tier(config, "netherite_backpack", 6, "overhaul:diamond_backpack", "minecraft:netherite_ingot", true);

		if (config.overburden.effects.isEmpty()) {
			// Slowness and mining fatigue rather than damage: being loaded down should change how
			// you travel and work, not threaten to kill you for organising your inventory badly.
			config.overburden.effects = new ArrayList<>(List.of(
					new BackpackConfig.BurdenEffect("minecraft:slowness", 0),
					new BackpackConfig.BurdenEffect("minecraft:mining_fatigue", 0)));
		}

		if (config.upgradeTemplate.lootTables.isEmpty()) {
			config.upgradeTemplate.lootTables = new ArrayList<>(List.of(
					"minecraft:chests/simple_dungeon",
					"minecraft:chests/abandoned_mineshaft",
					"minecraft:chests/stronghold_corridor",
					"minecraft:chests/stronghold_crossing",
					"minecraft:chests/desert_pyramid",
					"minecraft:chests/jungle_temple",
					"minecraft:chests/pillager_outpost",
					"minecraft:chests/shipwreck_supply",
					"minecraft:chests/woodland_mansion",
					"minecraft:chests/bastion_other",
					"minecraft:chests/nether_bridge",
					"minecraft:chests/village/village_cartographer",
					"minecraft:chests/village/village_tannery"));
		}

		String templateId = "overhaul:" + BackpackModule.TEMPLATE_ID;

		config.upgradeTemplate.recipes.putIfAbsent("duplicate", RecipeSpec.shapeless(templateId, 2,
				List.of(templateId, "minecraft:leather", "minecraft:leather",
						"minecraft:leather", "minecraft:leather", "#c:strings", "#c:strings"))
				.category("misc"));

		// The reverse recipe gives back what duplication costs, so a spare template is never dead
		// weight. It is shapeless with a single ingredient, which cannot collide with the
		// duplication recipe above because vanilla matches shapeless recipes on exact contents.
		config.upgradeTemplate.recipes.putIfAbsent("to_leather", RecipeSpec.shapeless("minecraft:leather", 4,
				List.of(templateId)).category("misc"));
	}

	private static void tier(BackpackConfig config, String name, int rows, String from, String material, boolean fireResistant) {
		TierEntry entry = new TierEntry();
		entry.rows = rows;
		entry.upgradeFrom = from;
		entry.upgradeMaterial = material;
		entry.fireResistant = fireResistant;
		config.tiers.putIfAbsent(name, entry);
	}
}
