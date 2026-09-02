package com.overhaul.mixin;

import com.overhaul.module.magical.AnvilRecipes;
import com.overhaul.module.magical.MagicalModule;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jspecify.annotations.Nullable;

/**
 * Trades the anvil's level wall for a material cost, and gives the anvil two jobs it did not have.
 *
 * <p>Vanilla stops you repairing an item once its accumulated prior-work cost passes 40 levels,
 * which turns a well-enchanted tool into a consumable. Removing that cap on its own makes repairs
 * nearly free, so the cost moves to where the player can actually pay it: the number of ingots,
 * diamonds or planks the repair consumes grows with the item's total enchantment levels.
 *
 * <p>The two added jobs — bottling experience, and lifting one enchantment off a book onto a blank
 * one — live here rather than as recipes because neither is one. Both charge experience, both
 * leave something behind in an input slot instead of consuming it, and both want the preview the
 * anvil already draws. What they actually do is decided in {@link AnvilRecipes}; this class only
 * connects that to the three points in the menu where it matters.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
	@Shadow
	private int repairItemCountCost;

	@Shadow
	@Final
	private DataSlot cost;

	/**
	 * Never called: a mixin's own constructor is discarded rather than merged. It exists because
	 * declaring the superclass is how the mixin reaches {@code player}, {@code inputSlots} and
	 * {@code access}, which live on {@link ItemCombinerMenu} rather than on the anvil, and which
	 * {@code @Shadow} would not find because shadowing does not look past the target class.
	 */
	private AnvilMenuMixin(MenuType<?> menuType, int containerId, Inventory inventory,
			ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slots) {
		super(menuType, containerId, inventory, access, slots);
	}

	/**
	 * The job the current inputs describe, recomputed every time they change.
	 *
	 * <p>Held rather than matched again in {@code onTake} so that what the player paid for is
	 * exactly what they were shown, even if something moved a slot in between.
	 */
	@Unique
	private AnvilRecipes.@Nullable Recipe overhaul$custom;

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
	 * Settles what the result slot holds, once vanilla has had its say.
	 *
	 * <p>Running at the end means the vanilla repair maths, including how much durability a single
	 * unit restores, is left completely alone. Our own jobs are checked first because vanilla will
	 * have produced nothing for those inputs; the enchantment surcharge is applied second, and only
	 * ever to a vanilla repair, which is what the material cost it reads comes from.
	 */
	@Inject(method = "createResult", at = @At("RETURN"))
	private void overhaul$settleResult(CallbackInfo ci) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		overhaul$custom = MagicalModule.anvilRecipe(player,
				menu.getSlot(AnvilMenu.INPUT_SLOT).getItem(),
				menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem());

		if (overhaul$custom != null) {
			menu.getSlot(AnvilMenu.RESULT_SLOT).set(overhaul$custom.output());
			cost.set(overhaul$custom.displayCost());
			repairItemCountCost = 0;
			return;
		}

		overhaul$chargeForEnchantments(menu);
	}

	/**
	 * Applies the enchantment surcharge to a repair. Only the amount of material taken changes;
	 * the durability restored per unit is vanilla's.
	 */
	@Unique
	private void overhaul$chargeForEnchantments(AnvilMenu menu) {
		if (repairItemCountCost <= 0 || menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty()) {
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
	 * Our jobs are paid for in experience points rather than whole levels, so whether the player
	 * can afford one is a different question from the one vanilla asks.
	 */
	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void overhaul$mayTakeCustomResult(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
		AnvilRecipes.Recipe custom = overhaul$custom;

		if (custom != null) {
			cir.setReturnValue(player.hasInfiniteMaterials() || custom.canAfford(player));
		}
	}

	/**
	 * Commits one of our jobs and stops vanilla's own take from running, because vanilla would
	 * charge levels for it and empty the input slot that the job is supposed to hand back.
	 */
	@Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
	private void overhaul$takeCustomResult(Player player, ItemStack stack, CallbackInfo ci) {
		AnvilRecipes.Recipe custom = overhaul$custom;

		if (custom == null) {
			return;
		}

		cost.set(0);
		custom.take(player, inputSlots);
		overhaul$wearAnvil(player);
		ci.cancel();
	}

	/**
	 * The chip-and-clang half of a normal anvil use, which our jobs still deserve: they are anvil
	 * work, and an anvil that never wears out from them would be the cheapest way to use one.
	 */
	@Unique
	private void overhaul$wearAnvil(Player player) {
		access.execute((level, pos) -> {
			BlockState state = level.getBlockState(pos);

			if (player.hasInfiniteMaterials() || !state.is(BlockTags.ANVIL)
					|| player.getRandom().nextFloat() >= 0.12F) {
				level.levelEvent(1030, pos, 0);
				return;
			}

			overhaul$damageAnvil(level, pos, state);
		});
	}

	@Unique
	private static void overhaul$damageAnvil(Level level, BlockPos pos, BlockState state) {
		BlockState damaged = AnvilBlock.damage(state);

		if (damaged == null) {
			level.removeBlock(pos, false);
			level.levelEvent(1029, pos, 0);
			return;
		}

		level.setBlock(pos, damaged, 2);
		level.levelEvent(1030, pos, 0);
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
