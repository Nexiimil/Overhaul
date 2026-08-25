package com.overhaul.module.backpack;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The live view of a backpack's contents while its screen is open.
 *
 * <p>Contents live on the item stack as the vanilla {@code CONTAINER} component, the same one
 * shulker boxes use, so a backpack keeps its items through anything that copies stacks — being
 * dropped, picked up, moved between inventories, or upgraded in a smithing table.
 *
 * <p>The container holds the stack instance rather than a slot index, so it keeps writing to the
 * right item even if the player shuffles their inventory while the screen is open.
 */
public class BackpackContainer extends SimpleContainer {
	private final ItemStack backpack;
	private final boolean allowNesting;

	public BackpackContainer(ItemStack backpack, int size, boolean allowNesting) {
		super(size);
		this.backpack = backpack;
		this.allowNesting = allowNesting;

		NonNullList<ItemStack> loaded = NonNullList.withSize(size, ItemStack.EMPTY);
		backpack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(loaded);

		for (int slot = 0; slot < size; slot++) {
			setItem(slot, loaded.get(slot));
		}
	}

	/**
	 * Items held past the end of the container, which only happens if a tier's row count was
	 * lowered in the config after the backpack was filled. The caller hands these back to the
	 * player instead of letting the next save drop them.
	 */
	public static List<ItemStack> overflow(ItemStack backpack, int size) {
		return backpack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
				.allItemsCopyStream()
				.skip(size)
				.filter(stack -> !stack.isEmpty())
				.toList();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (stack.isEmpty() || allowNesting) {
			return true;
		}

		// Nesting bags inside bags makes items very easy to lose track of, and lets a small tier
		// hold arbitrarily much. Vanilla applies the same rule to shulker boxes.
		return !(stack.getItem() instanceof BackpackItem);
	}

	@Override
	public boolean stillValid(Player player) {
		return !backpack.isEmpty() && backpack.getItem() instanceof BackpackItem;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		save();
	}

	private void save() {
		List<ItemStack> items = new ArrayList<>(getContainerSize());

		for (int slot = 0; slot < getContainerSize(); slot++) {
			items.add(getItem(slot));
		}

		backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
	}
}
