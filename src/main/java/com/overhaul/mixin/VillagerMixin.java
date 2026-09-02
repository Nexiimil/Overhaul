package com.overhaul.mixin;

import com.overhaul.module.mob.MobModule;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands a villager its follow-the-emerald behaviour once its brain has finished deciding.
 *
 * <p>At the tail rather than the head on purpose: the brain writes a walk target most ticks, and
 * anything wanting to override where a villager is going has to be the last writer or it will be
 * overruled the moment it stops looking.
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {
	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void overhaul$followEmeralds(ServerLevel level, CallbackInfo ci) {
		MobModule.leadVillager((Villager) (Object) this, level);
	}
}
