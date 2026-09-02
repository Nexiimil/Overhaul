package com.overhaul.mixin;

import com.overhaul.module.multiplayer.Protection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps fire out of a claim.
 *
 * <p>Two hooks rather than one, because fire changes the world in two different ways and both are
 * position-based. It burns its immediate neighbours away, and separately it takes hold of blocks
 * further out. Blocking only one of them would leave the other as a way in.
 *
 * <p>Neither touches the fire block itself, so a fire inside a claim still burns down and goes out
 * on its own — a campfire and a lit fireplace behave exactly as they did. What it cannot do is take
 * anything with it.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	/**
	 * How likely fire is to take hold at a position. Zero is already how vanilla says "never", so
	 * this needs no special handling anywhere downstream.
	 */
	@Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
			at = @At("HEAD"), cancellable = true)
	private void overhaul$noSpreadIntoClaims(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (level instanceof ServerLevel server && !Protection.fireMayChange(server, pos)) {
			cir.setReturnValue(0);
		}
	}

	/** Burning a neighbouring block away, which is the other half of what fire does to a build. */
	@Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
	private void overhaul$noBurningClaimedBlocks(Level level, BlockPos pos, int chance, RandomSource random,
			int age, CallbackInfo ci) {
		if (level instanceof ServerLevel server && !Protection.fireMayChange(server, pos)) {
			ci.cancel();
		}
	}
}
