package com.overhaul.mixin;

import com.overhaul.module.magical.MagicalModule;

import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gives every experience bottle the same, known payout.
 *
 * <p>Vanilla rolls 3 to 11, which is fine when bottles only ever come out of a witch or a
 * villager. It stops being fine the moment you can buy one for a fixed price at an anvil: a price
 * quoted in experience against a payout that is not is not a trade anyone can reason about.
 *
 * <p>This applies to all bottles rather than only the ones the anvil made, because a bottle is a
 * bottle — two stacks of the same item that were worth different amounts would be worse than
 * either rule on its own.
 */
@Mixin(ThrownExperienceBottle.class)
public abstract class ThrownExperienceBottleMixin {
	@ModifyArg(
			method = "onHit",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ExperienceOrb;awardWithDirection(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;I)V"),
			index = 3)
	private int overhaul$fixedPayout(int rolled) {
		int fixed = MagicalModule.fixedExperienceBottleValue();
		return fixed < 0 ? rolled : fixed;
	}
}
