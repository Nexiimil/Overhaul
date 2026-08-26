package com.overhaul.module.mob;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * One faction's worth of mobs, on their way to a particular player.
 *
 * <p>A horde owns nothing the world does not already own: its members are ordinary mobs that were
 * either standing nearby or spawned normally, and the only thing that makes them a horde is that
 * they share a team and a target. So there is nothing here to save — if the server stops mid-fight
 * the mobs are still there in the morning, they have simply forgotten they were part of something.
 *
 * <p>Members are held by id rather than by reference, so a mob that unloads, despawns or is killed
 * by something else drops out of the count on its own instead of pinning an entity the level has
 * finished with.
 */
final class Horde {
	private final String team;
	private final Set<UUID> members;
	private final int initialSize;
	private final @Nullable ServerBossEvent bar;

	/** Ticks the target has spent with the whole horde out of range. Reset the moment one closes. */
	private int graceTicks;

	Horde(String team, List<Mob> founders, boolean showBar) {
		this.team = team;
		this.members = new LinkedHashSet<>();
		founders.forEach(mob -> members.add(mob.getUUID()));
		this.initialSize = Math.max(1, members.size());
		this.bar = showBar
				? new ServerBossEvent(UUID.randomUUID(), Component.translatable("boss.overhaul.horde"),
						BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10)
				: null;
	}

	String team() {
		return team;
	}

	int size() {
		return members.size();
	}

	boolean claims(UUID id) {
		return members.contains(id);
	}

	void show(ServerPlayer player) {
		ServerBossEvent event = bar;

		if (event != null) {
			event.addPlayer(player);
			event.setProgress(1.0F);
		}
	}

	/**
	 * Advances the horde by one check.
	 *
	 * <p>Retargeting on every check rather than once at the start is what keeps a horde together.
	 * Vanilla goals drop a target the moment it goes out of sight, so without this the group would
	 * dissolve back into ordinary wandering mobs the first time the player broke line of sight.
	 *
	 * @return false once the horde is finished, either because it is dead or because the player
	 *         stayed out of reach long enough for it to lose the trail
	 */
	boolean tick(ServerPlayer player, ServerLevel level, MobConfig.Hordes settings, int elapsedTicks) {
		members.removeIf(id -> resolve(level, id) == null);

		if (members.isEmpty()) {
			return false;
		}

		double leashSquared = settings.leashRadius * settings.leashRadius;
		boolean beyondLeash = true;

		for (UUID id : members) {
			Mob mob = resolve(level, id);

			if (mob == null || mob.distanceToSqr(player) > leashSquared) {
				continue;
			}

			beyondLeash = false;

			if (mob.getTarget() != player) {
				mob.setTarget(player);
			}
		}

		// The horde only breaks up once every last member has lost the trail, so outrunning the
		// fast half of it is not the same as escaping it.
		graceTicks = beyondLeash ? graceTicks + elapsedTicks : 0;

		if (graceTicks >= settings.despawnGraceTicks) {
			return false;
		}

		ServerBossEvent event = bar;

		if (event != null) {
			event.setProgress(Math.clamp((float) members.size() / initialSize, 0.0F, 1.0F));
		}

		return true;
	}

	/** Takes the bar down. The mobs themselves are left exactly as they are. */
	void disband() {
		ServerBossEvent event = bar;

		if (event != null) {
			event.removeAllPlayers();
			event.setVisible(false);
		}

		members.clear();
	}

	private static @Nullable Mob resolve(ServerLevel level, UUID id) {
		Entity entity = level.getEntityInAnyDimension(id);
		return entity instanceof Mob mob && mob.isAlive() ? mob : null;
	}
}
