package com.overhaul.module.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Rearranges a run of a container's slots in place.
 *
 * <p>The sort runs on the server because the server owns the container. A client that reordered
 * its own view would have to be trusted with the result, and everyone else looking into the same
 * chest would still see the old arrangement until it resynced.
 */
final class ContainerSort {
	/** Chests, barrels, shulker boxes, backpacks and the player's inventory are all nine wide. */
	private static final int WIDTH = 9;

	private ContainerSort() {
	}

	/**
	 * Merges partial stacks, orders what is left and writes it back.
	 *
	 * <p>Skipped slots keep whatever they hold, and the fill order is still worked out across the
	 * whole grid rather than across what is left of it. That is what keeps a locked slot from
	 * shunting every item after it one place over and pulling the columns out of line.
	 *
	 * @param from first slot to sort, inclusive
	 * @param to last slot to sort, exclusive
	 * @param skip slots to leave exactly as they are
	 */
	static void sort(Container container, int from, int to, SortMode mode, FillOrder order, IntPredicate skip) {
		int start = Math.max(0, from);
		int end = Math.min(to, container.getContainerSize());
		int size = end - start;

		if (size <= 1) {
			return;
		}

		List<ItemStack> stacks = new ArrayList<>(size);

		for (int slot = start; slot < end; slot++) {
			ItemStack stack = container.getItem(slot);

			if (!skip.test(slot) && !stack.isEmpty()) {
				stacks.add(stack.copy());
			}
		}

		merge(container, stacks);
		stacks.sort(mode.comparator());

		int next = 0;

		for (int index = 0; index < size; index++) {
			int slot = start + order.slotFor(index, size, WIDTH);

			if (skip.test(slot)) {
				continue;
			}

			container.setItem(slot, next < stacks.size() ? stacks.get(next++) : ItemStack.EMPTY);
		}

		container.setChanged();
	}

	/**
	 * Tops up partial stacks from later ones. Sorting without this leaves three half stacks of
	 * cobblestone sitting next to each other, which is the mess the sort was meant to clear up.
	 */
	private static void merge(Container container, List<ItemStack> stacks) {
		for (int index = 0; index < stacks.size(); index++) {
			ItemStack into = stacks.get(index);
			int limit = container.getMaxStackSize(into);

			for (int other = index + 1; other < stacks.size() && into.getCount() < limit; other++) {
				ItemStack rest = stacks.get(other);

				if (!ItemStack.isSameItemSameComponents(into, rest)) {
					continue;
				}

				int taken = Math.min(limit - into.getCount(), rest.getCount());
				into.grow(taken);
				rest.shrink(taken);

				if (rest.isEmpty()) {
					stacks.remove(other);
					other--;
				}
			}
		}
	}
}
