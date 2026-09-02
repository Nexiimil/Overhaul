package com.overhaul.module.magical;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import com.overhaul.module.magical.MagicalConfig.VeinMineSettings;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Breaks the rest of a vein when one block of it is mined.
 *
 * <p>The rule the player learns is a short one: same block, one tool, thirty-two at most. Keeping
 * "same block" literal — the same block, not a tag or a category — is what makes it predictable,
 * because there is never a question about whether two things count as the same. The single
 * exception is the one that makes an axe worth enchanting: a tree is a log and its leaves, so
 * felling one takes both.
 *
 * <p>The correct-tool requirement is the balance lever rather than a technicality. Without it a
 * vein miner turns every block in the world into a thirty-two block strip mine; with it, the
 * enchantment only ever does the job the tool was already the right tool for.
 */
public final class VeinMine {
	/**
	 * Set while a vein is being taken apart. Every block we break fires the same break events the
	 * original did — which is what keeps protection and any other listener honest — and those
	 * events are how this class is entered in the first place.
	 */
	private static final ThreadLocal<Boolean> RUNNING = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private VeinMine() {
	}

	/**
	 * Runs a vein from a block a player has just broken.
	 *
	 * @param broken the state that was there, since the block itself is already gone by now
	 */
	public static void afterBreak(Level level, Player player, BlockPos origin, BlockState broken,
			VeinMineSettings settings) {
		if (RUNNING.get() || level.isClientSide() || player.isSpectator()) {
			return;
		}

		ItemStack tool = player.getMainHandItem();
		int enchantmentLevel = OverhaulEnchantments.levelOn(tool, OverhaulEnchantments.VEIN_MINE);

		if (enchantmentLevel <= 0 || !canStartFrom(broken, tool, settings)) {
			return;
		}

		// The block that was broken counts towards the limit, so a budget of 32 takes 31 more.
		int budget = Math.max(0, settings.maxBlocks + settings.extraBlocksPerLevel * (enchantmentLevel - 1) - 1);

		if (budget <= 0) {
			return;
		}

		RUNNING.set(Boolean.TRUE);

		try {
			spread(level, player, origin, broken, tool, settings, budget);
		} finally {
			RUNNING.set(Boolean.FALSE);
		}
	}

	/**
	 * Breadth-first from the block that was mined, so the vein comes apart outwards and a budget
	 * that runs out leaves the far end of a large deposit rather than a hole through the middle.
	 */
	private static void spread(Level level, Player player, BlockPos origin, BlockState broken,
			ItemStack tool, VeinMineSettings settings, int budget) {
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();

		seen.add(origin);
		queue.add(origin);

		int mined = 0;

		while (!queue.isEmpty() && mined < budget) {
			BlockPos current = queue.poll();

			for (BlockPos neighbour : neighbours(current, settings.includeDiagonals)) {
				if (mined >= budget || !seen.add(neighbour.immutable())) {
					continue;
				}

				BlockState state = level.getBlockState(neighbour);

				if (!belongsToVein(broken, state, settings)) {
					continue;
				}

				if (!breakBlock(level, player, neighbour, state, tool)) {
					// Refused by a listener, or the tool gave out. Either way the vein stops here.
					return;
				}

				mined++;
				queue.add(neighbour.immutable());
			}
		}
	}

	private static Iterable<BlockPos> neighbours(BlockPos pos, boolean includeDiagonals) {
		if (includeDiagonals) {
			return BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1));
		}

		return java.util.List.of(pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west());
	}

	/**
	 * Whether the enchantment applies at all to the block that was mined.
	 *
	 * <p>The tool test is deliberately the vanilla drops test rather than a speed comparison: it is
	 * already the game's own answer to "is this the right tool for this block", it accounts for
	 * material tier, and it is the one a modded tool will have answered correctly for a modded
	 * block without anyone here knowing either of them exists.
	 */
	private static boolean canStartFrom(BlockState state, ItemStack tool, VeinMineSettings settings) {
		if (isBlocked(state, settings)) {
			return false;
		}

		return !settings.requiresCorrectTool || tool.isCorrectToolForDrops(state);
	}

	private static boolean belongsToVein(BlockState origin, BlockState candidate, VeinMineSettings settings) {
		if (candidate.isAir() || isBlocked(candidate, settings)) {
			return false;
		}

		if (candidate.is(origin.getBlock())) {
			return true;
		}

		return settings.axeIncludesLeaves && isLeavesOf(origin, candidate);
	}

	private static boolean isBlocked(BlockState state, VeinMineSettings settings) {
		if (settings.blocked.isEmpty()) {
			return false;
		}

		return settings.blocked.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
	}

	/**
	 * Whether a block is the foliage belonging to the log that was mined.
	 *
	 * <p>Worked out from the block ids rather than from a table, because the naming convention —
	 * {@code oak_log} and {@code oak_leaves}, {@code stripped_cherry_wood} and
	 * {@code cherry_leaves} — is one every mod that adds a tree already follows. A wood type with
	 * no leaves under that name, such as crimson stems, simply never matches.
	 */
	private static boolean isLeavesOf(BlockState log, BlockState candidate) {
		if (!log.is(BlockTags.LOGS) || !candidate.is(BlockTags.LEAVES)) {
			return false;
		}

		Identifier logId = BuiltInRegistries.BLOCK.getKey(log.getBlock());
		String wood = logId.getPath();

		if (wood.startsWith("stripped_")) {
			wood = wood.substring("stripped_".length());
		}

		for (String suffix : new String[] { "_log", "_wood", "_stem", "_hyphae" }) {
			if (wood.endsWith(suffix)) {
				wood = wood.substring(0, wood.length() - suffix.length());
				break;
			}
		}

		Identifier leavesId = BuiltInRegistries.BLOCK.getKey(candidate.getBlock());
		return leavesId.getNamespace().equals(logId.getNamespace())
				&& leavesId.getPath().equals(wood + "_leaves");
	}

	/**
	 * Breaks one block exactly the way the player breaking it by hand would.
	 *
	 * <p>Going through {@code playerDestroy} rather than {@code Level.destroyBlock} is what keeps
	 * Fortune, Silk Touch and dropped experience working, since those all come from the tool that
	 * did the breaking and {@code destroyBlock} has no tool to hand it.
	 *
	 * @return false when the vein should stop, either because a listener refused this block or
	 *     because the tool has run out of durability
	 */
	private static boolean breakBlock(Level level, Player player, BlockPos pos, BlockState state, ItemStack tool) {
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;

		if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity)) {
			return false;
		}

		Block block = state.getBlock();
		boolean creative = player.hasInfiniteMaterials();
		ItemStack usedTool = creative ? ItemStack.EMPTY : tool.copy();

		block.playerWillDestroy(level, pos, state, player);

		if (!level.removeBlock(pos, false)) {
			return true;
		}

		block.destroy(level, pos, state);

		if (!creative) {
			// mineBlock is what spends the durability, and it is also what a modded tool hooks to
			// react to being used, so it runs before the drops rather than instead of them.
			tool.mineBlock(level, state, pos, player);
			block.playerDestroy(level, player, pos, state, blockEntity, usedTool);
		}

		PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity);

		// A tool that broke mid-vein has already been emptied by mineBlock; carrying on would mine
		// the rest of the deposit bare-handed, which is not something the enchantment was doing.
		return !tool.isEmpty();
	}
}
