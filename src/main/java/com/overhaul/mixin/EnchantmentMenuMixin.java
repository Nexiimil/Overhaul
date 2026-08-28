package com.overhaul.mixin;

import com.overhaul.module.magical.EnchantingLapis;
import com.overhaul.module.magical.MagicalModule;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the lapis in the enchanting table instead of handing it back.
 *
 * <p>Only the lapis slot is kept. The item being enchanted is still returned when the screen
 * closes, because that one you are holding mid-task — leaving it behind would mean walking away
 * from a table having quietly put your sword in it.
 *
 * <p>Taking the stack on open and putting it back on close, rather than reading it where it sits,
 * is what makes this safe with two players at one table: the first to open it holds the lapis, and
 * the second finds the table empty rather than a second copy of it.
 */
@Mixin(EnchantmentMenu.class)
public class EnchantmentMenuMixin {
	/** Slot 0 is the item being enchanted; slot 1 is the lapis. */
	private static final int LAPIS_SLOT = 1;

	@Shadow @Final private Container enchantSlots;

	@Shadow @Final private ContainerLevelAccess access;

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL"))
	private void overhaul$takeStoredLapis(int containerId, Inventory inventory, ContainerLevelAccess level,
			CallbackInfo ci) {
		if (!MagicalModule.keepLapis()) {
			return;
		}

		// Before the menu is sent to the client, so the lapis is simply there when the screen opens.
		access.execute((world, pos) -> {
			ItemStack stored = EnchantingLapis.take(world, pos);

			if (!stored.isEmpty()) {
				enchantSlots.setItem(LAPIS_SLOT, stored);
			}
		});
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void overhaul$leaveLapisBehind(Player player, CallbackInfo ci) {
		if (!MagicalModule.keepLapis()) {
			return;
		}

		access.execute((world, pos) -> {
			ItemStack lapis = enchantSlots.getItem(LAPIS_SLOT);

			// Emptying the slot first is what stops vanilla returning the same lapis to the player
			// a moment later. If the table is gone, the slot is left alone and vanilla does exactly
			// that, which is the right answer when there is nowhere to leave it.
			if (!lapis.isEmpty() && EnchantingLapis.store(world, pos, lapis)) {
				enchantSlots.setItem(LAPIS_SLOT, ItemStack.EMPTY);
			}
		});
	}
}
