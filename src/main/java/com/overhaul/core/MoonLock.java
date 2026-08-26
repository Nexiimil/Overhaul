package com.overhaul.core;

import com.overhaul.Overhaul;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import org.jspecify.annotations.Nullable;

/**
 * The {@code overhaul:fixed_moon_phase} game rule: holds the moon at one phase.
 *
 * <p>The obvious way to pin the moon is to wind the world clock back a day whenever it drifts off
 * the wanted phase, and it is the wrong way. The phase is which day of the lunar cycle the clock is
 * on, so holding it that way means permanently rewinding world time — and world age is an input to
 * local difficulty, so a moon lock built that way would quietly suppress the very hordes it exists
 * to help test. Overriding the value where it is read costs one mixin and leaves the clock alone.
 *
 * <p>The override goes in at {@code EnvironmentAttributeSystem}, which is the single point every
 * reader passes through: the server's own {@code getMoonBrightness}, the client's interpolating
 * probe that feeds the sky renderer, and the clock item. One seam covers mechanics and visuals
 * together, which is what stops the two disagreeing.
 *
 * <p>The rule itself lives only on the server, so the value is pushed to each client on join and
 * whenever it changes. Without that a multiplayer client would keep drawing the real moon.
 */
public final class MoonLock {
	/**
	 * The rule's values: the eight phases, plus {@code none} for the ordinary cycle.
	 *
	 * <p>A separate enum rather than {@link MoonPhase} itself because the rule needs an off switch,
	 * and a second boolean rule to say whether the first one counts would be worse.
	 *
	 * <p>Lower case constants, unusually, because these names <em>are</em> the command argument:
	 * the game rule matches on {@link Enum#name()}, so {@code NEW_MOON} would be the only spelling
	 * the rule accepted while {@code /overhaul moon} takes {@code new_moon}. The alternative was a
	 * custom argument type, but an unregistered one breaks command tree sync to clients, which is
	 * a much worse trade than an unconventional constant name.
	 */
	@SuppressWarnings("checkstyle:constantname")
	public enum Value {
		none(null),
		full_moon(MoonPhase.FULL_MOON),
		waning_gibbous(MoonPhase.WANING_GIBBOUS),
		third_quarter(MoonPhase.THIRD_QUARTER),
		waning_crescent(MoonPhase.WANING_CRESCENT),
		new_moon(MoonPhase.NEW_MOON),
		waxing_crescent(MoonPhase.WAXING_CRESCENT),
		first_quarter(MoonPhase.FIRST_QUARTER),
		waxing_gibbous(MoonPhase.WAXING_GIBBOUS);

		private final @Nullable MoonPhase phase;

		Value(@Nullable MoonPhase phase) {
			this.phase = phase;
		}

		public @Nullable MoonPhase phase() {
			return phase;
		}
	}

	private static @Nullable GameRule<Value> rule;

	/**
	 * The phase currently being forced, or null for the ordinary cycle.
	 *
	 * <p>Read from a mixin on the hot path, so it is a plain field rather than a lookup. On a
	 * server it is refreshed from the game rule; on a client connected to one it is set by the
	 * payload. In single player both paths write the same value, which is harmless.
	 */
	private static volatile @Nullable MoonPhase forced;

	private MoonLock() {
	}

	/** @return the phase to report instead of the real one, or null to leave it alone */
	public static @Nullable MoonPhase forced() {
		return forced;
	}

	public static void register() {
		rule = GameRuleBuilder.forEnum(Value.none)
				.category(GameRuleCategory.MISC)
				.buildAndRegister(Overhaul.id("fixed_moon_phase"));

		PayloadTypeRegistry.clientboundPlay().register(MoonLockPayload.TYPE, MoonLockPayload.STREAM_CODEC);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			refresh(server);
			ServerPlayNetworking.send(handler.getPlayer(), payload());
		});

		// Polled rather than hooked on the rule's own change callback, because a rule can also be
		// changed by a data pack load or another mod writing it directly.
		ServerTickEvents.END_SERVER_TICK.register(MoonLock::tick);
	}

	private static int sinceCheck;

	private static void tick(MinecraftServer server) {
		if (++sinceCheck < 20) {
			return;
		}

		sinceCheck = 0;
		MoonPhase before = forced;
		refresh(server);

		if (before == forced) {
			return;
		}

		MoonLockPayload update = payload();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, update);
		}

		Overhaul.LOGGER.info("Fixed moon phase is now {}", forced == null ? "off" : forced.getSerializedName());
	}

	private static void refresh(MinecraftServer server) {
		GameRule<Value> current = rule;

		if (current == null) {
			return;
		}

		forced = server.overworld().getGameRules().get(current).phase();
	}

	private static MoonLockPayload payload() {
		MoonPhase phase = forced;
		return new MoonLockPayload(phase == null ? -1 : phase.index());
	}

	/** Applies a value pushed from the server. Called on the client only. */
	public static void applyFromServer(int phaseIndex) {
		if (phaseIndex < 0 || phaseIndex >= MoonPhase.COUNT) {
			forced = null;
			return;
		}

		for (MoonPhase phase : MoonPhase.values()) {
			if (phase.index() == phaseIndex) {
				forced = phase;
				return;
			}
		}

		forced = null;
	}

	/**
	 * Clears the override when a client leaves a server, so a pinned moon on one server does not
	 * follow the player into the next world they open.
	 */
	public static void clear() {
		forced = null;
	}
}
