package com.overhaul.module.inventory;

import java.util.Comparator;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * What a sort orders items by.
 *
 * <p>Names come from {@code getHoverName}, which the server resolves against its own language
 * file. That is deliberate: the sort happens server-side because the server owns the container,
 * so on a dedicated server everyone sees the same order rather than one per client locale.
 */
public enum SortMode {
	/** Purely by display name, so a modded copper wire files next to a vanilla copper ingot. */
	ALPHABETICAL,

	/** Vanilla items first, then each mod's items together, alphabetically within the group. */
	BY_MOD;

	private static final SortMode[] VALUES = values();

	private static final Comparator<ItemStack> BY_NAME =
			Comparator.<ItemStack, String>comparing(stack -> stack.getHoverName().getString(),
					String.CASE_INSENSITIVE_ORDER)
					// Two stacks of the same item with different components share a display name;
					// ordering them by id and then by size keeps the result stable between sorts.
					.thenComparing(stack -> key(stack).toString())
					.thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());

	private static final Comparator<ItemStack> BY_NAMESPACE =
			Comparator.<ItemStack>comparingInt(stack -> key(stack).getNamespace().equals("minecraft") ? 0 : 1)
					.thenComparing(stack -> key(stack).getNamespace())
					.thenComparing(BY_NAME);

	public static SortMode byIndex(int index) {
		return VALUES[Math.floorMod(index, VALUES.length)];
	}

	public SortMode next() {
		return byIndex(ordinal() + 1);
	}

	public Comparator<ItemStack> comparator() {
		return switch (this) {
			case ALPHABETICAL -> BY_NAME;
			case BY_MOD -> BY_NAMESPACE;
		};
	}

	private static Identifier key(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem());
	}
}
