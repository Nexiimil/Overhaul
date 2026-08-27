package com.overhaul.module.mob;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config/overhaul/mob.json}.
 *
 * <p>Team membership is a plain list of entity ids per team, so adding a modded mob to a faction —
 * or splitting the vanilla ones differently — is a config edit. Every behavioural tweak below is
 * expressed as a multiplier on the vanilla value rather than an absolute, so the numbers stay
 * meaningful if Mojang retunes a mob.
 */
public class MobConfig {
	public String _comment = "Mobs on the same team cannot hurt each other. Everything else here retunes "
			+ "individual mobs; multipliers are relative to their vanilla values.";

	public Teams teams = new Teams();
	public Hordes hordes = new Hordes();
	public Zombies zombies = new Zombies();
	public Skeletons skeletons = new Skeletons();
	public Creepers creepers = new Creepers();
	public Endermen endermen = new Endermen();
	public Spiders spiders = new Spiders();
	public Fleeing fleeing = new Fleeing();

	public static class Teams {
		public boolean enabled = true;

		/** Whether a stray arrow or explosion from a teammate still hurts. */
		public boolean friendlyFire = false;

		/** Teams also refuse to target each other, not just to damage each other. */
		public boolean preventTargeting = true;

		public Map<String, List<String>> members = new LinkedHashMap<>();
	}

	/**
	 * A faction that comes for you on a bad night.
	 *
	 * <p>Hordes are the payoff for the team system rather than a separate feature: a group drawn
	 * from one faction cannot defuse itself on the way over, so what arrives is what set out. The
	 * gate is local difficulty, which already rises with how long you have lived in a chunk — so
	 * the place you have settled is the place that eventually comes looking, and a fresh spawn is
	 * left alone.
	 *
	 * <p>Members are gathered from the teammates already loaded nearby before any are spawned, so a
	 * horde makes an area that is already dangerous worse rather than doubling the mob count.
	 */
	public static class Hordes {
		public boolean enabled = true;

		/**
		 * Hordes only form after dark. A dimension with no day cycle, such as the Nether or the
		 * End, counts as permanently dark — which is the intent, since there is no night there to
		 * wait for. Turn this off to let them form in daylight too.
		 */
		public boolean requiresNight = true;

		/**
		 * Local difficulty a chunk must reach before it can produce a horde.
		 *
		 * <p>Vanilla's local difficulty is
		 * {@code difficultyId x (0.75 + worldAge + chunkInhabitedTime + moon)}, with everything
		 * after the 0.75 halved on Easy. That gives it a different ceiling on each setting:
		 * <b>1.5</b> on Easy, <b>4.0</b> on Normal and <b>6.75</b> on Hard, against floors of
		 * 0.75, 1.5 and 2.25 in a brand new chunk.
		 *
		 * <p>The default sits above Easy's ceiling on purpose — hordes are something the harder
		 * settings opt into. On Normal it wants a world some way in and a chunk that has been lived
		 * in; on Hard it comes into reach early. Raise it towards 4 to make them rare, or drop it
		 * below 0.75 to have them everywhere on any setting.
		 */
		public float minLocalDifficulty = 2.5F;

		/** Which faction a horde is drawn from, per dimension. A dimension with no team never forms one. */
		public Map<String, String> teamsByDimension = new LinkedHashMap<>();

		public int minSize = 4;
		public int maxSize = 12;

		/** Extra members per point of local difficulty above {@link #minLocalDifficulty}. */
		public float sizePerDifficulty = 1.5F;

		/** How far out teammates are gathered from before the rest are spawned. */
		public int recruitRadius = 40;

		/** Ring around the player that topped-up members spawn into, in blocks. */
		public int spawnRadiusMin = 12;
		public int spawnRadiusMax = 28;

		/** Only spawn where a hostile mob could normally stand in the dark. */
		public boolean requireDarkSpawn = true;

		/** How often a player is considered for a horde. */
		public int checkIntervalTicks = 200;

		/**
		 * Chance a qualifying player actually gets one on any given check. Low on purpose: a night
		 * is about 9000 ticks, so at the default interval this is a coin toss across a whole night
		 * rather than something that fires the moment the sun goes down.
		 */
		public float chancePerCheck = 0.05F;

		/**
		 * Quiet spell after a horde ends, before that player can draw another. A full day-night
		 * cycle by default, so two hordes never stack up in one night.
		 */
		public int cooldownTicks = 24000;

		/** How far a horde will follow before it loses interest. */
		public double leashRadius = 96.0;

		/** How long a player has to stay clear of that radius for the horde to break up. */
		public int despawnGraceTicks = 600;

		/** A sound and a message when a horde forms, so it reads as an event rather than a spike. */
		public boolean announce = true;

		/** A bar tracking how many of the horde are left, so the fight has a visible end. */
		public boolean bossBar = true;
	}

	public static class Zombies {
		public boolean enabled = true;

		/**
		 * Each zombie picks a speed in this range, fixed by its own id so it stays the same
		 * whenever the chunk reloads. A pack of zombies then arrives strung out rather than
		 * as one wall.
		 */
		public float minSpeedMultiplier = 0.75F;
		public float maxSpeedMultiplier = 1.35F;

		/** Chance per hit that a wounded zombie calls one of its own up out of the ground. */
		public float callForHelpChance = 0.12F;
		public int maxHelpers = 2;
		public int helpCooldownTicks = 300;
		public int helpSearchRadius = 6;

		/** Chance that a zombie's corpse gets back up as a skeleton. */
		public float riseAsSkeletonChance = 0.15F;
	}

	public static class Skeletons {
		public boolean enabled = true;

		/** Frailer and slower, but they open fire from much further out. */
		public float healthMultiplier = 0.65F;
		public float speedMultiplier = 0.85F;
		public float followRangeMultiplier = 1.75F;

		/** Distance in blocks at which a skeleton starts shooting. Vanilla is 15. */
		public float bowRange = 26.0F;

		/**
		 * Multiplies the spread on their arrows. Below one they stay accurate at the longer
		 * range, which is the point of the trade: dangerous far away, weak up close.
		 */
		public float inaccuracyMultiplier = 0.35F;
	}

	public static class Creepers {
		public boolean enabled = true;

		/** Chance that a creeper's explosion leaves a lingering cloud behind. */
		public float lingeringChance = 0.6F;
		public int durationTicks = 300;
		public int maxAmplifier = 1;

		/** Effects a creeper may be carrying. Charged creepers roll twice. */
		public List<String> effectPool = new ArrayList<>();
	}

	public static class Endermen {
		public boolean enabled = true;

		/** Lets an enderman pick up anything that renders as a full solid block. */
		public boolean carryAnySolidBlock = true;

		/** Lets an enderman pick up stairs, slabs, glass, walls and fences, which are not solid. */
		public boolean carryPartialBlocks = true;

		/** Never picked up, whatever the rules above say. */
		public List<String> blocked = new ArrayList<>();
	}

	public static class Spiders {
		public boolean enabled = true;

		/** Chance per hit that a wounded spider leaves a cobweb where it stood. */
		public float webOnHurtChance = 0.2F;
	}

	public static class Fleeing {
		public boolean enabled = true;

		/** Below this fraction of maximum health a mob will try to break off and run. */
		public float healthFraction = 0.5F;
		public float speedMultiplier = 1.3F;

		/** How long a mob keeps running before it will consider fighting again. */
		public int durationTicks = 120;

		public boolean hostilesFlee = true;
		public boolean passivesFlee = true;

		/** Mobs that never flee, such as bosses and anything that cannot walk away. */
		public List<String> excluded = new ArrayList<>();
	}
}
