package com.overhaul.module.mob;

import java.util.ArrayList;
import java.util.List;

import com.overhaul.module.mob.MobConfig.TradeEntry;

/** The shipped factions and effect pools, written to {@code mob.json} on first launch. */
final class MobDefaults {
	private MobDefaults() {
	}

	static void fill(MobConfig config) {
		fillTrades(config);

		config.teams.members.putIfAbsent("overworld", new ArrayList<>(List.of(
				"minecraft:zombie",
				"minecraft:zombie_villager",
				"minecraft:husk",
				"minecraft:drowned",
				"minecraft:skeleton",
				"minecraft:stray",
				"minecraft:bogged",
				"minecraft:creeper",
				"minecraft:spider",
				"minecraft:cave_spider",
				"minecraft:silverfish")));

		config.teams.members.putIfAbsent("nether", new ArrayList<>(List.of(
				"minecraft:ghast",
				"minecraft:happy_ghast",
				"minecraft:wither_skeleton",
				"minecraft:piglin",
				"minecraft:piglin_brute",
				"minecraft:zombified_piglin",
				"minecraft:hoglin",
				"minecraft:zoglin",
				"minecraft:magma_cube",
				"minecraft:blaze",
				"minecraft:strider")));

		config.teams.members.putIfAbsent("ender", new ArrayList<>(List.of(
				"minecraft:enderman",
				"minecraft:shulker",
				"minecraft:phantom")));

		config.teams.members.putIfAbsent("illager", new ArrayList<>(List.of(
				"minecraft:witch",
				"minecraft:pillager",
				"minecraft:vindicator",
				"minecraft:evoker",
				"minecraft:illusioner",
				"minecraft:ravager",
				"minecraft:vex")));

		// A horde is drawn from whichever faction belongs in the dimension it forms in, so the same
		// team lists above decide what comes for you. Illager is deliberately absent: it has its
		// own arrival in vanilla, and giving it a second one would step on that.
		config.hordes.teamsByDimension.putIfAbsent("minecraft:overworld", "overworld");
		config.hordes.teamsByDimension.putIfAbsent("minecraft:the_nether", "nether");
		config.hordes.teamsByDimension.putIfAbsent("minecraft:the_end", "ender");

		if (config.creepers.effectPool.isEmpty()) {
			config.creepers.effectPool = new ArrayList<>(List.of(
					"minecraft:poison",
					"minecraft:slowness",
					"minecraft:weakness",
					"minecraft:blindness",
					"minecraft:nausea",
					"minecraft:levitation",
					"minecraft:mining_fatigue",
					"minecraft:wither",
					"minecraft:glowing",
					"minecraft:hunger",
					"minecraft:instant_damage",
					"minecraft:darkness"));
		}

		if (config.endermen.blocked.isEmpty()) {
			config.endermen.blocked = new ArrayList<>(List.of(
					"minecraft:bedrock",
					"minecraft:obsidian",
					"minecraft:crying_obsidian",
					"minecraft:respawn_anchor",
					"minecraft:reinforced_deepslate",
					"minecraft:end_portal_frame",
					"minecraft:budding_amethyst",
					"minecraft:spawner",
					"minecraft:trial_spawner",
					"minecraft:vault",
					"minecraft:ancient_debris"));
		}

		if (config.fleeing.excluded.isEmpty()) {
			config.fleeing.excluded = new ArrayList<>(List.of(
					"minecraft:ender_dragon",
					"minecraft:wither",
					"minecraft:warden",
					"minecraft:elder_guardian",
					"minecraft:iron_golem",
					"minecraft:snow_golem",
					"minecraft:shulker",
					"minecraft:ravager"));
		}
	}

