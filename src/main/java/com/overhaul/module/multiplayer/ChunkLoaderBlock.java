package com.overhaul.module.multiplayer;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that holds its own chunk open.
 *
 * <p>The bookkeeping lives in {@link ChunkLoaders}; what this class owns is the two moments that
 * bookkeeping has to hear about. Both are block callbacks rather than events on purpose: a loader
 * that only noticed being broken by a player would leave a chunk pinned forever the first time an
 * operator cleared one with {@code /setblock}, and a pinned chunk with nothing standing in it is
 * invisible until someone goes looking at a profiler.
 */
public class ChunkLoaderBlock extends Block {
	public static final MapCodec<ChunkLoaderBlock> CODEC = simpleCodec(ChunkLoaderBlock::new);

	public ChunkLoaderBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);

		if (level instanceof ServerLevel server && !oldState.is(this)) {
			ChunkLoaders.add(server, pos);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		ChunkLoaders.remove(level, pos);
	}

	/**
	 * Refuses the placement, after the fact, if the world is already at its loader limit.
	 *
	 * <p>Done here rather than in {@code canSurvive} because the limit is a rule about the world
	 * rather than about the spot, and a block that silently vanished when placed would read as a
	 * bug. Giving the item back and saying why is the honest version.
	 */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);

		if (!(level instanceof ServerLevel server) || ChunkLoaders.add(server, pos)) {
			return;
		}

		level.removeBlock(pos, false);

		if (placer instanceof Player player) {
			if (!player.hasInfiniteMaterials()) {
				player.getInventory().placeItemBackInInventory(new ItemStack(this));
			}

			player.sendOverlayMessage(Component.literal("This world is already holding as many chunk "
					+ "loaders as it is allowed.").withStyle(ChatFormatting.RED));
		}
	}
}
