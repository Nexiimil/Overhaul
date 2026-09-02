package com.overhaul.mixin;

import com.overhaul.module.mob.MobModule;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a dispenser feed before it fires.
 *
 * <p>Injecting ahead of the whole dispense step rather than registering a behaviour per food item
 * is what makes this work for animals and foods nobody here has heard of: the decision is made by
 * asking the animal in front, at the moment the dispenser goes off, instead of by a list of item
 * ids compiled in advance.
 *
 * <p>A dispenser with nothing to feed falls through to its normal behaviour untouched, so a
 * dispenser loaded with both wheat and arrows still shoots when there is no animal there.
 */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin {
	@Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
	private void overhaul$feedBeforeDispensing(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
		if (MobModule.dispenserFed(level, state, pos)) {
			ci.cancel();
		}
	}
}
