package com.overhaul.module.mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.overhaul.Overhaul;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import org.jspecify.annotations.Nullable;

/**
 * Decides when a faction comes looking for someone, and puts the group together.
 *
 * <p>The gate is local difficulty, which vanilla already raises with world age, chunk inhabited
 * time and the moon. That means the trigger is not "somewhere dangerous" but "somewhere you have
 * lived in" — the base you have spent a hundred nights in is what eventually draws one, and a
 * player who just spawned is left alone without needing a separate rule to say so.
 *
 * <p>Members are gathered from teammates already loaded nearby before any are spawned. A horde
 * should make a dangerous area worse rather than doubling the mob count, and recruiting first is
 * also what makes one feel like the night closing in rather than a group materialising out of it.
 *
 * <p>Nothing here is saved. A horde is a fact about some mobs' current target, not a thing in the
 * world, so a restart simply ends it.
 */
final class HordeManager {
	/** How often an active horde is re-checked. A boss bar only needs to move about this often. */
	private static final int TICK_INTERVAL = 20;

	/** Tries at finding one spawn spot before giving up on a member. */
	private static final int SPAWN_ATTEMPTS = 24;

	/** Quiet spell after a horde fails to assemble, so a lit-up base is not re-rolled every check. */
	private static final int FAILED_RETRY_TICKS = 1200;

	/** Fewest members worth calling a horde, however badly the gathering went. */
	private static final int VIABLE_SIZE = 2;

	private final Map<UUID, Horde> active = new HashMap<>();
	private final Map<UUID, Long> readyAt = new HashMap<>();

	private int sinceTick;
	private int sinceCheck;

	void tick(MinecraftServer server, MobConfig config) {
		MobConfig.Hordes settings = config.hordes;

		if (!settings.enabled) {
			return;
		}

		if (++sinceTick >= TICK_INTERVAL) {
			sinceTick = 0;
			tickActive(server, settings);
		}

		if (++sinceCheck >= Math.max(TICK_INTERVAL, settings.checkIntervalTicks)) {
			sinceCheck = 0;
			considerAll(server, config);
		}
	}

	// Active hordes ------------------------------------------------------------------------------

	private void tickActive(MinecraftServer server, MobConfig.Hordes settings) {
		for (UUID playerId : List.copyOf(active.keySet())) {
			Horde horde = active.get(playerId);
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);

			if (horde == null) {
				continue;
			}

			// A player who logged out, died, or changed dimension is no longer being hunted; the
			// mobs stay where they are and go back to behaving like anything else.
			if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
				end(playerId, horde, server, settings);
				continue;
			}

