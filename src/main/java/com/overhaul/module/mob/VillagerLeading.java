package com.overhaul.module.mob;

import com.overhaul.module.mob.MobConfig.VillagerSettings;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Villagers follow a held emerald, the way a cow follows wheat.
 *
 * <p>Moving a villager is currently a job of boats, rails and minecarts, and none of that is a
 * mechanic so much as a workaround for the absence of one. Every other mob in the game that you
 * might want to relocate has a food you can hold; villagers were only ever missing theirs, and an
 * emerald is the obvious candidate because it is already the thing they want.
 *
 * <p>Done through the brain's walk target rather than by adding a goal. Villagers are brain-driven
 * and their goal selector barely runs, so a {@code TemptGoal} would fight whatever the brain had
 * decided and produce a villager visibly torn between two destinations. Writing the walk target
 * after the brain has ticked simply overrides that decision for as long as the emerald is out.
 */
public final class VillagerLeading {
	private VillagerLeading() {
	}

	/** Called once per villager AI tick, after its own brain has had its turn. */
	public static void tick(Villager villager, ServerLevel level, VillagerSettings settings) {
		if (!settings.leadWithEmeralds || villager.isBaby() && !settings.leadBabies) {
			return;
		}

		// A villager mid-trade is being talked to, not led; yanking it away from its own trade
		// screen would close it.
		if (villager.getTradingPlayer() != null) {
			return;
		}

		Player leader = nearestLeader(villager, level, settings);

		if (leader == null) {
			return;
		}

		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(leader, true));

		// Close enough already: keep looking, but stop walking, so a led villager settles next to
		// you instead of shuffling into you forever.
		if (villager.distanceToSqr(leader) <= settings.leadStopDistance * settings.leadStopDistance) {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			return;
		}

		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
				new WalkTarget(new EntityTracker(leader, false), settings.leadSpeed, 1));
	}

	private static @Nullable Player nearestLeader(Villager villager, ServerLevel level, VillagerSettings settings) {
		Player nearest = level.getNearestPlayer(villager, settings.leadRange);

		if (nearest == null || nearest.isSpectator() || !holdsLure(nearest, settings)) {
			return null;
		}

		return nearest;
	}

	private static boolean holdsLure(Player player, VillagerSettings settings) {
		return isLure(player.getItemInHand(InteractionHand.MAIN_HAND), settings)
				|| isLure(player.getItemInHand(InteractionHand.OFF_HAND), settings);
	}

	private static boolean isLure(ItemStack stack, VillagerSettings settings) {
		if (stack.isEmpty()) {
			return false;
		}

		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return settings.leadItems.contains(id.toString());
	}
}
