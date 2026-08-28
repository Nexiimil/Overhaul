package com.overhaul.module.inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntPredicate;

import com.overhaul.core.CarriedContainer;
import com.overhaul.core.OverhaulModule;
import com.overhaul.core.config.ConfigManager;
import com.overhaul.module.backpack.BackpackItem;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jspecify.annotations.Nullable;

/**
 * Handling items in bulk: quick-stacking into the containers around you, sorting the one in front
 * of you, marking slots to be left alone, throwing something away, and opening a container item
 * where it sits rather than placing it down first.
 *
 * <p>All of it is driven from the client — buttons on any nine-wide container screen, and keys for
 * the rest — and none of it is decided there. The client says "quick-stack from my inventory" or
 * "lock the slot I am pointing at"; the server works out what that means against its own record of
 * what the player has open and where they are standing, so nothing a modified client sends can
 * reach a container the player is not at or a slot they do not own.
 */
public class InventoryModule implements OverhaulModule {
	private static @Nullable InventoryConfig config;

	/**
	 * Last tick each player quick-stacked on, so a client holding the key down cannot make the
	 * server rescan its surroundings sixty times a second. Nothing else here needs an equivalent:
	 * the rest touch one container the player already has open, or one slot.
	 */
	private final Map<UUID, Long> lastQuickStack = new HashMap<>();

	private final Trash trash = new Trash();

	@Override
	public String id() {
		return "inventory";
	}

	@Override
	public String displayName() {
		return "Inventory Module";
	}

	@Override
	public void loadConfig() {
		config = ConfigManager.load(id(), InventoryConfig.class);
	}

	@Override
	public void registerContent() {
		SlotLocks.init();
	}

