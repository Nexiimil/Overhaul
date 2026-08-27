package com.overhaul.mixin;

import com.overhaul.core.MoonLock;

import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.SpatialAttributeInterpolator;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports a pinned or rotated moon phase in place of the real one.
 *
 * <p>This is the single point every reader of the moon passes through. The server's own
 * {@code getMoonBrightness} asks this class, and so do the client probe behind the sky renderer,
 * the clock item, and the moon brightness check that gates mob variant spawns — the first three by
 * way of {@code EnvironmentAttributeReader}'s defaults, which funnel {@code (attribute, BlockPos)}
 * and {@code (attribute, Vec3)} down into the three argument {@code getValue} injected below.
 * Overriding here therefore keeps the moon that is drawn and the moon that drives local difficulty
 * in agreement — putting the override any further down would let the two disagree.
 *
 * <p>{@link MoonLock} has the argument for overriding the phase where it is read rather than
 * winding the world clock to it.
 *
 * <p>Injected at {@code RETURN} rather than {@code HEAD} because the rotation is relative: it
 * needs the phase vanilla was about to report. {@code getValue} and {@code getDimensionValue} read
 * their sampler independently — neither delegates to the other — so a value passes through exactly
 * one of these and is rotated once. That matters here in a way it would not for the pin, which is
 * idempotent; rotating twice would land a phase short.
 *
 * <p>Both methods are generic over the attribute's value type and are called for every environment
 * attribute there is, so the guard is an identity comparison against one field and nothing else
 * happens on the common path.
 */
@Mixin(EnvironmentAttributeSystem.class)
public class EnvironmentAttributeSystemMixin {
	@Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
	private void overhaul$bendMoonPhase(EnvironmentAttribute<?> attribute, Vec3 position,
			SpatialAttributeInterpolator interpolator, CallbackInfoReturnable<Object> cir) {
		bend(attribute, cir);
	}

	@Inject(method = "getDimensionValue", at = @At("RETURN"), cancellable = true)
	private void overhaul$bendDimensionMoonPhase(EnvironmentAttribute<?> attribute,
			CallbackInfoReturnable<Object> cir) {
		bend(attribute, cir);
	}

	private static void bend(EnvironmentAttribute<?> attribute, CallbackInfoReturnable<Object> cir) {
		if (attribute != EnvironmentAttributes.MOON_PHASE) {
			return;
		}

		if (!(cir.getReturnValue() instanceof MoonPhase real)) {
			return;
		}

		MoonPhase effective = MoonLock.apply(real);

		if (effective != real) {
			cir.setReturnValue(effective);
		}
	}
}
