package com.overhaul.module.inventory;

import com.overhaul.Overhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Asks the server to sort the container the player has open, or their own inventory.
 *
 * <p>The mode and fill order travel with the request rather than living on the server, because
 * they are a display preference: the client remembers which way its buttons are set and says so
 * each time. There is nothing to guard here — a player who can open a container can already
 * rearrange it by hand, so a sort can only produce an arrangement they could have reached anyway.
 */
public record SortPayload(SortMode mode, FillOrder order, boolean playerInventory)
		implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SortPayload> TYPE =
			new CustomPacketPayload.Type<>(Overhaul.id("sort_container"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SortPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BYTE.map(SortMode::byIndex, mode -> (byte) mode.ordinal()),
					SortPayload::mode,
					ByteBufCodecs.BYTE.map(FillOrder::byIndex, order -> (byte) order.ordinal()),
					SortPayload::order,
					ByteBufCodecs.BOOL,
					SortPayload::playerInventory,
					SortPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
