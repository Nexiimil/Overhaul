package com.overhaul.module.multiplayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.overhaul.Overhaul;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Which team owns which chunk, per dimension.
 *
 * <p>Held as one map on the level rather than a flag on each chunk. A claim has to be answerable
 * for a chunk that is not loaded — to stop a second team claiming it, to list what a team holds,
 * and to say whose land you are walking towards — and a chunk attachment can only answer for
 * chunks already in memory. The map costs a hash lookup on every block break, which is the same
 * order as the block lookup that prompted it.
 *
 * <p>Claims are per dimension because chunks are: the same coordinates in the Nether are somewhere
 * else entirely, and a team that has claimed its base should not thereby own the roof of the Nether
 * above it.
 */
public final class Claims {
	private record Entry(ChunkPos chunk, String team) {
		private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ChunkPos.CODEC.fieldOf("chunk").forGetter(Entry::chunk),
				Codec.STRING.fieldOf("team").forGetter(Entry::team))
				.apply(instance, Entry::new));
	}

	private static final Codec<Map<ChunkPos, String>> CODEC = Entry.CODEC.listOf().xmap(
			entries -> {
				Map<ChunkPos, String> map = new LinkedHashMap<>();
				entries.forEach(entry -> map.put(entry.chunk(), entry.team()));
				return map;
			},
			map -> map.entrySet().stream()
					.map(entry -> new Entry(entry.getKey(), entry.getValue()))
					.toList());

	public static final AttachmentType<Map<ChunkPos, String>> OWNERS =
			AttachmentRegistry.create(Overhaul.id("claims"), builder -> builder
					.persistent(CODEC)
					.initializer(LinkedHashMap::new));

	private Claims() {
	}

	/** Forces class initialisation, which is what registers the attachment type. */
	public static void init() {
	}

	/** @return the team owning this chunk, or null if nobody does */
	public static @Nullable String ownerOf(ServerLevel level, ChunkPos chunk) {
		Map<ChunkPos, String> owners = level.getAttached(OWNERS);
		return owners == null ? null : owners.get(chunk);
	}

	public static @Nullable String ownerAt(ServerLevel level, BlockPos pos) {
		return ownerOf(level, ChunkPos.containing(pos));
	}

	/** Records a claim. The caller has already decided that it is allowed. */
	public static void claim(ServerLevel level, ChunkPos chunk, String team) {
		Map<ChunkPos, String> owners = mutable(level);
		owners.put(chunk, team);
		level.setAttached(OWNERS, owners);
	}

	/** @return the team that had claimed it, or null if it was unclaimed */
	public static @Nullable String release(ServerLevel level, ChunkPos chunk) {
		Map<ChunkPos, String> owners = mutable(level);
		String had = owners.remove(chunk);
		level.setAttached(OWNERS, owners);
		return had;
	}

	/** Everything one team holds in this dimension, in the order it was claimed. */
	public static List<ChunkPos> heldBy(ServerLevel level, String team) {
		Map<ChunkPos, String> owners = level.getAttached(OWNERS);

		if (owners == null) {
			return List.of();
		}

		List<ChunkPos> held = new ArrayList<>();
		owners.forEach((chunk, owner) -> {
			if (owner.equals(team)) {
				held.add(chunk);
			}
		});

		return held;
	}

	public static int countHeldBy(ServerLevel level, String team) {
		return heldBy(level, team).size();
	}

	/**
	 * Drops every claim belonging to a team, across every dimension.
	 *
	 * <p>Needed because a team can be deleted out from under its claims by {@code /team remove},
	 * and land owned by a team that no longer exists would be permanently locked: nobody could
	 * build on it, and nobody could join the team that would let them release it.
	 */
	public static int forgetTeam(Iterable<ServerLevel> levels, String team) {
		int released = 0;

		for (ServerLevel level : levels) {
			Map<ChunkPos, String> owners = level.getAttached(OWNERS);

			if (owners == null || owners.isEmpty()) {
				continue;
			}

			Map<ChunkPos, String> kept = new LinkedHashMap<>(owners);
			int before = kept.size();
			kept.values().removeIf(team::equals);

			if (kept.size() != before) {
				released += before - kept.size();
				level.setAttached(OWNERS, kept);
			}
		}

		return released;
	}

	private static Map<ChunkPos, String> mutable(ServerLevel level) {
		Map<ChunkPos, String> owners = level.getAttached(OWNERS);
		return owners == null ? new LinkedHashMap<>() : new LinkedHashMap<>(owners);
	}
}
