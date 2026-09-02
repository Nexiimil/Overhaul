package com.overhaul.module.multiplayer;

import java.util.Locale;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;

/**
 * The named permission tiers, as a config string.
 *
 * <p>Permissions stopped being plain integers, so a config that said {@code 2} would now be
 * naming something the game no longer has. Spelling the tiers out is also better config: someone
 * editing this file can tell what {@code gamemasters} means without looking up which number
 * {@code /gamemode} needs.
 */
public final class Permissions {
	private Permissions() {
	}

	/** @return true when this player is at or above the named tier; {@code none} is never true */
	public static boolean check(String tier, ServerPlayer player) {
		PermissionCheck required = of(tier);
		return required != null && required.check(player.permissions());
	}

	public static boolean check(String tier, CommandSourceStack source) {
		PermissionCheck required = of(tier);
		return required != null && required.check(source.permissions());
	}

	private static @org.jspecify.annotations.Nullable PermissionCheck of(String tier) {
		return switch (tier.toLowerCase(Locale.ROOT)) {
			case "none" -> null;
			case "all" -> Commands.LEVEL_ALL;
			case "moderators" -> Commands.LEVEL_MODERATORS;
			case "admins" -> Commands.LEVEL_ADMINS;
			case "owners" -> Commands.LEVEL_OWNERS;
			default -> Commands.LEVEL_GAMEMASTERS;
		};
	}
}
