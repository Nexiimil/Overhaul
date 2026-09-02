package com.overhaul.module.multiplayer;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code config/overhaul/multiplayer.json}.
 *
 * <p>Almost nothing about a claim lives here. A claim belongs to a team, and what a team allows is
 * that team's business, so it is stored in the world and edited in game with
 * {@code /overhaul claim}. What this file holds is the shape of the system around it: whether it is
 * on, what a team's settings start out as before anyone has touched them, and the handful of
 * server-wide limits an operator rather than a team leader should be deciding.
 */
public class MultiplayerConfig {
	public String _comment = "Claims use vanilla teams: a chunk belongs to a team, not to a player. "
			+ "What each team allows is set in game with /overhaul claim; the values here are only "
			+ "what a team starts out with and the limits an operator sets over all of them.";

	public ClaimSettings claims = new ClaimSettings();
	public ChunkLoaderSettings chunkLoaders = new ChunkLoaderSettings();

	public static class ClaimSettings {
		public boolean enabled = true;

		/** Placing a banner claims the chunk it stands in for the placer's team. */
		public boolean claimWithBanner = true;

		/** Most chunks one team may hold. Zero for no limit. */
		public int maxChunksPerTeam = 0;

		/**
		 * Who ignores claims entirely: {@code none}, {@code moderators}, {@code gamemasters},
		 * {@code admins} or {@code owners}. Gamemasters is where {@code /gamemode} and most other
		 * moderation commands sit, so the default is "anyone already trusted to fix things".
		 */
		public String bypassPermission = "gamemasters";

		/** Creative mode ignores claims, on the same reasoning. */
		public boolean creativeBypasses = true;

		/**
		 * Explosions do not break blocks inside a claim.
		 *
		 * <p>Without this the whole system has a hole in it the size of a TNT block: everything
		 * else here asks who is doing something, and an explosion is nobody doing it. Any explosion
		 * is stopped rather than only a player's, because a creeper walked into a claim on purpose
		 * is exactly as effective as a lit fuse.
		 */
		public boolean protectFromExplosions = true;

		/** Item frames, armour stands, paintings and minecarts inside a claim follow its rules too. */
		public boolean protectEntities = true;

		/** Tells a player whose land they have walked into, on the action bar. */
		public boolean announceOnEnter = true;

		/**
		 * Players can form their own teams with {@code /overhaul claim team}.
		 *
		 * <p>Vanilla's own {@code /team} is operator-only from its root down, which would otherwise
		 * make every claim an operator's job: not once per team, but once per member joining one.
		 * These commands do the same work through the same scoreboard teams, gated on being a team's
		 * leader rather than on holding operator permissions.
		 *
		 * <p>Turn this off to put team management back in an operator's hands, in which case teams
		 * are made with {@code /team} and their leader named with {@code /overhaul claim leader}.
		 */
		public boolean playersMayCreateTeams = true;

		/** Most members one player-made team may have. Zero for no limit. */
		public int maxTeamSize = 0;

		/** Longest name a player-made team may have. */
		public int maxTeamNameLength = 24;

		/** How long an invitation stands before it lapses. */
		public int inviteExpirySeconds = 300;

		/** What a team allows before its leader has decided anything. */
		public AccessDefaults outsiders = new AccessDefaults(false, false);
		public AccessDefaults allies = new AccessDefaults(true, true);
	}

	/**
	 * What one class of visitor may do, as a rule plus its exceptions.
	 *
	 * <p>Two switches and a list rather than a list of permissions, because the interesting
	 * settings are all "everything except" or "nothing except": a public farm is a claim where
	 * outsiders may interact with nothing except the crops, and a trusted ally is one who may touch
	 * everything except the chests. Naming the exceptions is much shorter than naming the rule for
	 * every block in the game, and it means a modded block is covered by the rule without anyone
	 * having heard of it.
	 */
	public static class AccessDefaults {
		/** Placing and breaking blocks. */
		public boolean build;

		/** Right-clicking doors, buttons, levers, chests and everything else that responds. */
		public boolean interact;

		/**
		 * Blocks and entities where {@link #interact} is reversed, by id or by {@code #tag}. A tag
		 * covers a whole family at once, which is usually what is meant: {@code #minecraft:doors}
		 * rather than every wood type separately.
		 */
		public List<String> interactExceptions = new ArrayList<>();

		public AccessDefaults() {
		}

		public AccessDefaults(boolean build, boolean interact) {
			this.build = build;
			this.interact = interact;
		}
	}

	/**
	 * A block that keeps its chunk loaded while nobody is there.
	 *
	 * <p>The point of it is everything that stops when you walk away: furnaces, crops, breeding,
	 * and every machine on the far side of a rail line. It is priced accordingly — two nether stars
	 * means two withers, which is a long way past the point where keeping one chunk running is the
	 * cheapest problem you have.
	 */
	public static class ChunkLoaderSettings {
		public boolean enabled = true;

		/**
		 * Chunks held either side of the loader's own. Zero keeps only the chunk it stands in;
		 * one makes it a three by three.
		 */
		public int radius = 0;

		/** Most loaders one world may hold. Zero for no limit. */
		public int maxPerLevel = 0;
	}
}
