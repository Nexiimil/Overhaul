package com.overhaul.mixin;

import com.overhaul.module.magical.MagicalModule;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jspecify.annotations.Nullable;

/**
 * Makes the Shrouded enchantment do what a carved pumpkin does.
 *
 * <p>Vanilla's answer to endermen is already an item you wear on your head; the problem with it is
 * that it costs you a helmet and most of your view. This hooks the same check the pumpkin does, so
 * an enchanted helmet is that answer without the trade — and everything downstream of the stare,
 * including the freeze-when-looked-at behaviour, follows from the one method.
 *
 * <p>Being provoked is left alone by default. Hitting an enderman while wearing a pumpkin still
 * starts a fight, and an enchantment that made you unattackable rather than unnoticed would be a
 * different and much larger thing.
 */
@Mixin(EnderMan.class)
public abstract class EndermanShroudMixin {
	@Inject(method = "isBeingStaredBy(Lnet/minecraft/world/entity/player/Player;)Z",
			at = @At("HEAD"), cancellable = true)
	private void overhaul$lookAwayFromShroudedPlayers(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (MagicalModule.shrouds(player.getItemBySlot(EquipmentSlot.HEAD))) {
			cir.setReturnValue(Boolean.FALSE);
		}
	}

	/**
	 * Only when a pack has asked for it: an enderman that has already been provoked also loses
	 * interest. Off by default, because "they do not notice you" and "they cannot fight you" are
	 * very different promises.
	 */
	@Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
	private void overhaul$forgetShroudedPlayers(@Nullable LivingEntity target, CallbackInfo ci) {
		if (target instanceof Player player
				&& MagicalModule.shroudCalmsProvokedEndermen()
				&& MagicalModule.shrouds(player.getItemBySlot(EquipmentSlot.HEAD))) {
			ci.cancel();
		}
	}
}
