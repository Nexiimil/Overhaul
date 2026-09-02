package com.overhaul.module.multiplayer;

import com.overhaul.core.Reg;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import org.jspecify.annotations.Nullable;

/** The one block this module owns. */
public final class MultiplayerContent {
	private static @Nullable Block chunkLoader;
	private static @Nullable Item chunkLoaderItem;

	private MultiplayerContent() {
	}

	public static @Nullable Block chunkLoader() {
		return chunkLoader;
	}

	public static @Nullable Item chunkLoaderItem() {
		return chunkLoaderItem;
	}

	public static void register() {
		// Obsidian's own properties, because it is mostly obsidian: blast proof, slow to mine, and
		// not something a creeper or a piston is going to relocate. A loader that could be pushed
		// would be a chunk hold that could be moved by anyone with a sticky piston.
		Block block = Reg.block("chunk_loader", ChunkLoaderBlock::new,
				BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
						.pushReaction(PushReaction.BLOCK));

		chunkLoader = block;
		chunkLoaderItem = Reg.item("chunk_loader",
				properties -> new BlockItem(block, properties.useBlockDescriptionPrefix()),
				new Item.Properties());
	}
}
