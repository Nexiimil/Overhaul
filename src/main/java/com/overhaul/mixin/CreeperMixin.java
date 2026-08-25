package com.overhaul.mixin;

import com.overhaul.module.mob.MobModule;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.Creeper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives a creeper a random status effect just before it detonates.
 *
 * <p>Vanilla already turns whatever effects a creeper is carrying into a lingering cloud on death,
 * so handing it an effect on the way out is enough — the cloud, its radius, its decay and its
 * particles all come from vanilla, and a creeper that happens to be poisoned by other means still
 * behaves exactly as it always did.
 */
@Mixin(Creeper.class)
public abstract class CreeperMixin {
	@Inject(method = "explodeCreeper", at = @At("HEAD"))
	private void overhaul$addLingeringEffect(CallbackInfo ci) {
		Creeper creeper = (Creeper) (Object) this;
		MobEffectInstance effect = MobModule.rollCreeperEffect(creeper.getRandom(), creeper.isPowered());

		if (effect != null) {
			creeper.addEffect(effect);
		}
	}
}
