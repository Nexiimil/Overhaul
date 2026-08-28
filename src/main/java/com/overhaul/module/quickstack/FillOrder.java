package com.overhaul.module.quickstack;

/**
 * Which way a sorted run of items is laid out across a container's grid.
 *
 * <p>Horizontal is the order the slots are already numbered in, so it costs nothing. Vertical
 * transposes the grid, which reads better when you are scanning a tall backpack for one item.
 */
public enum FillOrder {
	HORIZONTAL,
	VERTICAL;

	private static final FillOrder[] VALUES = values();

	public static FillOrder byIndex(int index) {
		return VALUES[Math.floorMod(index, VALUES.length)];
	}

	public FillOrder next() {
		return byIndex(ordinal() + 1);
	}

	/**
	 * The slot the item at {@code index} of the sorted run belongs in.
	 *
	 * <p>Falls back to the plain slot order for any grid the transpose does not divide evenly,
	 * which keeps a modded container of an odd size sortable rather than scrambled.
	 */
	public int slotFor(int index, int size, int width) {
		if (this == HORIZONTAL || width <= 1 || size % width != 0) {
			return index;
		}

		int rows = size / width;
		return index % rows * width + index / rows;
	}
}
