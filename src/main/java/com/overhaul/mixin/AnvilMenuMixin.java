package com.overhaul.mixin;

import com.overhaul.module.magical.MagicalModule;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trades the anvil's level wall for a material cost that scales with how enchanted an item is.
 *
 * <p>Vanilla stops you repairing an item once its accumulated prior-work cost passes 40 levels,
 * which turns a well-enchanted tool into a consumable. Removing that cap on its own makes repairs
 * nearly free, so the cost moves to where the player can actually pay it: the number of ingots,
 * diamonds or planks the repair consumes grows with the item's total enchantment levels.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Shadow
	private int repairItemCountCost;

	@Shadow
	@Final
	private DataSlot cost;

	/**
	 * Vanilla blanks the result when the level cost reaches 40 unless the player is in creative.
	 * Reporting "creative" to that one check lifts the cap without touching anything else the
	 * method does with the player.
	 */
	@Redirect(
			method = "createResult",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hasInfiniteMaterials()Z"))
	private boolean overhaul$liftCostCap(Player player) {
		return player.hasInfiniteMaterials() || MagicalModule.removeTooExpensiveCap();
	}

	/**
	 * Applies the enchantment surcharge once vanilla has settled on a result. Running at the end
	 * means the vanilla repair maths, including how much durability a single unit restores, is
	 * left completely alone; only the amount of material taken changes.
	 */
	@Inject(method = "createResult", at = @At("RETURN"))
	private void overhaul$chargeForEnchantments(CallbackInfo ci) {
		if (repairItemCountCost <= 0) {
			return;
		}

		AnvilMenu menu = (AnvilMenu) (Object) this;

		if (menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty()) {
			return;
		}

		ItemStack input = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
		ItemStack material = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
		int surcharge = MagicalModule.repairSurcharge(input);

		if (surcharge <= 0) {
			return;
		}

		int required = repairItemCountCost + surcharge;

		if (material.getCount() < required) {
			// Not enough material for the surcharge: show nothing rather than a cheap repair.
			menu.getSlot(AnvilMenu.RESULT_SLOT).set(ItemStack.EMPTY);
			cost.set(0);
			return;
		}

		repairItemCountCost = required;
	}

	/**
	 * Vanilla doubles an item's prior-work cost after every anvil use, which is what eventually
	 * makes it unrepairable. Returning the cost unchanged is what actually removes the cap; the
	 * 40-level check above is only where the player notices it.
	 */
	@Inject(method = "calculateIncreasedRepairCost", at = @At("HEAD"), cancellable = true)
	private static void overhaul$keepRepairCost(int baseCost, CallbackInfoReturnable<Integer> cir) {
		if (MagicalModule.removePriorWorkPenalty()) {
			cir.setReturnValue(baseCost);
		}
	}
}
