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
 * The two ways Overhaul interferes with the moon, and the one seam both go through.
 *
 * <p>{@code overhaul:fixed_moon_phase} holds the moon at one phase. {@code
 * overhaul:moon_phase_offset} rotates the cycle by a whole number of phases, leaving it running.
 * The pin wins where both are set, because a frozen moon has no cycle left to rotate.
 *
 * <p>Neither touches the world clock, and that is the whole point. The phase is which day of the
 * eight day lunar cycle the clock is on, so the obvious implementation of either — wind the clock
 * until the moon reads right — moves world time. World time is an input to local difficulty, on a
 * sixty day ramp, so an eight day nudge to fix the moon quietly moves difficulty by up to a
 * seventh of that ramp. That would suppress or inflate the very hordes these exist to test, and it
 * would do it invisibly. Overriding the value where it is read costs one mixin and leaves both the
 * clock and the difficulty alone.
 *
 * <p>The override goes in at {@code EnvironmentAttributeSystem}, which is the single point every
 * reader passes through: the server's own {@code getMoonBrightness}, the client probe that feeds
 * the sky renderer, the clock item, and the moon brightness check that gates mob variant spawns.
 * One seam covers mechanics and visuals together, which is what stops the two disagreeing.
 *
 * <p>Both rules live only on the server, so the pair is pushed to each client on join and whenever
 * it changes. Without that a multiplayer client would keep drawing the real moon.
 */
public final class MoonLock {
	/**
	 * The pin's values: the eight phases, plus {@code none} for the ordinary cycle.
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

	/**
	 * Phases by {@link MoonPhase#index()}.
	 *
	 * <p>{@code values()} happens to be in index order today, but the rotation is arithmetic on
	 * index and nothing promises the two stay aligned, so this looks them up rather than assuming.
	 */
	private static final MoonPhase[] BY_INDEX = byIndex();

	private static @Nullable GameRule<Value> pinRule;
	private static @Nullable GameRule<Integer> offsetRule;

	/**
	 * The phase being held, or null for none, and the rotation applied to the running cycle.
	 *
	 * <p>Read from a mixin on the hot path, so these are plain fields rather than a lookup. On a
	 * server they are refreshed from the game rules; on a client connected to one they are set by
	 * the payload. In single player both paths write the same values, which is harmless.
	 */
	private static volatile @Nullable MoonPhase forced;
	private static volatile int offset;

	private MoonLock() {
	}

	private static MoonPhase[] byIndex() {
		MoonPhase[] phases = new MoonPhase[MoonPhase.COUNT];

		for (MoonPhase phase : MoonPhase.values()) {
			phases[phase.index()] = phase;
		}

		return phases;
	}

	/** The phase being held by {@code fixed_moon_phase}, or null if it is off. */
	public static @Nullable MoonPhase forced() {
		return forced;
	}

	/** How many phases {@code moon_phase_offset} rotates the cycle by, 0 for none. */
	public static int offset() {
		return offset;
	}

	/**
	 * The phase to report in place of the real one.
	 *
	 * <p>Returns {@code real} unchanged when neither rule is doing anything, which is the common
	 * case and lets the mixin skip writing a return value at all.
	 */
	public static MoonPhase apply(MoonPhase real) {
		MoonPhase pinned = forced;

		if (pinned != null) {
			return pinned;
		}

		int shift = offset;

		return shift == 0 ? real : BY_INDEX[Math.floorMod(real.index() + shift, MoonPhase.COUNT)];
	}

	public static void register() {
		pinRule = GameRuleBuilder.forEnum(Value.none)
				.category(GameRuleCategory.MISC)
				.buildAndRegister(Overhaul.id("fixed_moon_phase"));

		offsetRule = GameRuleBuilder.forInteger(0)
				.range(0, MoonPhase.COUNT - 1)
				.category(GameRuleCategory.MISC)
				.buildAndRegister(Overhaul.id("moon_phase_offset"));

		PayloadTypeRegistry.clientboundPlay().register(MoonLockPayload.TYPE, MoonLockPayload.STREAM_CODEC);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			refresh(server);
			send(handler.getPlayer());
		});

		// Polled rather than hooked on the rules' own change callbacks, because a rule can also be
		// changed by a data pack load or another mod writing it directly.
		ServerTickEvents.END_SERVER_TICK.register(MoonLock::tick);
	}

	private static int sinceCheck;

	private static void tick(MinecraftServer server) {
		if (++sinceCheck < 20) {
			return;
		}

		sinceCheck = 0;
		pushIfChanged(server);
	}

	/**
	 * Re-reads both rules and pushes the pair to every client if either moved.
	 *
	 * <p>Called from the poll, and directly by {@code /overhaul moon} so that a command's effect
	 * is on screen before the next poll rather than up to a second later.
	 */
	public static void pushIfChanged(MinecraftServer server) {
		MoonPhase wasForced = forced;
		int wasOffset = offset;

		refresh(server);

		if (wasForced == forced && wasOffset == offset) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player);
		}

		Overhaul.LOGGER.info("Moon is now {}, offset {}",
				forced == null ? "on its ordinary cycle" : "held at " + forced.getSerializedName(), offset);
	}

	/** Sets the rotation, then republishes so the read back and every client agree at once. */
	public static void setOffset(MinecraftServer server, int phases) {
		GameRule<Integer> rule = offsetRule;

		if (rule == null) {
			return;
		}

		server.overworld().getGameRules().set(rule, Math.floorMod(phases, MoonPhase.COUNT), server);
		pushIfChanged(server);
	}

	private static void refresh(MinecraftServer server) {
		if (pinRule != null) {
			forced = server.overworld().getGameRules().get(pinRule).phase();
		}

		if (offsetRule != null) {
			offset = Math.floorMod(server.overworld().getGameRules().get(offsetRule), MoonPhase.COUNT);
		}
	}

	private static void send(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, MoonLockPayload.TYPE)) {
			return;
		}

		MoonPhase phase = forced;
		ServerPlayNetworking.send(player, new MoonLockPayload(phase == null ? -1 : phase.index(), offset));
	}

	/** Applies a pair pushed from the server. Called on the client only. */
	public static void applyFromServer(int phaseIndex, int phases) {
		forced = phaseIndex < 0 || phaseIndex >= MoonPhase.COUNT ? null : BY_INDEX[phaseIndex];
		offset = Math.floorMod(phases, MoonPhase.COUNT);
	}

	/**
	 * Clears both overrides when a client leaves a server, so a moon bent on one server does not
	 * follow the player into the next world they open.
	 */
	public static void clear() {
		forced = null;
		offset = 0;
	}
}
