package com.overhaul.module.multiplayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

/**
 * Standing invitations to join a team.
 *
 * <p>Deliberately not saved. An invitation is a conversation between two people who are both
 * playing right now, and one that outlived a server restart would be a surprise rather than a
 * convenience — the player accepting it a fortnight later would have no idea what they were
 * agreeing to. They lapse on their own as well, for the same reason.
 *
 * <p>A player may hold several at once, because being wanted by two teams is a normal thing to
 * happen and picking between them is the player's business.
 */
public final class TeamInvites {
	private record Invite(String team, long expiresAt) {
	}

	private static final Map<UUID, List<Invite>> PENDING = new HashMap<>();

	private TeamInvites() {
	}

	public static void offer(MinecraftServer server, UUID invitee, String team) {
		long expiry = server.overworld().getGameTime()
				+ Math.max(1, MultiplayerModule.claimSettings().inviteExpirySeconds) * 20L;

		List<Invite> invites = live(server, invitee);
		invites.removeIf(invite -> invite.team().equals(team));
		invites.add(new Invite(team, expiry));
		PENDING.put(invitee, invites);
	}

	/** @return true if the invitation was there to accept, in which case it is now used up */
	public static boolean redeem(MinecraftServer server, UUID invitee, String team) {
		List<Invite> invites = live(server, invitee);
		boolean found = invites.removeIf(invite -> invite.team().equals(team));

		if (invites.isEmpty()) {
			PENDING.remove(invitee);
		} else {
			PENDING.put(invitee, invites);
		}

		return found;
	}

	/** The teams currently waiting on this player, freshest last. */
	public static List<String> pending(MinecraftServer server, UUID invitee) {
		return live(server, invitee).stream().map(Invite::team).toList();
	}

	public static void forget(UUID player) {
		PENDING.remove(player);
	}

	/**
	 * Drops any invitation to a team that has since been disbanded as well as any that has lapsed,
	 * so nothing here can name a team that no longer exists.
	 */
	private static List<Invite> live(MinecraftServer server, UUID invitee) {
		List<Invite> invites = PENDING.get(invitee);

		if (invites == null) {
			return new ArrayList<>();
		}

		long now = server.overworld().getGameTime();
		List<Invite> kept = new ArrayList<>(invites);
		kept.removeIf(invite -> invite.expiresAt() <= now
				|| server.getScoreboard().getPlayerTeam(invite.team()) == null);
		return kept;
	}
}
