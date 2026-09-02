package com.overhaul.mixin;

import java.util.List;

import com.overhaul.module.multiplayer.Protection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops a piston reaching into somebody else's claim.
 *
 * <p>A piston is the oldest way through a wall you are not allowed to break, and none of the claim
 * module's other rules see it: by the time it fires there is no player to ask about, only a
 * redstone pulse. So the question is asked about the two ends instead, and the answer has to be
 * that everything it touches belongs to whoever owns the chunk it is standing in.
 *
 * <p>{@code resolve} is the one place worth asking. It is where the piston works out what it would
 * move, it runs before anything has actually moved, and returning false from it is exactly how
 * vanilla already expresses "this push cannot happen".
 */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
	@Shadow
	@Final
	private Level level;

	@Shadow
	@Final
	private BlockPos pistonPos;

	@Shadow
	@Final
	private List<BlockPos> toPush;

	@Shadow
	@Final
	private List<BlockPos> toDestroy;

	@Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
	private void overhaul$keepPistonsInTheirOwnClaim(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() || !(level instanceof ServerLevel server)) {
			return;
		}

		if (!Protection.pistonMayMove(server, pistonPos, toPush, toDestroy)) {
			cir.setReturnValue(Boolean.FALSE);
		}
	}
}
