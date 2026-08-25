package com.overhaul.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes a mob's goal list so behaviour can be added to vanilla mobs from outside their class.
 *
 * <p>Adding goals is how the mob module changes vanilla AI without rewriting any of it: the
 * existing goals stay exactly as they are and the new one competes with them on priority.
 */
@Mixin(Mob.class)
public interface MobGoalAccess {
	@Accessor("goalSelector")
	GoalSelector overhaul$goalSelector();

	@Accessor("targetSelector")
	GoalSelector overhaul$targetSelector();
}