	@Override
	public void registerBehaviour() {
		PayloadTypeRegistry.serverboundPlay().register(QuickStackPayload.TYPE, QuickStackPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SortPayload.TYPE, SortPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TrashPayload.TYPE, TrashPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(ToggleSlotLockPayload.TYPE, ToggleSlotLockPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(OpenCarriedPayload.TYPE, OpenCarriedPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(InventorySettingsPayload.TYPE, InventorySettingsPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(QuickStackPayload.TYPE,
				(payload, context) -> onQuickStack(payload, context.player()));
		ServerPlayNetworking.registerGlobalReceiver(SortPayload.TYPE,
				(payload, context) -> onSort(payload, context.player()));
		ServerPlayNetworking.registerGlobalReceiver(TrashPayload.TYPE,
				(payload, context) -> onTrash(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(ToggleSlotLockPayload.TYPE,
				(payload, context) -> onToggleSlotLock(payload, context.player()));
		ServerPlayNetworking.registerGlobalReceiver(OpenCarriedPayload.TYPE,
				(payload, context) -> onOpenCarried(payload, context.player()));

		registerShulkerUse();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendSettings(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			lastQuickStack.remove(handler.getPlayer().getUUID());
			trash.forget(handler.getPlayer());
		});
	}

	private void sendSettings(ServerPlayer player) {
		InventoryConfig settings = config;

		if (settings == null || !ServerPlayNetworking.canSend(player, InventorySettingsPayload.TYPE)) {
			return;
		}

		boolean buttons = settings.buttons.enabled;

		ServerPlayNetworking.send(player, new InventorySettingsPayload(
				buttons && settings.quickStackEnabled,
				buttons && settings.sortEnabled,
				buttons && settings.trashEnabled,
				settings.lockSlotsEnabled,
				buttons && settings.buttons.inPlayerInventory));
	}

	// Quick-stacking ------------------------------------------------------------------------------

	private void onQuickStack(QuickStackPayload payload, ServerPlayer player) {
		InventoryConfig settings = config;

		if (settings == null || !settings.quickStackEnabled || onCooldown(player, settings)) {
			return;
		}

		Container source;
		IntPredicate skip;
		int from;
		int to;

		if (payload.fromOpenContainer()) {
			// Any nine-wide container the player has open, which is a chest, a shulker box or a
			// backpack. The menu is the server's own record of what they opened, so there is
			// nothing here to validate.
			if (!(player.containerMenu instanceof ChestMenu menu)) {
				return;
			}

			source = menu.getContainer();
			skip = SlotLocks.NONE;
			from = 0;
			to = source.getContainerSize();
		} else {
			source = player.getInventory();
			skip = SlotLocks.lockedIn(player);

			// Never the hotbar. What is on it was put there on purpose, and a quick-stack that
			// emptied it would take the player's tools along with the cobblestone.
			from = Inventory.SELECTION_SIZE;
			to = Inventory.INVENTORY_SIZE;
		}

		int moved = QuickStack.run(player, source, from, to, skip, settings);
		player.containerMenu.broadcastFullState();
		report(player, moved);
	}

	private boolean onCooldown(ServerPlayer player, InventoryConfig settings) {
		if (settings.cooldownTicks <= 0) {
			return false;
		}

		long now = player.level().getGameTime();
		Long last = lastQuickStack.get(player.getUUID());

		if (last != null && now - last < settings.cooldownTicks) {
			return true;
		}

		lastQuickStack.put(player.getUUID(), now);
		return false;
	}

	private static void report(ServerPlayer player, int moved) {
		if (moved <= 0) {
			player.sendSystemMessage(Component.translatable("message.overhaul.quickstack.nothing"), true);
			return;
		}

		player.sendSystemMessage(Component.translatable("message.overhaul.quickstack.moved", moved), true);
		click(player, 1.2F);
	}

	// Sorting -------------------------------------------------------------------------------------

	private void onSort(SortPayload payload, ServerPlayer player) {
		InventoryConfig settings = config;

		if (settings == null || !settings.sortEnabled) {
			return;
		}

		if (payload.playerInventory()) {
			// The three main rows only, and not the slots the player has locked.
			ContainerSort.sort(player.getInventory(), Inventory.SELECTION_SIZE, Inventory.INVENTORY_SIZE,
					payload.mode(), payload.order(), SlotLocks.lockedIn(player));
		} else if (player.containerMenu instanceof ChestMenu menu) {
			Container container = menu.getContainer();
			ContainerSort.sort(container, 0, container.getContainerSize(),
					payload.mode(), payload.order(), SlotLocks.NONE);
		} else {
			return;
		}

		player.containerMenu.broadcastFullState();
		click(player, 1.6F);
	}

	// Locked slots --------------------------------------------------------------------------------

	private static void onToggleSlotLock(ToggleSlotLockPayload payload, ServerPlayer player) {
		InventoryConfig settings = config;

		if (settings == null || !settings.lockSlotsEnabled || !SlotLocks.lockable(payload.slot())) {
			return;
		}

		// The attachment syncs itself back to this player, so flipping the bit is the whole of the
		// reply; the client redraws from what it is sent rather than from what it guessed.
		click(player, SlotLocks.toggle(player, payload.slot()) ? 1.8F : 1.0F);
	}

	// Trash ---------------------------------------------------------------------------------------

	private void onTrash(ServerPlayer player) {
		InventoryConfig settings = config;

		if (settings == null || !settings.trashEnabled) {
			return;
		}

		Component message = trash.press(player);

		if (message == null) {
			return;
		}

		player.containerMenu.broadcastFullState();
		player.sendSystemMessage(message, true);
		click(player, 0.8F);
	}

	// Opening a container item in place -------------------------------------------------------------

	/**
	 * Using a shulker box in the air opens it. Placement is untouched: that goes through a
	 * different interaction, so aiming at a block still puts the box down.
	 */
	private void registerShulkerUse() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			InventoryConfig settings = config;
			ItemStack held = player.getItemInHand(hand);

			if (settings == null || !settings.openShulkerBoxes || !isShulkerBox(held)) {
				return InteractionResult.PASS;
			}

			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}

			openShulkerBox(player, held);
			return InteractionResult.CONSUME;
		});
	}

	private static void onOpenCarried(OpenCarriedPayload payload, ServerPlayer player) {
		InventoryConfig settings = config;

		if (settings == null) {
			return;
		}

		int slot = payload.slot();

		if (slot < 0 || slot >= Inventory.INVENTORY_SIZE) {
			return;
		}

		ItemStack stack = player.getInventory().getItem(slot);

		// A backpack already knows how to open itself, and does so whether or not this module is
		// the one asking; only the shulker box is this module's own addition.
		if (stack.getItem() instanceof BackpackItem) {
			BackpackItem.open(player, stack);
		} else if (settings.openShulkerBoxes && isShulkerBox(stack)) {
			openShulkerBox(player, stack);
		}
	}

	private static void openShulkerBox(Player player, ItemStack stack) {
		// Three rows, and no shulker box inside a shulker box — the same rule the placed block
		// enforces, so opening one in place behaves exactly like opening the block would.
		CarriedContainer.open(player, stack, 3, held -> !isShulkerBox(held));
	}

	private static boolean isShulkerBox(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock;
	}

	private static void click(ServerPlayer player, float pitch) {
		player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT,
				SoundSource.PLAYERS, 0.5F, pitch);
	}
}
