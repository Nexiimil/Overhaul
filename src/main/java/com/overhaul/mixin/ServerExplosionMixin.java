package com.overhaul.mixin;

import java.util.ArrayList;
import java.util.List;

import com.overhaul.module.multiplayer.Protection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Takes claimed chunks out of an explosion's list before it acts on it.
 *
 * <p>Every other rule in the claims module asks who is doing something. An explosion is the one
 * thing that changes a claim with nobody to ask: whoever lit the fuse is long out of the picture
 * by the time the blocks go, and a creeper never had an opinion. Filtering the block list is the
 * narrowest point that covers all of it — TNT, creepers, beds, end crystals and anything a mod
 * detonates — because they all arrive here with a list of positions.
 *
 * <p>Entities caught in the blast are left alone. A claim is about the land, and an explosion that
 * still hurt was the point of standing near it.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Shadow
	public abstract ServerLevel level();

	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
	private void overhaul$spareClaimedChunks(CallbackInfoReturnable<List<BlockPos>> cir) {
		List<BlockPos> exploded = cir.getReturnValue();

		if (exploded.isEmpty()) {
			return;
		}

		ServerLevel level = level();
		// Mutable, and it has to be: the caller shuffles this list in place before acting on it.
		List<BlockPos> spared = new ArrayList<>(exploded.size());

		for (BlockPos pos : exploded) {
			if (Protection.explosionMayBreak(level, pos)) {
				spared.add(pos);
			}
		}

		// Only replace the list when something was actually taken out of it, so an explosion in the
		// wilderness — which is nearly all of them — costs one pass and no allocation beyond it.
		if (spared.size() != exploded.size()) {
			cir.setReturnValue(spared);
		}
	}
}
