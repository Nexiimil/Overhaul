package com.overhaul.module.magical;

import net.minecraft.world.entity.player.Player;

/**
 * Experience points, and what they are worth in levels.
 *
 * <p>Vanilla stores a player's experience as a level plus a fraction of the way to the next one,
 * and charges for anvil work in whole levels. Anything that wants to charge an exact number of
 * points — which is the only honest way to sell a bottle that gives an exact number back — has to
 * convert between the two itself, because the game never needs to.
 *
 * <p>The level-to-total direction is vanilla's own piecewise curve. The other direction is walked
 * rather than inverted algebraically: the loop runs once when an anvil result changes, and being
 * plainly correct at every level boundary matters more here than the handful of iterations saved
 * by a square root and a fudge factor.
 */
public final class Experience {
	/** Above this the curve is linear enough that nobody is ever going to reach it legitimately. */
	private static final int MAX_LEVEL_SEARCH = 25_000;

	private Experience() {
	}

	/** Total points a player is holding, counting the part-finished level they are on. */
	public static int total(Player player) {
		return totalAtLevel(player.experienceLevel)
				+ Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
	}

	/** Points needed to have reached a level from nothing. */
	public static int totalAtLevel(int level) {
		if (level <= 0) {
			return 0;
		}

		if (level <= 16) {
			return level * level + 6 * level;
		}

		if (level <= 31) {
			return (int) (2.5 * level * level - 40.5 * level + 360.0);
		}

		return (int) (4.5 * level * level - 162.5 * level + 2220.0);
	}

	/** The level a given number of points works out to. */
	public static int levelForTotal(int total) {
		if (total <= 0) {
			return 0;
		}

		for (int level = 1; level <= MAX_LEVEL_SEARCH; level++) {
			if (totalAtLevel(level) > total) {
				return level - 1;
			}
		}

		return MAX_LEVEL_SEARCH;
	}

	/**
	 * How many levels a player would visibly drop by paying a number of points.
	 *
	 * <p>The anvil has one number to show and it is a level count, so this is what goes in it: not
	 * the price, but what paying the price costs you off the bar in front of you. It never reads
	 * as zero, because an anvil that says a job is free will not let you take it.
	 */
	public static int levelsLostPaying(Player player, int points) {
		int before = total(player);
		int after = Math.max(0, before - points);
		return Math.max(1, player.experienceLevel - levelForTotal(after));
	}
}
