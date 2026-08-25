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

		/** Lets an enderman pick up stairs, slabs, glass and panes, which are not solid blocks. */
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
