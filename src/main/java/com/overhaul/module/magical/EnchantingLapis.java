package com.overhaul.module.magical;

import com.overhaul.Overhaul;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * The lapis an enchanting table is holding on to.
 *
 * <p>Vanilla hands the lapis back every time the screen closes, so a table you use regularly means
 * carrying lapis to it and away from it on every visit. Letting the table keep what you gave it
 * makes it a station you stock once, the same shape as filling a bookshelf.
 *
 * <p>The stack hangs off the table's own block entity as a data attachment. Vanilla already gives
 * the enchanting table a block entity for the book animation, so there is nothing to create — and
 * hanging it there rather than on the chunk means a table destroyed by something that leaves no
 * chance to react takes its lapis with it, instead of leaving an orphaned stack in the chunk for
 * the next table built on that spot to inherit.
 *
 * <p>The menu takes the stack out when it opens and puts it back when it closes, rather than
 * reading it in place. Two players can have the same table open at once, and a table that handed
 * the same lapis to both of them would be duplicating it.
 */
public final class EnchantingLapis {
	public static final AttachmentType<ItemStack> STORED = AttachmentRegistry.create(
			Overhaul.id("enchanting_lapis"), builder -> builder
					.persistent(ItemStack.OPTIONAL_CODEC)
					.initializer(() -> ItemStack.EMPTY));

	private EnchantingLapis() {
	}

	/** Forces class initialisation, which is what registers the attachment type. */
	public static void init() {
	}

	/** Takes what the table is holding, leaving it empty. */
	public static ItemStack take(Level level, BlockPos pos) {
		return takeFrom(level.getBlockEntity(pos));
	}

	/** Takes what a table block entity is holding, for when the block itself is already gone. */
	public static ItemStack takeFrom(@Nullable BlockEntity table) {
		if (!(table instanceof EnchantingTableBlockEntity)) {
			return ItemStack.EMPTY;
		}

		ItemStack stored = table.getAttached(STORED);

		if (stored == null || stored.isEmpty()) {
			return ItemStack.EMPTY;
		}

		table.setAttached(STORED, ItemStack.EMPTY);
		table.setChanged();
		return stored;
	}

	/**
	 * Hands the table something to hold.
	 *
	 * @return false if there is no longer a table here, in which case the caller still owns the
	 *     stack — a table broken while its screen was open must not swallow what was in it
	 */
	public static boolean store(Level level, BlockPos pos, ItemStack lapis) {
		if (!(level.getBlockEntity(pos) instanceof EnchantingTableBlockEntity table)) {
			return false;
		}

		table.setAttached(STORED, lapis.copy());
		table.setChanged();
		return true;
	}
}
