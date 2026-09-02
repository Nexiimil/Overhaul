package com.overhaul.module.multiplayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.overhaul.Overhaul;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * The chunk loaders standing in a world, and the chunks they are between them holding open.
 *
 * <p>Keeping the list of loaders rather than the list of forced chunks is what makes overlapping
 * loaders work. Two loaders a few blocks apart hold many of the same chunks, and a system that
 * counted chunks instead of loaders would release a shared chunk the moment either one came down.
 * Recomputing the union each time a loader appears or disappears is exact, and it happens twice in
 * the life of a loader.
 *
 * <p>Chunks are held through vanilla's own forced-chunk mechanism — the one {@code /forceload}
 * uses — so a loader keeps working through a restart without this code running at all, and an
 * operator can see what is held with {@code /forceload query}.
 */
public final class ChunkLoaders {
	public static final AttachmentType<List<BlockPos>> PLACED =
			AttachmentRegistry.create(Overhaul.id("chunk_loaders"), builder -> builder
					.persistent(BlockPos.CODEC.listOf())
					.initializer(ArrayList::new));

	private ChunkLoaders() {
	}

	/** Forces class initialisation, which is what registers the attachment type. */
	public static void init() {
	}

	/** @return false if this world is already holding as many loaders as it is allowed */
	public static boolean add(ServerLevel level, BlockPos pos) {
		List<BlockPos> before = placed(level);

		if (before.contains(pos)) {
			return true;
		}

		int max = MultiplayerModule.chunkLoaderSettings().maxPerLevel;

		if (max > 0 && before.size() >= max) {
			return false;
		}

		List<BlockPos> after = new ArrayList<>(before);
		after.add(pos.immutable());
		commit(level, before, after);
		return true;
	}

	public static void remove(ServerLevel level, BlockPos pos) {
		List<BlockPos> before = placed(level);

		if (!before.contains(pos)) {
			return;
		}

		List<BlockPos> after = new ArrayList<>(before);
		after.remove(pos.immutable());
		commit(level, before, after);
	}

	/**
	 * Re-asserts every hold when a world loads.
	 *
	 * <p>Belt and braces rather than load-bearing: forced chunks are saved with the world, so they
	 * are normally still held when it comes back. This covers the world where somebody cleared them
	 * with {@code /forceload remove all} and the loaders are still standing there.
	 */
	public static void refresh(ServerLevel level) {
		force(level, chunksAround(placed(level)), true);
	}

	/** The chunks a single loader at this position holds. */
	public static Set<ChunkPos> chunksAround(BlockPos pos) {
		Set<ChunkPos> chunks = new LinkedHashSet<>();
		ChunkPos centre = ChunkPos.containing(pos);
		int radius = Math.max(0, MultiplayerModule.chunkLoaderSettings().radius);

		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				chunks.add(new ChunkPos(centre.x() + x, centre.z() + z));
			}
		}

		return chunks;
	}

	private static void commit(ServerLevel level, List<BlockPos> before, List<BlockPos> after) {
		Set<ChunkPos> held = chunksAround(before);
		Set<ChunkPos> wanted = chunksAround(after);

		// Release first, then take: a chunk in both sets is never touched, so a loader coming down
		// next to another one does not briefly unload what its neighbour is still holding.
		force(level, held.stream().filter(chunk -> !wanted.contains(chunk)).toList(), false);
		force(level, wanted.stream().filter(chunk -> !held.contains(chunk)).toList(), true);

		level.setAttached(PLACED, after);
	}

	private static Set<ChunkPos> chunksAround(List<BlockPos> loaders) {
		Set<ChunkPos> chunks = new LinkedHashSet<>();
		loaders.forEach(pos -> chunks.addAll(chunksAround(pos)));
		return chunks;
	}

	private static void force(ServerLevel level, Iterable<ChunkPos> chunks, boolean forced) {
		chunks.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), forced));
	}

	private static List<BlockPos> placed(ServerLevel level) {
		List<BlockPos> loaders = level.getAttached(PLACED);
		return loaders == null ? List.of() : loaders;
	}
}
