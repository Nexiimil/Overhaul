package com.overhaul.core;

import com.overhaul.Overhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells a client how the server is bending the moon: which phase is pinned, or {@code -1} for
 * none, and how many phases the cycle is rotated by.
 *
 * <p>This exists because the moon is drawn from the client's own copy of the environment
 * attributes, computed from its own clock — the server never sends it. Bending the moon server
 * side alone would give a player a full moon in the sky and new moon mechanics under it, so the
 * values have to travel.
 */
public record MoonLockPayload(int phaseIndex, int offset) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MoonLockPayload> TYPE =
			new CustomPacketPayload.Type<>(Overhaul.id("moon_lock"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MoonLockPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, MoonLockPayload::phaseIndex,
					ByteBufCodecs.VAR_INT, MoonLockPayload::offset,
					MoonLockPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
