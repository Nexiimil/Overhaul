package com.overhaul.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes where a container screen has placed its panel, so widgets can be hung off the edge of it.
 *
 * <p>These are protected fields meant for subclasses, and the screens we want to add buttons to
 * are vanilla's own. An accessor is the whole of the change: nothing about how the screen draws
 * or behaves is touched.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccess {
	@Accessor("leftPos")
	int overhaul$leftPos();

	@Accessor("topPos")
	int overhaul$topPos();

	@Accessor("imageWidth")
	int overhaul$imageWidth();

	@Accessor("imageHeight")
	int overhaul$imageHeight();

	@Accessor("hoveredSlot")
	@Nullable Slot overhaul$hoveredSlot();
}
