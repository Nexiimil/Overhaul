package com.overhaul.module.magical;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.overhaul.Overhaul;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Storage for the books a player puts into a vanilla bookshelf.
 *
 * <p>The contents hang off the chunk as a Fabric data attachment rather than off a block entity.
 * Vanilla's bookshelf is a plain {@code Block}, and the only way to give it a block entity is to
 * make every block in the game an {@code EntityBlock} and then unpick the consequences — a very
 * large blast radius for a feature that stores six item stacks. A chunk attachment saves and loads
 * with the chunk, needs no mixins into block placement at all, and leaves vanilla bookshelves
 * exactly as they are for any other mod that touches them.
 */
public final class Bookshelves {
	private record Entry(BlockPos pos, List<ItemStack> books) {
		private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				ItemStack.CODEC.listOf().fieldOf("books").forGetter(Entry::books))
				.apply(instance, Entry::new));
	}

	private static final Codec<Map<BlockPos, List<ItemStack>>> CONTENTS_CODEC = Entry.CODEC.listOf().xmap(
			entries -> {
				Map<BlockPos, List<ItemStack>> map = new LinkedHashMap<>();
				entries.forEach(entry -> map.put(entry.pos(), new ArrayList<>(entry.books())));
				return map;
			},
			map -> map.entrySet().stream()
					.map(entry -> new Entry(entry.getKey(), List.copyOf(entry.getValue())))
					.toList());

	public static final AttachmentType<Map<BlockPos, List<ItemStack>>> CONTENTS =
			AttachmentRegistry.create(Overhaul.id("bookshelf_contents"), builder -> builder
					.persistent(CONTENTS_CODEC)
					.initializer(LinkedHashMap::new));

	private Bookshelves() {
	}

	/** Forces class initialisation, which is what registers the attachment type. */
	public static void init() {
	}

	public static boolean isBookshelf(LevelReader level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.BOOKSHELF);
	}

	public static List<ItemStack> booksAt(LevelReader level, BlockPos pos) {
		ChunkAccess chunk = level.getChunk(pos);
		Map<BlockPos, List<ItemStack>> contents = chunk.getAttached(CONTENTS);

		if (contents == null) {
			return List.of();
		}

		List<ItemStack> books = contents.get(pos.immutable());
		return books == null ? List.of() : books;
	}

	public static int bookCount(LevelReader level, BlockPos pos) {
		return booksAt(level, pos).size();
	}

	/** @return true if the book was stored */
	public static boolean insert(Level level, BlockPos pos, ItemStack book, int capacity) {
		ChunkAccess chunk = level.getChunk(pos);
		Map<BlockPos, List<ItemStack>> contents = mutableContents(chunk);
		List<ItemStack> books = contents.computeIfAbsent(pos.immutable(), key -> new ArrayList<>());

		if (books.size() >= capacity) {
			return false;
		}

		books.add(book);
		chunk.setAttached(CONTENTS, contents);
		chunk.markUnsaved();
		return true;
	}

	/** Removes and returns the most recently added book, or an empty stack if the shelf is bare. */
	public static ItemStack removeLast(Level level, BlockPos pos) {
		ChunkAccess chunk = level.getChunk(pos);
		Map<BlockPos, List<ItemStack>> contents = mutableContents(chunk);
		List<ItemStack> books = contents.get(pos.immutable());

		if (books == null || books.isEmpty()) {
			return ItemStack.EMPTY;
		}

		ItemStack removed = books.removeLast();

		if (books.isEmpty()) {
			contents.remove(pos.immutable());
		}

		chunk.setAttached(CONTENTS, contents);
		chunk.markUnsaved();
		return removed;
	}

	/** Clears a shelf and hands back whatever it held, for when the block is destroyed. */
	public static List<ItemStack> clear(Level level, BlockPos pos) {
		ChunkAccess chunk = level.getChunk(pos);
		Map<BlockPos, List<ItemStack>> contents = chunk.getAttached(CONTENTS);

		if (contents == null) {
			return List.of();
		}

		List<ItemStack> removed = contents.remove(pos.immutable());

		if (removed == null || removed.isEmpty()) {
			return List.of();
		}

		chunk.setAttached(CONTENTS, contents);
		chunk.markUnsaved();
		return removed;
	}

	public static boolean accepts(ItemStack stack, MagicalConfig.BookshelfSettings settings) {
		if (stack.isEmpty()) {
			return false;
		}

		Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());

		if (settings.acceptedItems.contains(id.toString())) {
			return true;
		}

		String acceptedTag = settings.acceptedTag;

		if (acceptedTag == null || acceptedTag.isBlank()) {
			return false;
		}

		TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(acceptedTag));
		return stack.is(tag);
	}

	private static Map<BlockPos, List<ItemStack>> mutableContents(ChunkAccess chunk) {
		Map<BlockPos, List<ItemStack>> contents = chunk.getAttached(CONTENTS);
		return contents == null ? new LinkedHashMap<>() : contents;
	}
}
