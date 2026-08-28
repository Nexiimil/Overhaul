package com.overhaul.module.inventory;

import com.overhaul.Overhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Voids what the player is holding on the cursor, or hands back the last thing voided.
 *
 * <p>The packet carries nothing: the only stack it can reach is the one the server already knows
 * the player has picked up, so there is no slot for a modified client to name.
 */
public record TrashPayload() implements CustomPacketPayload {
	public static final TrashPayload INSTANCE = new TrashPayload();

	public static final CustomPacketPayload.Type<TrashPayload> TYPE =
			new CustomPacketPayload.Type<>(Overhaul.id("trash"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TrashPayload> STREAM_CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
