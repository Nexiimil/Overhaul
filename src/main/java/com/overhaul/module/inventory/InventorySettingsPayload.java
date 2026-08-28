package com.overhaul.module.inventory;

import com.overhaul.Overhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells a joining client which buttons are worth drawing.
 *
 * <p>The config that decides this lives on the server, and the buttons live on the client, so
 * without a packet the two cannot agree. Sending it on join means a server with the module
 * switched off produces no buttons at all, rather than buttons that quietly do nothing — and a
 * client connecting to a server without the mod never hears from this at all, which is the same
 * outcome by default.
 */
public record InventorySettingsPayload(boolean quickStack, boolean sort, boolean playerInventory)
		implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<InventorySettingsPayload> TYPE =
			new CustomPacketPayload.Type<>(Overhaul.id("inventory_settings"));

	public static final StreamCodec<RegistryFriendlyByteBuf, InventorySettingsPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BOOL, InventorySettingsPayload::quickStack,
					ByteBufCodecs.BOOL, InventorySettingsPayload::sort,
					ByteBufCodecs.BOOL, InventorySettingsPayload::playerInventory,
					InventorySettingsPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
