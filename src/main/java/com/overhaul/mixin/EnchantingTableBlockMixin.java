package com.overhaul.mixin;

import com.overhaul.module.magical.Bookshelves;
import com.overhaul.module.magical.MagicalModule;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes an empty bookshelf worth nothing to an enchanting table.
 *
 * <p>Once shelves start out empty this is what gives stocking them a point: the enchantment level
 * you can reach depends on how many shelves you have actually filled with books, not just on how
 * many wooden blocks you stacked around the table.
 */
@Mixin(EnchantingTableBlock.class)
public class EnchantingTableBlockMixin {
	@Inject(method = "isValidBookShelf", at = @At("RETURN"), cancellable = true)
	private static void overhaul$requireBooks(Level level, BlockPos pos, BlockPos offset, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() || !MagicalModule.bookshelvesEnabled()) {
			return;
		}

		int required = MagicalModule.booksForEnchantingPower();

		if (required <= 0) {
			return;
		}

		BlockPos shelf = pos.offset(offset);

		if (Bookshelves.isBookshelf(level, shelf) && Bookshelves.bookCount(level, shelf) < required) {
			cir.setReturnValue(false);
		}
	}
}
