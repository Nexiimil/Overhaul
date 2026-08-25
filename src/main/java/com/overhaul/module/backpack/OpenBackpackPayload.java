package com.overhaul.module.backpack;

import com.overhaul.Overhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Asks the server to open the player's backpack.
 *
 * <p>The packet carries nothing: the server picks the backpack, so a modified client cannot name
 * a slot it does not own or one that holds something else.
 */
public record OpenBackpackPayload() implements CustomPacketPayload {
	public static final OpenBackpackPayload INSTANCE = new OpenBackpackPayload();

	public static final CustomPacketPayload.Type<OpenBackpackPayload> TYPE =
			new CustomPacketPayload.Type<>(Overhaul.id("open_backpack"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenBackpackPayload> STREAM_CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
