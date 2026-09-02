package com.overhaul.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

/**
 * {@code /overhaul} — the tuning and debugging commands.
 *
 * <p>Both of these exist because the numbers that drive hordes are derived rather than stored, so
 * there is no field to poke at and no way to reach a given state except by waiting for it. Being
 * able to put a chunk into the state you want to test is the difference between tuning the mob
 * module in minutes and tuning it over several in-game months.
 *
 * <p>Registered from the mod entry point rather than from a module, because local difficulty and
 * the moon drive vanilla mob equipment and spawning too — these stay useful with every Overhaul
 * module switched off.
 */
public final class OverhaulCommands {
	/**
	 * An inhabited time far past any plausible saturation point, used only as the top of the search
	 * in {@link #saturationPoint}.
	 *
	 * <p>Deliberately not vanilla's actual clamp. Hardcoding that would be the one copied number in
	 * a command built to avoid copying vanilla's difficulty maths, and it would fail in one
	 * direction only: raise the clamp and this would start refusing targets that are genuinely
	 * reachable, while reporting a ceiling and a percentage that were quietly wrong. A bound that
	 * is merely enormous cannot go stale that way — about thirty-five thousand years of it.
	 */
	private static final long INHABITED_PROBE = 1L << 40;

	private OverhaulCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> build(dispatcher));
	}

	private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> difficulty = Commands.literal("difficulty")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(context -> reportDifficulty(context.getSource()))
				.then(Commands.literal("set")
						.then(Commands.argument("value", FloatArgumentType.floatArg(0.0F))
								.executes(context -> setDifficulty(context.getSource(),
										FloatArgumentType.getFloat(context, "value")))))
				.then(Commands.literal("inhabited")
						.then(Commands.argument("ticks", LongArgumentType.longArg(0))
								.executes(context -> setInhabited(context.getSource(),
										LongArgumentType.getLong(context, "ticks")))))
				.then(Commands.literal("reset")
						.executes(context -> setInhabited(context.getSource(), 0L)));

		LiteralArgumentBuilder<CommandSourceStack> moon = Commands.literal("moon")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(context -> reportMoon(context.getSource()))
				.then(Commands.literal("reset")
						.executes(context -> resetMoon(context.getSource())));

		// A literal per phase rather than a string argument, so tab completion lists them.
		for (MoonPhase phase : MoonPhase.values()) {
			moon.then(Commands.literal(phase.getSerializedName())
					.executes(context -> setMoon(context.getSource(), phase)));
		}

		// The requirement sits on each subtree rather than on the root, because the multiplayer
		// module hangs /overhaul claim off the same root and that half is for everyone.
		dispatcher.register(Commands.literal("overhaul")
				.then(difficulty)
				.then(moon));
	}

	// Local difficulty ---------------------------------------------------------------------------

	private static int reportDifficulty(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		ChunkAccess chunk = chunkAt(level, pos);

		if (chunk == null) {
			source.sendFailure(Component.literal("That chunk is not loaded."));
			return 0;
		}

		int chunkX = pos.getX() >> 4;
		int chunkZ = pos.getZ() >> 4;
		long time = chunk.getInhabitedTime();
		long cap = saturationPoint(level, pos);

		source.sendSuccess(() -> Component.literal("Chunk " + chunkX + ", " + chunkZ)
				.withStyle(ChatFormatting.GOLD), false);
		source.sendSuccess(() -> line("Local difficulty",
				String.format("%.2f", difficultyWith(level, pos, time))), false);
		source.sendSuccess(() -> line("Range here", String.format("%.2f to %.2f",
				difficultyWith(level, pos, 0L), difficultyWith(level, pos, cap))), false);

		// Peaceful pins difficulty at zero however long a chunk has been lived in, so there is no
		// cap to be a percentage of and the honest thing is to say so rather than divide by it.
		source.sendSuccess(() -> line("Inhabited time", cap <= 0L
				? time + " ticks (inhabited time buys nothing on "
						+ level.getDifficulty().getSerializedName() + ")"
				: time + " ticks (" + String.format("%.1f", 100.0F * Math.min(1.0F, (float) time / cap))
						+ "% of cap)"), false);
		source.sendSuccess(() -> line("World difficulty", level.getDifficulty().getSerializedName()), false);
		source.sendSuccess(() -> line("Moon", moonPhaseOf(level, pos).getSerializedName()), false);

		return 1;
	}

	/**
	 * Solves for the inhabited time that produces the requested local difficulty.
	 *
	 * <p>The search asks vanilla's own {@link DifficultyInstance} what a candidate would be worth
	 * rather than reimplementing the formula here. Effective difficulty rises monotonically with
	 * inhabited time, so a binary search lands on the smallest value that reaches the target — and
	 * the answer stays correct even if Mojang retunes the maths.
	 */
	private static int setDifficulty(CommandSourceStack source, float target) {
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		ChunkAccess chunk = chunkAt(level, pos);

		if (chunk == null) {
			source.sendFailure(Component.literal("That chunk is not loaded."));
			return 0;
		}

		long cap = saturationPoint(level, pos);
		float floor = difficultyWith(level, pos, 0L);
		float ceiling = difficultyWith(level, pos, cap);

		if (target < floor) {
			source.sendFailure(Component.literal(String.format(
					"Cannot go below %.2f on %s — that is the floor with no inhabited time at all.",
					floor, level.getDifficulty().getSerializedName())));
			return 0;
		}

		if (target > ceiling) {
			source.sendFailure(Component.literal(String.format(
					"Cannot exceed %.2f on %s — inhabited time saturates there. Raise the world difficulty.",
					ceiling, level.getDifficulty().getSerializedName())));
			return 0;
		}

		long low = 0L;
		long high = cap;

		while (low < high) {
			long mid = low + (high - low) / 2L;

			if (difficultyWith(level, pos, mid) < target) {
				low = mid + 1L;
			} else {
				high = mid;
			}
		}

		return applyInhabited(source, level, chunk, pos, low);
	}

	private static int setInhabited(CommandSourceStack source, long ticks) {
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		ChunkAccess chunk = chunkAt(level, pos);

		if (chunk == null) {
			source.sendFailure(Component.literal("That chunk is not loaded."));
			return 0;
		}

		return applyInhabited(source, level, chunk, pos, ticks);
	}

	private static int applyInhabited(CommandSourceStack source, ServerLevel level, ChunkAccess chunk,
			BlockPos pos, long ticks) {
		chunk.setInhabitedTime(ticks);

		// Without this the write survives only until the chunk is evicted, which makes the command
		// look like it worked right up until the point it silently did not.
		chunk.markUnsaved();

		int chunkX = pos.getX() >> 4;
		int chunkZ = pos.getZ() >> 4;
		float now = difficultyWith(level, pos, ticks);

		source.sendSuccess(() -> Component.literal(String.format(
				"Chunk %d, %d is now at local difficulty %.2f (inhabited time %d ticks).",
				chunkX, chunkZ, now, ticks)).withStyle(ChatFormatting.GREEN), true);

		return 1;
	}

	// Moon ---------------------------------------------------------------------------------------

	private static int reportMoon(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		MoonPhase phase = moonPhaseOf(level, pos);
		MoonPhase pinned = MoonLock.forced();
		int offset = MoonLock.offset();

		source.sendSuccess(() -> Component.literal("Moon").withStyle(ChatFormatting.GOLD), false);
		source.sendSuccess(() -> line("Phase", phase.getSerializedName() + " (" + phase.index() + ")"), false);
		source.sendSuccess(() -> line("Brightness", String.format("%.2f", level.getMoonBrightness(pos))), false);

		// Only worth a line when it is doing something, but then it is worth it: without these the
		// moon reads as an ordinary one and there is nothing to say why it is not the one the clock
		// implies.
		if (pinned != null) {
			source.sendSuccess(() -> line("Held by", "overhaul:fixed_moon_phase at "
					+ pinned.getSerializedName()), false);
		}

		if (offset != 0) {
			// The true phase is recoverable by undoing the rotation, but only while nothing is
			// pinned — a pin discards the real value rather than shifting it, and re-deriving it
			// here would mean a second copy of vanilla's clock arithmetic.
			String rotated = "+" + offset + (offset == 1 ? " phase" : " phases")
					+ " (overhaul:moon_phase_offset)";
			source.sendSuccess(() -> line("Rotated", pinned == null
					? rotated + ", true phase " + shift(phase, -offset).getSerializedName()
					: rotated + ", applies when the pin is off"), false);
		}

		return 1;
	}

	/**
	 * Rotates the lunar cycle so that today reads as the requested phase.
	 *
	 * <p>Rotating rather than winding the clock, because the phase is which day of the eight day
	 * cycle the clock is on: reaching a phase by moving the clock moves world time with it, and
	 * world time feeds the local difficulty that the other half of this command exists to set
	 * deliberately. {@link MoonLock} has the full argument.
	 *
	 * <p>The rotation changes tonight's moon and nothing else. The clock, the time of day and the
	 * world age all stay exactly where they were, and the following nights go on through the cycle
	 * from the new phase at the ordinary rate.
	 */
	private static int setMoon(CommandSourceStack source, MoonPhase phase) {
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());

		// The rotation would still be applied underneath, but the pin would go on reporting the
		// phase it is holding, so the command would look broken. Say what is in the way instead.
		MoonPhase pinned = MoonLock.forced();

		if (pinned != null) {
			source.sendFailure(Component.literal("The moon is held at " + pinned.getSerializedName()
					+ " by the overhaul:fixed_moon_phase game rule. Set that rule to none first."));
			return 0;
		}

		MoonPhase before = moonPhaseOf(level, pos);
		MoonLock.setOffset(source.getServer(), MoonLock.offset() + phase.index() - before.index());

		// Read the phase back rather than trusting the arithmetic: the value comes from the
		// dimension's environment attributes, which a data pack is free to redefine.
		MoonPhase landed = moonPhaseOf(level, pos);

		if (landed != phase) {
			source.sendFailure(Component.literal("Rotated the cycle, but this dimension reports the moon as "
					+ landed.getSerializedName() + " rather than " + phase.getSerializedName()
					+ ". Its environment attributes are not the vanilla ones."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Moon is now " + phase.getSerializedName()
				+ ", and the cycle carries on from there. Clock and local difficulty untouched.")
				.withStyle(ChatFormatting.GREEN), true);

		return 1;
	}

	/** Puts the cycle back where the clock says it should be. */
	private static int resetMoon(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());

		MoonLock.setOffset(source.getServer(), 0);

		MoonPhase phase = moonPhaseOf(level, pos);
		MoonPhase pinned = MoonLock.forced();

		source.sendSuccess(() -> Component.literal("Moon cycle back on the clock, now "
				+ phase.getSerializedName() + ".").withStyle(ChatFormatting.GREEN), true);

		if (pinned != null) {
			source.sendSuccess(() -> Component.literal("The overhaul:fixed_moon_phase game rule is still "
					+ "holding it at " + pinned.getSerializedName() + ".").withStyle(ChatFormatting.YELLOW), false);
		}

		return 1;
	}

	// Helpers ------------------------------------------------------------------------------------

	/**
	 * The smallest inhabited time that buys everything it can here — past this, more is wasted.
	 *
	 * <p>Asked of vanilla rather than copied from it, in the same spirit as the search in
	 * {@link #setDifficulty}: evaluate the difficulty at an absurd inhabited time to learn what the
	 * maximum is, then binary search for the first time that reaches it. Effective difficulty rises
	 * monotonically with inhabited time and then flattens, so that lands exactly on the knee.
	 *
	 * <p>Returns zero on Peaceful, where difficulty is pinned at zero and the maximum is reached
	 * before the chunk has been lived in at all.
	 */
	private static long saturationPoint(ServerLevel level, BlockPos pos) {
		float most = difficultyWith(level, pos, INHABITED_PROBE);
		long low = 0L;
		long high = INHABITED_PROBE;

		while (low < high) {
			long mid = low + (high - low) / 2L;

			if (difficultyWith(level, pos, mid) < most) {
				low = mid + 1L;
			} else {
				high = mid;
			}
		}

		return low;
	}

	/** What local difficulty would be here if the chunk had been inhabited for this long. */
	private static float difficultyWith(ServerLevel level, BlockPos pos, long inhabitedTime) {
		return new DifficultyInstance(level.getDifficulty(), level.getOverworldClockTime(),
				inhabitedTime, level.getMoonBrightness(pos)).getEffectiveDifficulty();
	}

	/**
	 * The phase the level is currently reporting.
	 *
	 * <p>Read straight from the environment attribute rather than worked back from brightness:
	 * {@code MOON_BRIGHTNESS_PER_PHASE} is {@code [1, .75, .5, .25, 0, .25, .5, .75]}, so six of
	 * the eight phases share a brightness with another and a waxing crescent is indistinguishable
	 * from a waning one that way.
	 *
	 * <p>This also passes through the {@code fixed_moon_phase} and {@code moon_phase_offset}
	 * overrides, so what is reported is what the rest of the game is acting on — which is why
	 * {@code setMoon} can solve for a rotation by reading it before and after.
	 */
	private static MoonPhase moonPhaseOf(ServerLevel level, BlockPos pos) {
		return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos);
	}

	/** The phase {@code steps} along the cycle from this one, wrapping in either direction. */
	private static MoonPhase shift(MoonPhase phase, int steps) {
		int index = Math.floorMod(phase.index() + steps, MoonPhase.COUNT);

		for (MoonPhase candidate : MoonPhase.values()) {
			if (candidate.index() == index) {
				return candidate;
			}
		}

		return phase;
	}

	/** The chunk at this position, or null if it is not already loaded — never forces one in. */
	private static @Nullable ChunkAccess chunkAt(ServerLevel level, BlockPos pos) {
		return level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
	}

	private static Component line(String label, String value) {
		return Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}
}
