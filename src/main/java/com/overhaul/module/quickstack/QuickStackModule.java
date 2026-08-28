package com.overhaul.module.quickstack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.overhaul.core.OverhaulModule;
import com.overhaul.core.config.ConfigManager;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import org.jspecify.annotations.Nullable;

/**
 * Quick-stacking into the containers around you, and sorting the one in front of you.
 *
 * <p>Both halves are driven from buttons the client draws on any nine-wide container screen, plus
 * a key for quick-stacking without opening anything. What each button does is decided here rather
 * than on the client: the client says "quick-stack from my inventory" and the server works out
 * what that means, so nothing a modified client sends can reach a container the player is not
 * standing at.
 */
public class QuickStackModule implements OverhaulModule {
	private static @Nullable QuickStackConfig config;

	/**
	 * Last tick each player quick-stacked on, so a client holding the key down cannot make the
	 * server rescan its surroundings sixty times a second. Sorting needs no equivalent: it touches
	 * one container the player already has open.
	 */
	private final Map<UUID, Long> lastQuickStack = new HashMap<>();

	@Override
	public String id() {
		return "quickstack";
	}

	@Override
	public String displayName() {
		return "Quick Stack Module";
	}

	@Override
	public void loadConfig() {
		config = ConfigManager.load(id(), QuickStackConfig.class);
	}

	@Override
	public void registerBehaviour() {
		PayloadTypeRegistry.serverboundPlay().register(QuickStackPayload.TYPE, QuickStackPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SortPayload.TYPE, SortPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
				.register(QuickStackSettingsPayload.TYPE, QuickStackSettingsPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(QuickStackPayload.TYPE,
				(payload, context) -> onQuickStack(payload, context.player()));
		ServerPlayNetworking.registerGlobalReceiver(SortPayload.TYPE,
				(payload, context) -> onSort(payload, context.player()));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendSettings(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> lastQuickStack.remove(handler.getPlayer().getUUID()));
	}

	private void sendSettings(ServerPlayer player) {
		QuickStackConfig settings = config;

		if (settings == null || !ServerPlayNetworking.canSend(player, QuickStackSettingsPayload.TYPE)) {
			return;
		}

		boolean buttons = settings.buttons.enabled;

		ServerPlayNetworking.send(player, new QuickStackSettingsPayload(
				buttons && settings.quickStackEnabled,
				buttons && settings.sortEnabled,
				buttons && settings.buttons.inPlayerInventory));
	}

	// Quick-stacking ------------------------------------------------------------------------------

	private void onQuickStack(QuickStackPayload payload, ServerPlayer player) {
		QuickStackConfig settings = config;

		if (settings == null || !settings.quickStackEnabled || onCooldown(player, settings)) {
			return;
		}

		Container source;
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
			from = 0;
			to = source.getContainerSize();
		} else {
			source = player.getInventory();

			// Never the hotbar. What is on it was put there on purpose, and a quick-stack that
			// emptied it would take the player's tools along with the cobblestone.
			from = Inventory.SELECTION_SIZE;
			to = Inventory.INVENTORY_SIZE;
		}

		int moved = QuickStack.run(player, source, from, to, settings);
		player.containerMenu.broadcastFullState();
		report(player, moved);
	}

	private boolean onCooldown(ServerPlayer player, QuickStackConfig settings) {
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
		player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT,
				SoundSource.PLAYERS, 0.7F, 1.2F);
	}

	// Sorting -------------------------------------------------------------------------------------

	private void onSort(SortPayload payload, ServerPlayer player) {
		QuickStackConfig settings = config;

		if (settings == null || !settings.sortEnabled) {
			return;
		}

		if (payload.playerInventory()) {
			// The three main rows only. The hotbar is the one part of an inventory that is arranged
			// deliberately — a tool per key, in the order the player reaches for them — so sorting
			// it is destructive in a way that sorting a chest is not.
			ContainerSort.sort(player.getInventory(), Inventory.SELECTION_SIZE, Inventory.INVENTORY_SIZE,
					payload.mode(), payload.order());
		} else if (player.containerMenu instanceof ChestMenu menu) {
			Container container = menu.getContainer();
			ContainerSort.sort(container, 0, container.getContainerSize(), payload.mode(), payload.order());
		} else {
			return;
		}

		player.containerMenu.broadcastFullState();
		player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT,
				SoundSource.PLAYERS, 0.5F, 1.6F);
	}
}
