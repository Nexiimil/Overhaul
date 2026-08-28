package com.overhaul.client.quickstack;

import java.util.ArrayList;
import java.util.List;

import com.overhaul.client.mixin.ContainerScreenAccess;
import com.overhaul.module.quickstack.FillOrder;
import com.overhaul.module.quickstack.QuickStackPayload;
import com.overhaul.module.quickstack.QuickStackSettingsPayload;
import com.overhaul.module.quickstack.SortMode;
import com.overhaul.module.quickstack.SortPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Draws the sort and quick-stack buttons alongside any nine-wide container screen.
 *
 * <p>One hook covers chests, barrels, shulker boxes and backpacks together, because they all use
 * vanilla's generic chest screen — the same reason the backpack module never needed a screen of
 * its own. The player's own inventory is handled beside them and differs only in which slots the
 * request names.
 *
 * <p>Which buttons appear is the server's decision, sent on join. A server without the mod, or
 * with the module switched off, sends nothing, so nothing is drawn.
 */
public final class ContainerButtons {
	private static final int WIDTH = 46;
	private static final int HEIGHT = 16;
	private static final int GAP = 4;

	private static boolean quickStack;
	private static boolean sort;
	private static boolean playerInventory;

	private ContainerButtons() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(QuickStackSettingsPayload.TYPE,
				(payload, context) -> context.client().execute(() -> apply(payload)));

		// Cleared on disconnect so buttons from one server do not linger into a single player
		// world where the module is switched off.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> apply(
				new QuickStackSettingsPayload(false, false, false)));

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> addTo(screen));
	}

	private static void apply(QuickStackSettingsPayload payload) {
		quickStack = payload.quickStack();
		sort = payload.sort();
		playerInventory = payload.playerInventory();
	}

	private static void addTo(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			return;
		}

		boolean ownInventory = screen instanceof InventoryScreen;

		// Anything else with a chest menu is a chest, a barrel, a shulker box or a backpack. A
		// furnace or a brewing stand has its own menu type and its own slot rules, and is left
		// alone rather than being sorted into a state it would refuse from a player.
		if (ownInventory ? !playerInventory : !(container.getMenu() instanceof ChestMenu)) {
			return;
		}

		List<AbstractWidget> buttons = new ArrayList<>(3);

		if (sort) {
			buttons.add(sortButton(ownInventory, true));
			buttons.add(sortButton(ownInventory, false));
		}

		if (quickStack) {
			buttons.add(Button.builder(Component.translatable("button.overhaul.quickstack"),
							press -> ClientPlayNetworking.send(new QuickStackPayload(!ownInventory)))
					.size(WIDTH, HEIGHT)
					.tooltip(Tooltip.create(Component.translatable("tooltip.overhaul.quickstack")))
					.build());
		}

		if (buttons.isEmpty()) {
			return;
		}

		Screens.getWidgets(screen).addAll(buttons);
		place(container, buttons);

		// The inventory screen shifts its panel sideways when the recipe book opens, without
		// running init again, so the buttons follow it every frame rather than only on open.
		ScreenEvents.beforeExtract(screen)
				.register((current, graphics, mouseX, mouseY, tickProgress) -> place(container, buttons));
	}

	/**
	 * One of the two toggles. Both change a preference and immediately sort with it, which is what
	 * makes them the whole interface: there is no separate "sort now", because setting the buttons
	 * to what you want is the same action as asking for it.
	 */
	private static Button sortButton(boolean ownInventory, boolean modeButton) {
		Button.OnPress press = button -> {
			button.setMessage(modeButton ? label(SortPrefs.cycleMode()) : label(SortPrefs.cycleOrder()));
			ClientPlayNetworking.send(new SortPayload(SortPrefs.mode(), SortPrefs.order(), ownInventory));
		};

		return Button.builder(modeButton ? label(SortPrefs.mode()) : label(SortPrefs.order()), press)
				.size(WIDTH, HEIGHT)
				.tooltip(Tooltip.create(Component.translatable(
						modeButton ? "tooltip.overhaul.sort.mode" : "tooltip.overhaul.sort.fill")))
				.build();
	}

	private static Component label(SortMode mode) {
		return Component.translatable(mode == SortMode.BY_MOD
				? "button.overhaul.sort.by_mod"
				: "button.overhaul.sort.alphabetical");
	}

	private static Component label(FillOrder order) {
		return Component.translatable(order == FillOrder.VERTICAL
				? "button.overhaul.fill.vertical"
				: "button.overhaul.fill.horizontal");
	}

	/** Stacks the buttons down the right of the panel, or its left if the window is too narrow. */
	private static void place(AbstractContainerScreen<?> screen, List<AbstractWidget> buttons) {
		ContainerScreenAccess panel = (ContainerScreenAccess) screen;
		int right = panel.overhaul$leftPos() + panel.overhaul$imageWidth() + GAP;
		int x = right + WIDTH <= screen.width ? right : panel.overhaul$leftPos() - WIDTH - GAP;
		int y = panel.overhaul$topPos();

		for (AbstractWidget button : buttons) {
			button.setX(x);
			button.setY(y);
			y += HEIGHT + GAP;
		}
	}
}
