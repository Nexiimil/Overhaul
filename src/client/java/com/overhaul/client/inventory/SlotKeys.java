package com.overhaul.client.inventory;

import com.overhaul.module.inventory.SlotLocks;
import com.overhaul.module.inventory.ToggleSlotLockPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;

/**
 * The key that locks whichever slot the cursor is over, and the marks it leaves.
 *
 * <p>A key rather than a click, because every mouse button in a container screen already means
 * something and taking one over would cost more than it gave. Pointing at a slot and pressing a
 * key adds a gesture instead of overloading one.
 *
 * <p>The lock marks are drawn from the bitmask the server syncs to its owner, not from anything
 * this class remembers. A press asks the server to flip a bit and the mark appears when the answer
 * arrives, so what is drawn is always what will actually be respected.
 */
public final class SlotKeys {
	private static final int FILL = 0x30FFB020;
	private static final int OUTLINE = 0xC0FFB020;
	private static final int SLOT = 16;

	private SlotKeys() {
	}

	public static void register(KeyMapping toggleLock) {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container) || !InventoryScreens.handles(screen)) {
				return;
			}

			ScreenKeyboardEvents.afterKeyPress(screen)
					.register((current, event) -> onKeyPress(container, event, toggleLock));

			// Drawn after the screen is done rather than among its own layers, so the marks land
			// over the items they describe and in the screen's own coordinates.
			ScreenEvents.afterExtract(screen)
					.register((current, graphics, mouseX, mouseY, tickProgress) -> drawLocks(container, graphics));
		});
	}

	private static void onKeyPress(AbstractContainerScreen<?> screen, KeyEvent event, KeyMapping toggleLock) {
		int slot = InventoryScreens.playerSlot(InventoryScreens.hovered(screen));

		if (slot < 0 || !toggleLock.matches(event) || !ClientPlayNetworking.canSend(ToggleSlotLockPayload.TYPE)) {
			return;
		}

		ClientPlayNetworking.send(new ToggleSlotLockPayload(slot));
	}

	private static void drawLocks(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		long mask = SlotLocks.maskOf(player);

		if (mask == 0L) {
			return;
		}

		int left = InventoryScreens.left(screen);
		int top = InventoryScreens.top(screen);

		for (Slot slot : screen.getMenu().slots) {
			int index = InventoryScreens.playerSlot(slot);

			if (index < 0 || (mask & 1L << index) == 0L) {
				continue;
			}

			int x = left + slot.x;
			int y = top + slot.y;

			graphics.fill(x, y, x + SLOT, y + SLOT, FILL);
			graphics.outline(x, y, SLOT, SLOT, OUTLINE);
		}
	}
}
