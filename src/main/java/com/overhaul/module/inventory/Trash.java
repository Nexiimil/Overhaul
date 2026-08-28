package com.overhaul.module.inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Somewhere to put an item you want gone, with one step of undo.
 *
 * <p>Vanilla's only way to destroy an item is to throw it on the ground and wait, or find lava, so
 * a trash target is filling a real gap rather than saving a click. What makes it safe to press is
 * the undo: the last stack voided is held for as long as the player stays connected and comes back
 * if they press it again with an empty cursor. Without that, one misclick on an enchanted pickaxe
 * would be the whole argument against having the button at all.
 *
 * <p>It works on the cursor rather than on a slot, so what gets destroyed is always the stack the
 * player has already deliberately picked up.
 */
final class Trash {
	private final Map<UUID, ItemStack> recoverable = new HashMap<>();

	/** @return what to tell the player, or null if there was nothing to do */
	@Nullable Component press(ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		ItemStack carried = menu.getCarried();

		if (!carried.isEmpty()) {
			recoverable.put(player.getUUID(), carried);
			menu.setCarried(ItemStack.EMPTY);
			return Component.translatable("message.overhaul.trash.voided", carried.getCount(), carried.getHoverName());
		}

		ItemStack last = recoverable.remove(player.getUUID());

		if (last == null || last.isEmpty()) {
			return null;
		}

		menu.setCarried(last);
		return Component.translatable("message.overhaul.trash.recovered", last.getHoverName());
	}

	/** The undo buffer is a convenience within one session, not storage that has to survive one. */
	void forget(ServerPlayer player) {
		recoverable.remove(player.getUUID());
	}
}
