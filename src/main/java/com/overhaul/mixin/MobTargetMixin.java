package com.overhaul.mixin;

import com.overhaul.module.mob.MobModule;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jspecify.annotations.Nullable;

/**
 * Stops a mob from ever picking a teammate as its target.
 *
 * <p>Cancelling the damage alone is not enough: a zombified piglin clipped by another one would
 * still turn, walk over and swing forever, which looks broken even though no damage lands. Every
 * targeting decision in the game funnels through {@code setTarget}, so refusing it here covers
 * retaliation, the shared-anger mechanics and any goal a mod adds later.
 */
@Mixin(Mob.class)
public abstract class MobTargetMixin {
	@Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
	private void overhaul$refuseTeammates(@Nullable LivingEntity target, CallbackInfo ci) {
		if (target == null || !MobModule.preventsTargeting()) {
			return;
		}

		if (MobModule.sameTeam((Mob) (Object) this, target)) {
			ci.cancel();
		}
	}
}
