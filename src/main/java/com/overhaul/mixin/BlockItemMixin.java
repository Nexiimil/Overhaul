package com.overhaul.mixin;

import com.overhaul.module.multiplayer.MultiplayerModule;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notices when a banner has actually been planted.
 *
 * <p>Every event Fabric offers for placing a block fires beforehand, and "was a banner placed" is a
 * question that can only be answered afterwards: the placement may still be refused by the block,
 * by the world border, or by the claim rules this very feature enforces. Reading the result of
 * {@code place} is the only point where the answer is known.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("RETURN"))
	private void overhaul$claimOnBannerPlaced(BlockPlaceContext context,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (!cir.getReturnValue().consumesAction()
				|| !(context.getPlayer() instanceof ServerPlayer player)
				|| !(context.getLevel() instanceof ServerLevel level)) {
			return;
		}

		BlockPos pos = context.getClickedPos();
		MultiplayerModule.onBannerPlaced(player, level, pos, level.getBlockState(pos));
	}
}
