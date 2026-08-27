package com.overhaul.mixin;

import com.overhaul.module.mob.MobModule;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Widens what an enderman is willing to pick up.
 *
 * <p>Vanilla gates this on a single block tag, which is why endermen only ever move dirt and
 * flowers around. Replacing that one check with a rule — anything solid, plus the stairs, slabs,
 * glass, walls and fences that are not solid blocks, minus anything with a block entity or an
 * unbreakable hardness — lets them rearrange real builds while still leaving chests and spawners
 * alone.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class EndermanTakeBlockGoalMixin {
	@Redirect(
			method = "tick",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean overhaul$widenHoldableBlocks(BlockState state, TagKey<Block> tag) {
		return state.is(tag) || MobModule.endermanCanHold(state);
	}
}
