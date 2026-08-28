package com.overhaul.module.inventory;

import com.overhaul.Overhaul;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A bin that holds exactly one thing.
 *
 * <p>Vanilla's only way to destroy an item is to throw it on the ground and wait, or find lava, so
 * this is filling a real gap rather than saving a click. Clicking it with something on the cursor
 * puts that in the bin and destroys whatever was in there already; clicking it with an empty cursor
 * takes back what is in there.
 *
 * <p>Holding the last item is what makes the bin safe to click. A trash slot that destroyed on
 * contact would turn one misclick on an enchanted pickaxe into the whole argument against having
 * it, and because the slot shows what it holds, the thing you are about to lose is visible before
 * you lose it rather than a warning nobody reads.
 *
 * <p>The contents are a data attachment Fabric syncs to their owner and no-one else, which is what
 * lets the client draw the item without this module inventing a packet for it. They are saved with
 * the player, so a crash cannot be the thing that empties the bin — the recovery is worth nothing
 * if it only lasts as long as the session, since a crash is exactly when you find out you wanted
 * something back.
 *
 * <p>It deliberately does not survive death. Vanilla drops your inventory when you die, and a slot
 * that held one item through that would be a keep-inventory slot for whatever you cared about most
 * — bin the netherite sword before a risky fight, take it back afterwards. Saving through a
 * restart costs nothing because logging out does not drop anything.
 */
public final class TrashSlot {
	public static final AttachmentType<ItemStack> HELD = AttachmentRegistry.create(
			Overhaul.id("trashed_item"), builder -> builder
					.persistent(ItemStack.OPTIONAL_CODEC)
					.syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
					.initializer(() -> ItemStack.EMPTY));

	private TrashSlot() {
	}

	/** Forces class initialisation, which is what registers the attachment type. */
	public static void init() {
	}

	public static ItemStack contents(Player player) {
		ItemStack held = player.getAttached(HELD);
		return held == null ? ItemStack.EMPTY : held;
	}

	/** @return what to tell the player, or null if there was nothing to do */
	static @Nullable Component press(ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		ItemStack carried = menu.getCarried();

		if (!carried.isEmpty()) {
			// Copied because the menu's stack keeps being handled after this; the bin needs its own.
			player.setAttached(HELD, carried.copy());
			menu.setCarried(ItemStack.EMPTY);
			return Component.translatable("message.overhaul.trash.voided", carried.getCount(), carried.getHoverName());
		}

		ItemStack held = contents(player);

		if (held.isEmpty()) {
			return null;
		}

		player.setAttached(HELD, ItemStack.EMPTY);
		menu.setCarried(held);
		return Component.translatable("message.overhaul.trash.recovered", held.getHoverName());
	}
}
