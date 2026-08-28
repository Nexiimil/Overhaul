package com.overhaul.client.inventory;

import com.overhaul.client.mixin.ContainerScreenAccess;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * What this module knows about the screens it decorates.
 *
 * <p>Keeping the accessor cast in one place means the mixin is named once rather than at every
 * call site, and keeping the screen test in one place means the buttons and the keys agree about
 * where they apply — which matters more than it sounds, because the creative inventory is an
 * {@code AbstractContainerScreen} with a text field in it, and a letter key that both types into
 * the search box and locks a slot would be nobody's idea of a shortcut.
 */
final class InventoryScreens {
	private InventoryScreens() {
	}

	/** The player's own inventory, and any nine-wide container: chest, barrel, shulker, backpack. */
	static boolean handles(Screen screen) {
		return screen instanceof InventoryScreen
				|| (screen instanceof AbstractContainerScreen<?> container
						&& container.getMenu() instanceof ChestMenu);
	}

	static int left(AbstractContainerScreen<?> screen) {
		return ((ContainerScreenAccess) screen).overhaul$leftPos();
	}

	static int top(AbstractContainerScreen<?> screen) {
		return ((ContainerScreenAccess) screen).overhaul$topPos();
	}

	static int panelWidth(AbstractContainerScreen<?> screen) {
		return ((ContainerScreenAccess) screen).overhaul$imageWidth();
	}

	static int panelHeight(AbstractContainerScreen<?> screen) {
		return ((ContainerScreenAccess) screen).overhaul$imageHeight();
	}

	static @Nullable Slot hovered(AbstractContainerScreen<?> screen) {
		return ((ContainerScreenAccess) screen).overhaul$hoveredSlot();
	}

	/** @return the slot's index in the player's own inventory, or -1 if it is somewhere else */
	static int playerSlot(@Nullable Slot slot) {
		if (slot == null || !(slot.container instanceof Inventory)) {
			return -1;
		}

		int index = slot.getContainerSlot();
		return index >= 0 && index < Inventory.INVENTORY_SIZE ? index : -1;
	}
}