	/**
	 * The shipped villager stock.
	 *
	 * <p>Aimed at the two places vanilla trading is thinnest: the farmer, who buys four crops and
	 * sells almost nothing worth walking to a village for, and the middle levels of every other
	 * profession. Most of what is added is this mod's own food, priced against the vanilla crop
	 * trades it sits next to; the rest is vanilla goods that had no seller.
	 *
	 * <p>Any entry naming an item from a module that is switched off is dropped when the data pack
	 * is built, so a player running only the mob module still gets a valid, if shorter, list.
	 */
	private static void fillTrades(MobConfig config) {
		// Farmer: this mod's crops, bought and sold the way vanilla buys and sells wheat.
		trade(config, "farmer/1/tomato_emerald", new TradeEntry("overhaul:tomato", 20, "minecraft:emerald", 1)
				.uses(16).experience(2));
		trade(config, "farmer/1/lettuce_emerald", new TradeEntry("overhaul:lettuce", 24, "minecraft:emerald", 1)
				.uses(16).experience(2));
		trade(config, "farmer/1/emerald_tomato_seeds", new TradeEntry("minecraft:emerald", 1, "overhaul:tomato_seeds", 4)
				.uses(16).experience(2));
		trade(config, "farmer/2/corn_emerald", new TradeEntry("overhaul:corn", 18, "minecraft:emerald", 1));
		trade(config, "farmer/2/emerald_flour", new TradeEntry("minecraft:emerald", 1, "overhaul:flour", 6));
		trade(config, "farmer/3/emerald_cheese", new TradeEntry("minecraft:emerald", 2, "overhaul:cheese", 3)
				.experience(20));
		trade(config, "farmer/4/emerald_honey_glazed_ham",
				new TradeEntry("minecraft:emerald", 4, "overhaul:honey_glazed_ham", 2).experience(30));
		trade(config, "farmer/5/emerald_golden_baked_potato",
				new TradeEntry("minecraft:emerald", 4, "overhaul:golden_baked_potato", 3).experience(0));

		// Butcher: prepared food, including the vanilla meats nobody sells.
		trade(config, "butcher/2/emerald_fried_egg", new TradeEntry("minecraft:emerald", 1, "overhaul:fried_egg", 5));
		trade(config, "butcher/3/emerald_cooked_rabbit",
				new TradeEntry("minecraft:emerald", 2, "minecraft:cooked_rabbit", 4).experience(20));
		trade(config, "butcher/4/emerald_trail_mix",
				new TradeEntry("minecraft:emerald", 3, "overhaul:trail_mix", 6).experience(30));

		// Fisherman: the two vanilla goods a coastal village ought to have and does not.
		trade(config, "fisherman/2/emerald_cooked_salmon",
				new TradeEntry("minecraft:emerald", 2, "minecraft:cooked_salmon", 5));
		trade(config, "fisherman/3/emerald_lantern",
				new TradeEntry("minecraft:emerald", 4, "minecraft:lantern", 1).experience(20));

		// Librarian: bottled experience, which is what a librarian would sell if they could.
		trade(config, "librarian/4/emerald_experience_bottle",
				new TradeEntry("minecraft:emerald", 3, "minecraft:experience_bottle", 2).experience(30));

		// Leatherworker: the first backpack, for players who would rather buy one than hunt leather.
		trade(config, "leatherworker/3/emerald_and_leather_backpack",
				new TradeEntry("minecraft:emerald", 12, "overhaul:backpack", 1)
						.with("minecraft:leather", 6).uses(2).experience(20));

		// Cleric: glistering melon, which is finally worth eating.
		trade(config, "cleric/2/emerald_glistering_melon_slice",
				new TradeEntry("minecraft:emerald", 3, "minecraft:glistering_melon_slice", 2));

		// Two vanilla goods with no seller anywhere in a village.
		trade(config, "toolsmith/2/emerald_flint_and_steel",
				new TradeEntry("minecraft:emerald", 3, "minecraft:flint_and_steel", 1));
		trade(config, "mason/3/emerald_calcite",
				new TradeEntry("minecraft:emerald", 1, "minecraft:calcite", 4).experience(20));
	}

	private static void trade(MobConfig config, String key, TradeEntry entry) {
		config.villagers.addedTrades.putIfAbsent(key, entry);
	}
}
