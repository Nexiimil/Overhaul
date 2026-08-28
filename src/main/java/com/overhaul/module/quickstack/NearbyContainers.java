package com.overhaul.module.quickstack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Finds the containers a player is standing among.
 *
 * <p>The search asks each nearby chunk for the block entities it already has rather than walking
 * the cube of positions inside the radius: a five block radius covers over a thousand positions
 * but at most four chunk columns, and a chunk keeps its block entities in a map. That difference
 * is what makes a fresh scan on every press cheap enough that nothing has to be cached — and a
 * cache would be wrong anyway, since hoppers and other players change a chest's contents without
 * ever opening it.
 */
final class NearbyContainers {
	private NearbyContainers() {
	}

	static List<Container> around(ServerLevel level, Vec3 origin, double radius, List<String> allowed) {
		double radiusSq = radius * radius;
		List<Container> found = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();

		int minChunkX = Mth.floor(origin.x - radius) >> 4;
		int maxChunkX = Mth.floor(origin.x + radius) >> 4;
		int minChunkZ = Mth.floor(origin.z - radius) >> 4;
		int maxChunkZ = Mth.floor(origin.z + radius) >> 4;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);

				// Never force a chunk to load for this. Anything unloaded is by definition not
				// within five blocks of a player standing in a loaded one.
				if (chunk != null) {
					collect(level, chunk, origin, radiusSq, allowed, visited, found);
				}
			}
		}

		return found;
	}

	private static void collect(ServerLevel level, LevelChunk chunk, Vec3 origin, double radiusSq,
			List<String> allowed, Set<BlockPos> visited, List<Container> found) {
		// Copied because resolving a container can create a block entity in the chunk being walked.
		for (Map.Entry<BlockPos, BlockEntity> entry : List.copyOf(chunk.getBlockEntities().entrySet())) {
			BlockPos pos = entry.getKey();

			if (!visited.add(pos) || pos.distToCenterSqr(origin) > radiusSq) {
				continue;
			}

			BlockState state = entry.getValue().getBlockState();

			if (!Filters.matches(state, allowed)) {
				continue;
			}

			Container container = HopperBlockEntity.getContainerAt(level, pos);

			if (container == null) {
				continue;
			}

			claimChestPartner(state, pos, visited);
			found.add(container);
		}
	}

	/**
	 * Either half of a double chest resolves to the whole chest, so the half we have not reached
	 * yet is marked visited. Without this the chest is scanned and filled twice — harmless, but it
	 * doubles the work for the largest containers in the search.
	 */
	private static void claimChestPartner(BlockState state, BlockPos pos, Set<BlockPos> visited) {
		if (!(state.getBlock() instanceof ChestBlock)
				|| ChestBlock.getBlockType(state) == DoubleBlockCombiner.BlockType.SINGLE) {
			return;
		}

		Direction connected = ChestBlock.getConnectedDirection(state);
		visited.add(pos.relative(connected));
	}
}
