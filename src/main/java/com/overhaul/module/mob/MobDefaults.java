package com.overhaul.module.mob;

import java.util.ArrayList;
import java.util.List;

/** The shipped factions and effect pools, written to {@code mob.json} on first launch. */
final class MobDefaults {
	private MobDefaults() {
	}

	static void fill(MobConfig config) {
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
				"minecraft:endermite",
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
}
