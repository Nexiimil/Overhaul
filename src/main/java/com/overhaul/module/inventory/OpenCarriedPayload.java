package com.overhaul.module.inventory;

import com.overhaul.Overhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Opens the container item sitting in one of the player's own inventory slots.
 *
 * <p>A slot index is all the client sends, and the server reads it against the sending player's
 * inventory, so the worst a modified client can name is a slot it already owns.
 */
public record OpenCarriedPayload(int slot) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenCarriedPayload> TYPE =
			new CustomPacketPayload.Type<>(Overhaul.id("open_carried"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenCarriedPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, OpenCarriedPayload::slot,
					OpenCarriedPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
