package com.overhaul.client;

import com.overhaul.client.inventory.ContainerButtons;
import com.overhaul.client.inventory.SlotKeys;
import com.overhaul.core.ModuleManager;
import com.overhaul.core.MoonLock;
import com.overhaul.core.MoonLockPayload;
import com.overhaul.module.backpack.OpenBackpackPayload;
import com.overhaul.module.inventory.QuickStackPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class OverhaulClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("overhaul", "overhaul"));

	private static final KeyMapping OPEN_BACKPACK =
			new KeyMapping("key.overhaul.open_backpack", GLFW.GLFW_KEY_B, CATEGORY);

	private static final KeyMapping QUICK_STACK =
			new KeyMapping("key.overhaul.quick_stack", GLFW.GLFW_KEY_V, CATEGORY);

	private static final KeyMapping TOGGLE_SLOT_LOCK =
			new KeyMapping("key.overhaul.toggle_slot_lock", GLFW.GLFW_KEY_L, CATEGORY);


	@Override
	public void onInitializeClient() {
		ModuleManager.initClient();
		registerMoonLock();
		registerBackpackKey();
		registerInventory();
	}

	private static void registerBackpackKey() {
		if (!ModuleManager.isEnabled("backpack")) {
			return;
		}

		KeyMappingHelper.registerKeyMapping(OPEN_BACKPACK);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_BACKPACK.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(OpenBackpackPayload.INSTANCE);
				}
			}
		});
	}

	/**
	 * One key quick-stacks from the inventory without opening anything; the other locks the slot
	 * the cursor is over inside a container screen. Both are only ever a request — the server
	 * decides what each one actually reaches.
	 */
	private static void registerInventory() {
		if (!ModuleManager.isEnabled("inventory")) {
			return;
		}

		ContainerButtons.register();
		SlotKeys.register(TOGGLE_SLOT_LOCK);

		KeyMappingHelper.registerKeyMapping(QUICK_STACK);
		KeyMappingHelper.registerKeyMapping(TOGGLE_SLOT_LOCK);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (QUICK_STACK.consumeClick()) {
				if (client.player != null) {
					ClientPlayNetworking.send(new QuickStackPayload(false));
				}
			}
		});
	}

	/**
	 * Takes the server's pinned moon phase and cycle rotation, if it has either.
	 *
	 * <p>Registered whatever the module config says, because the moon is not any one module's: the
	 * game rules live on the server, and a client that ignored them would draw the real moon over
	 * mechanics running on a different one.
	 *
	 * <p>Cleared on disconnect so a moon bent on one server does not follow the player into the
	 * next world they open.
	 */
	private static void registerMoonLock() {
		ClientPlayNetworking.registerGlobalReceiver(MoonLockPayload.TYPE,
				(payload, context) -> context.client()
						.execute(() -> MoonLock.applyFromServer(payload.phaseIndex(), payload.offset())));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MoonLock.clear());
	}
}
