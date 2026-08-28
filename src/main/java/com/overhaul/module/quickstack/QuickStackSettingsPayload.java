package com.overhaul.module.quickstack;

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
public record QuickStackSettingsPayload(boolean quickStack, boolean sort, boolean playerInventory)
		implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<QuickStackSettingsPayload> TYPE =
			new CustomPacketPayload.Type<>(Overhaul.id("quick_stack_settings"));

	public static final StreamCodec<RegistryFriendlyByteBuf, QuickStackSettingsPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BOOL, QuickStackSettingsPayload::quickStack,
					ByteBufCodecs.BOOL, QuickStackSettingsPayload::sort,
					ByteBufCodecs.BOOL, QuickStackSettingsPayload::playerInventory,
					QuickStackSettingsPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
