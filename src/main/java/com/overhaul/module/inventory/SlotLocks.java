package com.overhaul.module.inventory;

import java.util.function.IntPredicate;

import com.mojang.serialization.Codec;
import com.overhaul.Overhaul;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * Slots the player has marked to be left alone.
 *
 * <p>A lock only stops this mod: sorting and quick-stacking skip the slot, and everything else —
 * picking the item up, shift-clicking it, dropping it — works exactly as it did. That is the whole
 * feature. Locking a slot against the player's own mouse would mean fighting vanilla's click
 * handling for no gain, and the thing people actually want protected from is a bulk operation they
 * asked for without looking at what it would sweep up.
 *
 * <p>The state is a bitmask over the 36 inventory slots, carried as a data attachment on the
 * player. Fabric syncs it to its owner and no-one else, so the client can draw the marks without a
 * packet of this module's own, and it survives logout and death the way the inventory it describes
 * does.
 */
public final class SlotLocks {
	public static final AttachmentType<Long> LOCKED = AttachmentRegistry.create(
			Overhaul.id("locked_slots"), builder -> builder
					.persistent(Codec.LONG)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly())
					.initializer(() -> 0L));

	/** Nothing is skipped, for containers that have no locks to respect. */
	public static final IntPredicate NONE = slot -> false;

	private SlotLocks() {
	}

	/** Forces class initialisation, which is what registers the attachment type. */
	public static void init() {
	}

	public static long maskOf(Player player) {
		Long mask = player.getAttached(LOCKED);
		return mask == null ? 0L : mask;
	}

	public static boolean isLocked(Player player, int slot) {
		return lockable(slot) && (maskOf(player) & bit(slot)) != 0L;
	}

	/** Which of the player's slots a bulk operation must leave where they are. */
	public static IntPredicate lockedIn(Player player) {
		long mask = maskOf(player);
		return slot -> lockable(slot) && (mask & bit(slot)) != 0L;
	}

	/** @return the slot's new state, or false if it is not a slot that can be locked */
	public static boolean toggle(Player player, int slot) {
		if (!lockable(slot)) {
			return false;
		}

		long flipped = maskOf(player) ^ bit(slot);
		player.setAttached(LOCKED, flipped);
		return (flipped & bit(slot)) != 0L;
	}

	/**
	 * The hotbar is left out because it is already never sorted or emptied, so a lock on it would
	 * describe a protection the player already has and change nothing.
	 */
	public static boolean lockable(int slot) {
		return slot >= Inventory.SELECTION_SIZE && slot < Inventory.INVENTORY_SIZE;
	}

	private static long bit(int slot) {
		return 1L << slot;
	}
}
