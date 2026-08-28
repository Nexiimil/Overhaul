package com.overhaul.module.quickstack;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Matches blocks and items against the config's id lists.
 *
 * <p>Entries are compared as written rather than resolved to registry objects up front, because
 * the lists are read after a config reload and may name blocks from a mod that is not installed;
 * an id that resolves to nothing simply never matches.
 */
final class Filters {
	private Filters() {
	}

	static boolean matches(BlockState state, List<String> entries) {
		for (String entry : entries) {
			if (entry.startsWith("#")) {
				if (state.is(TagKey.create(Registries.BLOCK, Identifier.parse(entry.substring(1))))) {
					return true;
				}

				continue;
			}

			if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(entry)) {
				return true;
			}
		}

		return false;
	}

	static boolean matches(ItemStack stack, List<String> entries) {
		for (String entry : entries) {
			if (entry.startsWith("#")) {
				if (stack.is(TagKey.create(Registries.ITEM, Identifier.parse(entry.substring(1))))) {
					return true;
				}

				continue;
			}

			if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(entry)) {
				return true;
			}
		}

		return false;
	}
}
