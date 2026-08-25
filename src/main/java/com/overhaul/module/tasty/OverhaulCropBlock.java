package com.overhaul.module.tasty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

/**
 * A crop whose seed and produce come from the config rather than a hardcoded pair.
 *
 * <p>Drops are produced directly instead of through a loot table so that the "bonus produce"
 * count stays a config option; a loot table would have to be regenerated and reloaded to change
 * it, and a pack that wants full control can still override the block's behaviour the usual way.
 */
public class OverhaulCropBlock extends CropBlock {
	private final Supplier<ItemLike> seed;
	private final Supplier<ItemLike> produce;
	private final int bonusDropsMax;

	public OverhaulCropBlock(BlockBehaviour.Properties properties, Supplier<ItemLike> seed, Supplier<ItemLike> produce, int bonusDropsMax) {
		super(properties);
		this.seed = seed;
		this.produce = produce;
		this.bonusDropsMax = bonusDropsMax;
	}

	@Override
	public MapCodec<? extends CropBlock> codec() {
		return CropBlock.CODEC;
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return seed.get();
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		List<ItemStack> drops = new ArrayList<>();

		if (!isMaxAge(state)) {
			drops.add(new ItemStack(seed.get()));
			return drops;
		}

		drops.add(new ItemStack(produce.get()));
		drops.add(new ItemStack(seed.get()));

		if (bonusDropsMax > 0) {
			int bonus = params.getLevel().getRandom().nextInt(bonusDropsMax + 1);

			if (bonus > 0) {
				drops.add(new ItemStack(produce.get(), bonus));
			}
		}

		return drops;
	}
}
