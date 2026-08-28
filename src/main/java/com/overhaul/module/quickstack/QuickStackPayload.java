package com.overhaul.module.quickstack;

import com.overhaul.Overhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Asks the server to quick-stack, either from the player's inventory or from the container they
 * have open.
 *
 * <p>The packet names a source in the abstract, never a slot or a block: the server reads the
 * player's own inventory and its own record of which menu is open, so a modified client cannot
 * name a container it is not standing at or one it never opened.
 */
public record QuickStackPayload(boolean fromOpenContainer) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<QuickStackPayload> TYPE =
			new CustomPacketPayload.Type<>(Overhaul.id("quick_stack"));

	public static final StreamCodec<RegistryFriendlyByteBuf, QuickStackPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BOOL, QuickStackPayload::fromOpenContainer,
					QuickStackPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
