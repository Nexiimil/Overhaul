package com.overhaul.module.mob;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Makes a wounded mob break off and run rather than fight to the death.
 *
 * <p>Applied to hostile and passive mobs alike, this changes the shape of most fights: a zombie
 * that turns and runs at half health has to be chased down or left alive to come back, and a
 * wounded animal is no longer a stationary target. The goal releases its hold after a while so a
 * mob that got away can rejoin the fight instead of fleeing forever.
 */
public class FleeWhenHurtGoal extends Goal {
	private static final int SEARCH_HORIZONTAL = 16;
	private static final int SEARCH_VERTICAL = 7;

	private final PathfinderMob mob;
	private final float healthFraction;
	private final double speedModifier;
	private final int duration;

	private double targetX;
	private double targetY;
	private double targetZ;
	private int ticksLeft;

	public FleeWhenHurtGoal(PathfinderMob mob, float healthFraction, double speedModifier, int duration) {
		this.mob = mob;
		this.healthFraction = healthFraction;
		this.speedModifier = speedModifier;
		this.duration = duration;
		setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (mob.getHealth() >= mob.getMaxHealth() * healthFraction) {
			return false;
		}

		LivingEntity threat = threat();

		if (threat == null || !threat.isAlive()) {
			return false;
		}

		Vec3 escape = DefaultRandomPos.getPosAway(mob, SEARCH_HORIZONTAL, SEARCH_VERTICAL, threat.position());

		if (escape == null) {
			return false;
		}

		targetX = escape.x;
		targetY = escape.y;
		targetZ = escape.z;
		return true;
	}

	@Override
	public void start() {
		ticksLeft = duration;
		mob.getNavigation().moveTo(targetX, targetY, targetZ, speedModifier);

		// Dropping the attack target is what stops the mob from turning straight back around; the
		// goal only controls movement, so without this it would flee and swing at the same time.
		mob.setTarget(null);
	}

	@Override
	public boolean canContinueToUse() {
		return ticksLeft > 0 && !mob.getNavigation().isDone();
	}

	@Override
	public void tick() {
		ticksLeft--;
	}

	@Override
	public void stop() {
		ticksLeft = 0;
		mob.getNavigation().stop();
	}

	private @Nullable LivingEntity threat() {
		LivingEntity attacker = mob.getLastHurtByMob();
		return attacker != null ? attacker : mob.getTarget();
	}
}
