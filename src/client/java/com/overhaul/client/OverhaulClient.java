package com.overhaul.client;

import com.overhaul.core.ModuleManager;
import com.overhaul.module.backpack.OpenBackpackPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class OverhaulClient implements ClientModInitializer {
	private static final KeyMapping OPEN_BACKPACK = new KeyMapping(
			"key.overhaul.open_backpack",
			GLFW.GLFW_KEY_B,
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("overhaul", "overhaul")));

	@Override
	public void onInitializeClient() {
		ModuleManager.initClient();

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
}
