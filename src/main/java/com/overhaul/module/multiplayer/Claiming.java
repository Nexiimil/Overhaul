package com.overhaul.module.multiplayer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Taking a chunk, and the four reasons you might not be able to.
 *
 * <p>Shared between the command and the banner rather than written twice, because the two have to
 * agree exactly: a banner that could claim land the command would refuse is a way around every
 * limit the command enforces.
 */
public final class Claiming {
	/** @param reason empty when the claim succeeded; otherwise something to show the player */
	public record Result(boolean claimed, String reason) {
		static final Result OK = new Result(true, "");

		static Result no(String reason) {
			return new Result(false, reason);
		}
	}

	private Claiming() {
	}

	public static Result claim(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
		if (!MultiplayerModule.claimsActive()) {
			return Result.no("Claims are switched off on this server.");
		}

		PlayerTeam team = player.getTeam();

		if (team == null) {
			return Result.no("You are not on a team. Claims belong to teams, not to players.");
		}

		String owner = Claims.ownerOf(level, chunk);

		if (owner != null) {
			return owner.equals(team.getName())
					? Result.no("Your team already holds this chunk.")
					: Result.no("This chunk already belongs to another team.");
		}

		if (!mayClaimFor(level.getServer(), player, team.getName())) {
			return Result.no("Only your team's leader may claim land.");
		}

		int max = MultiplayerModule.claimSettings().maxChunksPerTeam;

		// Counted per dimension, matching where the claims themselves live: a team that has used
		// its allowance in the overworld can still hold a base in the Nether.
		if (max > 0 && Claims.countHeldBy(level, team.getName()) >= max) {
			return Result.no("Your team already holds " + max + " chunk(s) in this dimension.");
		}

		Claims.claim(level, chunk, team.getName());
		return Result.OK;
	}

	/** Whether this player is allowed to claim on behalf of a team they are already on. */
	public static boolean mayClaimFor(MinecraftServer server, ServerPlayer player, String team) {
		TeamClaims.Settings settings = TeamClaims.settings(server, team);
		return settings.anyMemberMayClaim()
				|| settings.isLeader(player)
				|| Permissions.check(MultiplayerModule.claimSettings().bypassPermission, player);
	}
}
