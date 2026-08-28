package com.overhaul.client.inventory;

import com.overhaul.module.inventory.TrashSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The bin, drawn as a slot rather than a button.
 *
 * <p>A slot because that is what it behaves like: it holds one item, you put things in it by
 * clicking with something on the cursor and take them back by clicking with an empty one. A button
 * labelled "Trash" would have described the same actions while hiding the one thing worth seeing,
 * which is what is currently in there and about to be lost.
 *
 * <p>It is not a real menu slot. Adding one would mean reaching into every container menu in the
 * game to renumber its slots, which vanilla and every other mod would have opinions about. Drawing
 * a slot and asking the server to do the swap gets the same behaviour without touching any of that.
 */
final class TrashSlotWidget extends AbstractWidget {
	static final int SIZE = 18;

	// Vanilla's slot bevel: dark on the top and left, light on the bottom and right.
	private static final int BACKGROUND = 0xFF8B8B8B;
	private static final int SHADOW = 0xFF373737;
	private static final int HIGHLIGHT = 0xFFFFFFFF;
	private static final int HOVER = 0x80FFFFFF;

	private final Runnable onPress;

	TrashSlotWidget(Runnable onPress) {
		super(0, 0, SIZE, SIZE, Component.translatable("button.overhaul.trash"));
		this.onPress = onPress;
		setTooltip(Tooltip.create(Component.translatable("tooltip.overhaul.trash")));
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		onPress.run();
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = getX();
		int y = getY();

		graphics.fill(x, y, x + SIZE, y + SIZE, BACKGROUND);
		graphics.fill(x, y, x + SIZE - 1, y + 1, SHADOW);
		graphics.fill(x, y, x + 1, y + SIZE - 1, SHADOW);
		graphics.fill(x + 1, y + SIZE - 1, x + SIZE, y + SIZE, HIGHLIGHT);
		graphics.fill(x + SIZE - 1, y + 1, x + SIZE, y + SIZE, HIGHLIGHT);

		ItemStack held = contents();

		if (!held.isEmpty()) {
			graphics.item(held, x + 1, y + 1);
			graphics.itemDecorations(Minecraft.getInstance().font, held, x + 1, y + 1);
		}

		if (isHovered()) {
			graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, HOVER);
		}
	}

	private static ItemStack contents() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? ItemStack.EMPTY : TrashSlot.contents(player);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, createNarrationMessage());
	}
}
