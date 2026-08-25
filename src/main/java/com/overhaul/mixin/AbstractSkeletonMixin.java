package com.overhaul.mixin;

import com.overhaul.module.mob.MobModule;

import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Turns skeletons into long-range shooters.
 *
 * <p>The health and speed side of that trade is applied as attribute modifiers when the mob loads;
 * the two numbers that are baked into code rather than attributes are here. The bow goal's radius
 * decides how far out a skeleton opens fire, and the spread passed to the arrow decides whether
 * shooting from that distance is worth anything.
 */
@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonMixin {
	@ModifyArg(
			method = "<init>",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/RangedBowAttackGoal;<init>(Lnet/minecraft/world/entity/monster/Monster;DIF)V"),
			index = 3)
	private float overhaul$extendBowRange(float vanillaRange) {
		return MobModule.skeletonBowRange(vanillaRange);
	}

	@ModifyArg(
			method = "performRangedAttack",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"),
			index = 7)
	private float overhaul$tightenAim(float vanillaSpread) {
		return MobModule.skeletonInaccuracy(vanillaSpread);
	}
}