			if (!horde.tick(player, level, settings, TICK_INTERVAL)) {
				end(playerId, horde, server, settings);
			}
		}
	}

	private void end(UUID playerId, Horde horde, MinecraftServer server, MobConfig.Hordes settings) {
		horde.disband();
		active.remove(playerId);
		readyAt.put(playerId, server.overworld().getGameTime() + settings.cooldownTicks);
	}

	// Forming ------------------------------------------------------------------------------------

	private void considerAll(MinecraftServer server, MobConfig config) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			consider(server, player, config);
		}
	}

	private void consider(MinecraftServer server, ServerPlayer player, MobConfig config) {
		MobConfig.Hordes settings = config.hordes;

		if (player.isCreative() || player.isSpectator() || active.containsKey(player.getUUID())) {
			return;
		}

		long now = server.overworld().getGameTime();

		if (now < readyAt.getOrDefault(player.getUUID(), Long.MIN_VALUE)) {
			return;
		}

		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}

		if (settings.requiresNight && level.isBrightOutside()) {
			return;
		}

		String team = settings.teamsByDimension.get(level.dimension().identifier().toString());

		if (team == null || team.isBlank()) {
			return;
		}

		List<String> roster = config.teams.members.get(team);

		if (roster == null || roster.isEmpty()) {
			return;
		}

		float difficulty = level.getCurrentDifficultyAt(player.blockPosition()).getEffectiveDifficulty();

		if (difficulty < settings.minLocalDifficulty) {
			return;
		}

		if (level.getRandom().nextFloat() >= settings.chancePerCheck) {
			return;
		}

		form(player, level, team, roster, difficulty, settings, now);
	}

	/**
	 * Builds the horde: whatever is already nearby first, then as many spawned as it takes to reach
	 * the size the local difficulty calls for.
	 */
	private void form(ServerPlayer player, ServerLevel level, String team, List<String> roster,
			float difficulty, MobConfig.Hordes settings, long now) {
		// Derive the ceiling from the floor rather than trusting both: Math.clamp throws outright if
		// it is handed a range the wrong way round, and a hand-edited config can easily do that.
		int smallest = Math.max(1, settings.minSize);
		int largest = Math.max(smallest, settings.maxSize);
		int target = Math.clamp(
				Math.round(settings.minSize + (difficulty - settings.minLocalDifficulty) * settings.sizePerDifficulty),
				smallest, largest);

		List<Mob> gathered = new ArrayList<>(recruit(player, level, team, target, settings));
		int shortfall = target - gathered.size();

		if (shortfall > 0) {
			gathered.addAll(spawn(player, level, roster, shortfall, settings));
		}

		if (gathered.size() < VIABLE_SIZE) {
			// Nowhere to put them and nothing already around: try again later rather than every
			// check, so a well-lit base is not re-rolled every ten seconds.
			readyAt.put(player.getUUID(), now + FAILED_RETRY_TICKS);
			return;
		}

		Horde horde = new Horde(team, gathered, settings.bossBar);
		gathered.forEach(mob -> mob.setTarget(player));
		active.put(player.getUUID(), horde);
		horde.show(player);

		if (settings.announce) {
			level.playSound(null, player.blockPosition(), SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE,
					SoundSource.HOSTILE, 2.0F, 0.7F);
			player.sendSystemMessage(Component.translatable("event.overhaul.horde.formed"), true);
		}

		Overhaul.LOGGER.debug("Horde of {} '{}' mobs formed on {} at local difficulty {}",
				gathered.size(), team, player.getGameProfile().name(), difficulty);
	}

	/** Teammates already loaded nearby, excluding anything another horde has already claimed. */
	private List<Mob> recruit(ServerPlayer player, ServerLevel level, String team, int limit,
			MobConfig.Hordes settings) {
		if (limit <= 0) {
			return List.of();
		}

		List<Mob> found = level.getEntitiesOfClass(Mob.class,
				player.getBoundingBox().inflate(Math.max(1, settings.recruitRadius)),
				mob -> mob.isAlive() && team.equals(MobModule.teamOf(mob)) && !claimed(mob.getUUID()));

		return found.size() <= limit ? found : found.subList(0, limit);
	}

	private boolean claimed(UUID id) {
		return active.values().stream().anyMatch(horde -> horde.claims(id));
	}

	/**
	 * Spawns the shortfall into the dark around the player.
	 *
	 * <p>Only hostile members of the team are spawned. A faction lists everything that shares its
	 * allegiance, including things like striders and happy ghasts that belong to it for the purpose
	 * of not being shot by it — those are fine as teammates and absurd as a horde, and the mob
	 * category already draws exactly that line.
	 */
	private List<Mob> spawn(ServerPlayer player, ServerLevel level, List<String> roster, int count,
			MobConfig.Hordes settings) {
		List<EntityType<?>> hostiles = hostileTypes(roster);

		if (hostiles.isEmpty()) {
			return List.of();
		}

		List<Mob> spawned = new ArrayList<>();
		BlockPos origin = player.blockPosition();

		for (int index = 0; index < count; index++) {
			BlockPos spot = findSpawn(level, origin, settings);

			if (spot == null) {
				break;
			}

			EntityType<?> type = hostiles.get(level.getRandom().nextInt(hostiles.size()));

			if (!(type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob)) {
				continue;
			}

			mob.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
					level.getRandom().nextFloat() * 360.0F, 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
			mob.setTarget(player);
			level.addFreshEntity(mob);
			spawned.add(mob);
		}

		return spawned;
	}

	private static List<EntityType<?>> hostileTypes(List<String> roster) {
		List<EntityType<?>> types = new ArrayList<>();

		for (String id : roster) {
			BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(id))
					.map(holder -> (EntityType<?>) holder.value())
					.filter(type -> type.getCategory() == MobCategory.MONSTER)
					.ifPresent(types::add);
		}

		return types;
	}

	/**
	 * Finds somewhere out of arm's reach for a member to arrive: a ring around the player, on solid
	 * ground, in the dark if the config asks for it.
	 */
	private static @Nullable BlockPos findSpawn(ServerLevel level, BlockPos origin, MobConfig.Hordes settings) {
		int min = Math.max(1, settings.spawnRadiusMin);
		int max = Math.max(min, settings.spawnRadiusMax);

		for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = min + level.getRandom().nextDouble() * (max - min);
			int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);

			// Spawning into an unloaded chunk would force it to load, so check once per column
			// rather than per candidate block.
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}

			for (int dy = 4; dy >= -8; dy--) {
				BlockPos candidate = new BlockPos(x, origin.getY() + dy, z);

				if (isSpawnable(level, candidate, settings)) {
					return candidate;
				}
			}
		}

		return null;
	}

	private static boolean isSpawnable(ServerLevel level, BlockPos pos, MobConfig.Hordes settings) {
		if (!level.getBlockState(pos).isAir()
				|| !level.getBlockState(pos.above()).isAir()
				|| !level.getBlockState(pos.below()).isSolidRender()) {
			return false;
		}

		return !settings.requireDarkSpawn || Monster.isDarkEnoughToSpawn(level, pos, level.getRandom());
	}
}
