package com.overhaul.core;

import java.util.function.Function;

import com.overhaul.Overhaul;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Registration helpers that mirror what {@code Items.registerItem} and {@code Blocks.register} do
 * internally, including the {@code setId} call that every item and block needs before it is
 * handed to a registry.
 */
public final class Reg {
	private Reg() {
	}

	public static ResourceKey<Item> itemKey(String path) {
		return ResourceKey.create(Registries.ITEM, Overhaul.id(path));
	}

	public static ResourceKey<Block> blockKey(String path) {
		return ResourceKey.create(Registries.BLOCK, Overhaul.id(path));
	}

	public static Item item(String path, Item.Properties properties) {
		return item(path, Item::new, properties);
	}

	public static Item item(String path, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = itemKey(path);
		Item item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static Block block(String path, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = blockKey(path);
		Block block = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	public static Identifier id(String path) {
		return Overhaul.id(path);
	}
}
