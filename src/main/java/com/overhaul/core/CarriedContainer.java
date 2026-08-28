package com.overhaul.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The live view of a container item's contents while its screen is open.
 *
 * <p>Contents live on the item stack as the vanilla {@code CONTAINER} component, the same one
 * shulker boxes use, so the container keeps its items through anything that copies stacks — being
 * dropped, picked up, moved between inventories, or upgraded in a smithing table.
 *
 * <p>The container holds the stack instance rather than a slot index, so it keeps writing to the
 * right item even if the player shuffles their inventory while the screen is open.
 *
 * <p>Nothing here is specific to any one container item. A backpack and a shulker box differ only
 * in how many rows they have and in what they refuse to hold, which is the whole of what a caller
 * has to supply.
 */
public class CarriedContainer extends SimpleContainer {
	private final ItemStack carrier;
	private final Item carried;
	private final Predicate<ItemStack> accepts;

	public CarriedContainer(ItemStack carrier, int size, Predicate<ItemStack> accepts) {
		super(size);
		this.carrier = carrier;
		this.carried = carrier.getItem();
		this.accepts = accepts;

		NonNullList<ItemStack> loaded = NonNullList.withSize(size, ItemStack.EMPTY);
		carrier.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(loaded);

		for (int slot = 0; slot < size; slot++) {
			setItem(slot, loaded.get(slot));
		}
	}

	/**
	 * Opens a container item for the player, in a vanilla chest screen sized to fit.
	 *
	 * <p>One to six rows map exactly onto {@code GENERIC_9x1} through {@code GENERIC_9x6}, so there
	 * is no custom screen, no custom texture and nothing to keep in sync with resource pack changes.
	 */
	public static void open(Player player, ItemStack carrier, int rows, Predicate<ItemStack> accepts) {
		int bounded = Math.max(1, Math.min(6, rows));
		int size = bounded * 9;

		for (ItemStack lost : overflow(carrier, size)) {
			player.getInventory().placeItemBackInInventory(lost);
		}

		CarriedContainer container = new CarriedContainer(carrier, size, accepts);

		player.openMenu(new SimpleMenuProvider(
				(containerId, inventory, owner) -> new ChestMenu(
						menuTypeFor(bounded), containerId, inventory, container, bounded),
				carrier.getHoverName()));

		player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT,
				SoundSource.PLAYERS, 0.8F, 0.9F);
	}

	/**
	 * Items held past the end of the container, which only happens if the size was lowered after
	 * the container was filled. The caller hands these back to the player instead of letting the
	 * next save drop them.
	 */
	public static List<ItemStack> overflow(ItemStack carrier, int size) {
		return carrier.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
				.allItemsCopyStream()
				.skip(size)
				.filter(stack -> !stack.isEmpty())
				.toList();
	}

	private static MenuType<ChestMenu> menuTypeFor(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.isEmpty() || accepts.test(stack);
	}

	@Override
	public boolean stillValid(Player player) {
		// The stack can be moved around the inventory while the screen is open, but if it stops
		// being the item that was opened it is no longer the thing this container describes.
		return !carrier.isEmpty() && carrier.getItem() == carried;
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

		carrier.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
	}
}
