package com.overhaul.module.mob;

import java.util.List;

import com.overhaul.module.mob.MobConfig.DispenserSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A dispenser full of wheat, pointed at a pen, does what a player standing there would.
 *
 * <p>Automated farms are the one part of animal husbandry that vanilla leaves entirely manual, and
 * the reason is a gap rather than a decision: the dispenser already has a facing, an inventory and
 * a redstone trigger, and feeding is the only thing you can do with food that it cannot do.
 *
 * <p>What counts as food is asked of the animal rather than listed here. {@code isFood} is the
 * same method the player's own right-click consults, so a modded animal that eats a modded berry
 * works without either of them being known to this mod.
 */
public final class DispenserFeeding {
	private DispenserFeeding() {
	}

	/**
	 * @return true if an animal was fed, in which case the dispenser has done its job for this
	 *     pulse and should not also fire whatever else it was holding
	 */
	public static boolean tryFeed(ServerLevel level, BlockState state, BlockPos pos, DispenserSettings settings) {
		if (!settings.feedAnimals) {
			return false;
		}

		if (!(level.getBlockEntity(pos) instanceof DispenserBlockEntity dispenser)) {
			return false;
		}

		Direction facing = state.getValue(DispenserBlock.FACING);
		List<Animal> animals = level.getEntitiesOfClass(Animal.class, reach(pos, facing, settings.feedRange));

		if (animals.isEmpty()) {
			return false;
		}

		for (int slot = 0; slot < dispenser.getContainerSize(); slot++) {
			ItemStack stack = dispenser.getItem(slot);

			if (stack.isEmpty()) {
				continue;
			}

			for (Animal animal : animals) {
				if (feed(level, animal, stack)) {
					stack.shrink(1);
					dispenser.setItem(slot, stack);
					level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 1.0F, 1.0F);
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * The block in front, stretched out by however far the dispenser is allowed to reach.
	 *
	 * <p>Deflated slightly so that an animal standing in the next pen over, flush against the far
	 * face, is not counted — a fence line should still mean something.
	 */
	private static AABB reach(BlockPos pos, Direction facing, int range) {
		BlockPos front = pos.relative(facing);
		BlockPos far = pos.relative(facing, Math.max(1, range));
		return new AABB(front).minmax(new AABB(far)).inflate(0.25).deflate(0.1);
	}

	/**
	 * Feeds one animal, if this is food it currently wants.
	 *
	 * <p>An adult that can breed goes into love mode and a baby grows, which between them is
	 * everything feeding does that a farm cares about. An animal that is neither — one already in
	 * love, or on breeding cooldown — is passed over rather than fed for nothing, so a dispenser
	 * pointed at a full pen stops consuming wheat instead of quietly eating a double chest of it.
	 */
	private static boolean feed(ServerLevel level, Animal animal, ItemStack stack) {
		if (!animal.isAlive() || !animal.isFood(stack)) {
			return false;
		}

		if (animal.isBaby()) {
			if (!animal.canAgeUp()) {
				return false;
			}

			animal.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-animal.getAge()), true);
			level.broadcastEntityEvent(animal, (byte) 18);
			return true;
		}

		if (animal.getAge() != 0 || !animal.canFallInLove()) {
			return false;
		}

		animal.setInLove(null);
		return true;
	}
}
