package com.overhaul.module.inventory;

import com.overhaul.Overhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Flips the lock on one of the player's own inventory slots.
 *
 * <p>A slot index is the one thing a client can name here, and the only inventory it can name it
 * in is its own — the server reads the index against the sending player and refuses anything
 * outside the range it is willing to lock.
 */
public record ToggleSlotLockPayload(int slot) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ToggleSlotLockPayload> TYPE =
			new CustomPacketPayload.Type<>(Overhaul.id("toggle_slot_lock"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleSlotLockPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, ToggleSlotLockPayload::slot,
					ToggleSlotLockPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
