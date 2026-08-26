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
 * Reports a pinned moon phase in place of the real one.
 *
 * <p>This is the single point every reader of the moon passes through. The server's own
 * {@code getMoonBrightness} asks this class directly, and so does the client's interpolating probe,
 * which is what feeds the sky renderer and the clock item. Overriding here therefore keeps the moon
 * that is drawn and the moon that drives local difficulty in agreement — putting the override any
 * further down would let the two disagree.
 *
 * <p>The alternative was to hold the phase by winding the world clock back a day whenever it
 * drifted, which would have meant permanently rewinding world time. World age feeds local
 * difficulty, so that would have quietly suppressed hordes.
 *
 * <p>Both methods are generic over the attribute's value type and are called for every environment
 * attribute there is, so the guard is an identity comparison against one field and nothing else
 * happens on the common path.
 */
@Mixin(EnvironmentAttributeSystem.class)
public class EnvironmentAttributeSystemMixin {
	@Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
	private void overhaul$pinMoonPhase(EnvironmentAttribute<?> attribute, Vec3 position,
			SpatialAttributeInterpolator interpolator, CallbackInfoReturnable<Object> cir) {
		pin(attribute, cir);
	}

	@Inject(method = "getDimensionValue", at = @At("HEAD"), cancellable = true)
	private void overhaul$pinDimensionMoonPhase(EnvironmentAttribute<?> attribute, CallbackInfoReturnable<Object> cir) {
		pin(attribute, cir);
	}

	private static void pin(EnvironmentAttribute<?> attribute, CallbackInfoReturnable<Object> cir) {
		if (attribute != EnvironmentAttributes.MOON_PHASE) {
			return;
		}

		MoonPhase forced = MoonLock.forced();

		if (forced != null) {
			cir.setReturnValue(forced);
		}
	}
}
