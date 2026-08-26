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
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.clock.WorldClock;
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
	/** Inhabited time at which the chunk's contribution saturates; more buys nothing. */
	private static final long INHABITED_CAP = 3_600_000L;

	private OverhaulCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> build(dispatcher));
	}

	private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> difficulty = Commands.literal("difficulty")
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
				.executes(context -> reportMoon(context.getSource()));

		// A literal per phase rather than a string argument, so tab completion lists them.
		for (MoonPhase phase : MoonPhase.values()) {
			moon.then(Commands.literal(phase.getSerializedName())
					.executes(context -> setMoon(context.getSource(), phase)));
		}

		dispatcher.register(Commands.literal("overhaul")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
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

		source.sendSuccess(() -> Component.literal("Chunk " + chunkX + ", " + chunkZ)
				.withStyle(ChatFormatting.GOLD), false);
		source.sendSuccess(() -> line("Local difficulty",
				String.format("%.2f", difficultyWith(level, pos, time))), false);
		source.sendSuccess(() -> line("Range here", String.format("%.2f to %.2f",
				difficultyWith(level, pos, 0L), difficultyWith(level, pos, INHABITED_CAP))), false);
		source.sendSuccess(() -> line("Inhabited time", time + " ticks ("
				+ String.format("%.1f", 100.0F * Math.min(1.0F, (float) time / INHABITED_CAP)) + "% of cap)"), false);
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

		float floor = difficultyWith(level, pos, 0L);
		float ceiling = difficultyWith(level, pos, INHABITED_CAP);

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
		long high = INHABITED_CAP;

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

		source.sendSuccess(() -> Component.literal("Moon").withStyle(ChatFormatting.GOLD), false);
		source.sendSuccess(() -> line("Phase", phase.getSerializedName() + " (" + phase.index() + ")"), false);
		source.sendSuccess(() -> line("Brightness", String.format("%.2f", level.getMoonBrightness(pos))), false);

		return 1;
	}

	/**
	 * Moves the world clock to the requested moon phase, keeping the time of day exactly where it
	 * is.
	 *
	 * <p>The moon is not a stored value either: the phase is which day of the eight-day lunar cycle
	 * the clock is on, so setting it means moving the clock by whole days. Only ever forwards —
	 * winding a world backwards would take the world age term of local difficulty with it, and that
	 * is the other half of what these commands exist to control.
	 */
	private static int setMoon(CommandSourceStack source, MoonPhase phase) {
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());

		// Moving the clock would work, but the pin would go on reporting the phase it is holding,
		// so the command would look broken. Say what is actually in the way instead.
		MoonPhase pinned = MoonLock.forced();

		if (pinned != null) {
			source.sendFailure(Component.literal("The moon is pinned to " + pinned.getSerializedName()
					+ " by the overhaul:fixed_moon_phase game rule. Set that rule to none first."));
			return 0;
		}

		Holder<WorldClock> clock = level.dimensionType().defaultClock().orElse(null);

		if (clock == null) {
			source.sendFailure(Component.literal(
					"This dimension has no clock of its own, so it has no moon to set."));
			return 0;
		}

		ServerClockManager clocks = source.getServer().clockManager();
		long now = clocks.getTotalTicks(clock);
		long cycle = (long) MoonPhase.PHASE_LENGTH * MoonPhase.COUNT;
		long dayStart = now - Math.floorMod(now, (long) MoonPhase.PHASE_LENGTH);
		long target = dayStart - Math.floorMod(dayStart, cycle) + phase.startTick()
				+ Math.floorMod(now, (long) MoonPhase.PHASE_LENGTH);

		while (target < now) {
			target += cycle;
		}

		clocks.setTotalTicks(clock, target);

		// Read the phase back rather than trusting the arithmetic: the value comes from the
		// dimension's environment attributes, which a data pack is free to redefine.
		MoonPhase landed = moonPhaseOf(level, pos);

		if (landed != phase) {
			source.sendFailure(Component.literal("Moved the clock, but this dimension reports the moon as "
					+ landed.getSerializedName() + " rather than " + phase.getSerializedName()
					+ ". Its environment attributes are not the vanilla ones."));
			return 0;
		}

		long advanced = target - now;

		source.sendSuccess(() -> Component.literal(String.format(
				"Moon set to %s, %d days on. Time of day unchanged.",
				phase.getSerializedName(), advanced / MoonPhase.PHASE_LENGTH))
				.withStyle(ChatFormatting.GREEN), true);

		return 1;
	}

	// Helpers ------------------------------------------------------------------------------------

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
	 * <p>This also passes through the {@code fixed_moon_phase} override, so what is reported is
	 * what the rest of the game is acting on.
	 */
	private static MoonPhase moonPhaseOf(ServerLevel level, BlockPos pos) {
		return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos);
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
