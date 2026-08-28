package com.overhaul.module.quickstack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.overhaul.module.backpack.BackpackItem;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Sends a container's contents to nearby containers that already hold the same items.
 *
 * <p>Each nearby container is read once into a set of the items it holds, and each source stack is
 * then a set lookup per container rather than a walk of every slot: the pairing costs
 * {@code slots + containers} rather than {@code slots * slots}. Because that is cheap enough to do
 * from scratch on every press, nothing is ever read from a stale snapshot — which matters, since a
 * hopper can empty a chest between one press and the next without anybody opening it.
 *
 * <p>Insertion goes through {@link HopperBlockEntity#addItem}, so a container's own rules about
 * what it will accept — a shulker box refusing another shulker box, most of all — are respected
 * without this having to know about any of them.
 */
final class QuickStack {
	/** Every container the search can reach is nine slots wide, and a hopper cannot be a target. */
	private static final Direction INSERT_FACE = Direction.UP;

	/**
	 * Ceiling on the configured radius. The search cost grows with the chunks it covers, and a
	 * pack that sets this to a thousand should get a large radius rather than a stalled server.
	 */
	private static final double MAX_RADIUS = 32.0;

	private QuickStack() {
	}

	/**
	 * Moves what it can and reports how many items left.
	 *
	 * @param from first source slot, inclusive
	 * @param to last source slot, exclusive
	 */
	static int run(ServerPlayer player, Container source, int from, int to, QuickStackConfig config) {
		ServerLevel level = player.level();
		double radius = Math.clamp(config.radius, 0.0, MAX_RADIUS);
		List<Container> targets = new ArrayList<>(
				NearbyContainers.around(level, player.position(), radius, config.containers));

		targets.removeIf(target -> aliases(source, target));

		if (targets.isEmpty()) {
			return 0;
		}

		// Snapshot what each container holds before anything moves, so a container only receives
		// items it already had. Otherwise the first stack of cobblestone would make every
		// subsequent one follow it into whichever chest happened to be nearest.
		List<Set<Item>> holdings = targets.stream().map(QuickStack::itemsIn).toList();
		int moved = 0;
		int end = Math.min(to, source.getContainerSize());

		for (int slot = Math.max(0, from); slot < end; slot++) {
			if (!movable(source.getItem(slot), config)) {
				continue;
			}

			moved += offer(source, slot, targets, holdings, config);
		}

		if (moved > 0) {
			source.setChanged();
		}

		return moved;
	}

	private static int offer(Container source, int slot, List<Container> targets,
			List<Set<Item>> holdings, QuickStackConfig config) {
		int moved = 0;

		for (int index = 0; index < targets.size(); index++) {
			ItemStack stack = source.getItem(slot);

			if (stack.isEmpty()) {
				break;
			}

			Container target = targets.get(index);

			if (!holds(target, holdings.get(index), stack, config)) {
				continue;
			}

			// addItem may shrink the stack it is handed and return that same instance, so the
			// count to compare against is taken before the call rather than after.
			ItemStack taken = source.removeItemNoUpdate(slot);
			int before = taken.getCount();
			ItemStack leftover = HopperBlockEntity.addItem(source, target, taken, INSERT_FACE);
			source.setItem(slot, leftover);
			moved += before - leftover.getCount();
		}

		return moved;
	}

	/** Whether this container already holds the item, which is the whole condition for moving it. */
	private static boolean holds(Container target, Set<Item> items, ItemStack stack, QuickStackConfig config) {
		if (!items.contains(stack.getItem())) {
			return false;
		}

		if (!config.matchComponents) {
			return true;
		}

		for (int slot = 0; slot < target.getContainerSize(); slot++) {
			if (ItemStack.isSameItemSameComponents(target.getItem(slot), stack)) {
				return true;
			}
		}

		return false;
	}

	private static boolean movable(ItemStack stack, QuickStackConfig config) {
		// A backpack is never sent away, whatever the config says: quick-stacking your own storage
		// into a chest empties your inventory into a bag you then have to go and fetch.
		return !stack.isEmpty()
				&& !(stack.getItem() instanceof BackpackItem)
				&& !Filters.matches(stack, config.neverStack);
	}

	private static Set<Item> itemsIn(Container container) {
		Set<Item> items = new HashSet<>();

		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);

			if (!stack.isEmpty()) {
				items.add(stack.getItem());
			}
		}

		return items;
	}

	/**
	 * Whether a target is the source seen from somewhere else, which is what quick-stacking from an
	 * open chest into the chests around it has to rule out.
	 *
	 * <p>Reference equality alone is not enough: a double chest hands out a fresh pair of views on
	 * the same two halves every time it is resolved, so the container the player has open is never
	 * the same object as the one the search finds. Both views do share their item stacks, though,
	 * and stacks are never interned, so finding one of the source's stacks by identity inside a
	 * target proves the two are the same storage.
	 */
	private static boolean aliases(Container source, Container target) {
		if (source == target) {
			return true;
		}

		for (int slot = 0; slot < source.getContainerSize(); slot++) {
			ItemStack probe = source.getItem(slot);

			if (probe.isEmpty()) {
				continue;
			}

			for (int other = 0; other < target.getContainerSize(); other++) {
				if (target.getItem(other) == probe) {
					return true;
				}
			}

			// One stack settles it; the rest would only repeat the same answer.
			return false;
		}

		return false;
	}
}
