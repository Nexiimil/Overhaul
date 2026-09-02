package com.overhaul.module.multiplayer;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.overhaul.module.multiplayer.TeamClaims.Access;
import com.overhaul.module.multiplayer.TeamClaims.Settings;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.TeamArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

/**
 * {@code /overhaul claim} — everything a team does with its land.
 *
 * <p>Two audiences and two permission levels. Naming a team's leader is an operator's job, because
 * a scoreboard team has no owner for the game to consult and somebody outside the team has to
 * settle who speaks for it. Everything after that belongs to the leader, and claiming itself
 * belongs to whoever the leader says it does — so a server operator sets a team going once and is
 * not asked about it again.
 */
public final class ClaimCommands {
	private ClaimCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> build(dispatcher));
	}

	private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> access = Commands.literal("access");

		for (boolean allies : new boolean[] { false, true }) {
			access.then(Commands.literal(allies ? "allies" : "outsiders")
					.then(Commands.literal("build")
							.then(Commands.argument("allowed", BoolArgumentType.bool())
									.executes(context -> setBuild(context, allies,
											BoolArgumentType.getBool(context, "allowed")))))
					.then(Commands.literal("interact")
							.then(Commands.argument("allowed", BoolArgumentType.bool())
									.executes(context -> setInteract(context, allies,
											BoolArgumentType.getBool(context, "allowed")))))
					.then(Commands.literal("except")
							.then(Commands.literal("add")
									.then(Commands.argument("block", StringArgumentType.string())
											.executes(context -> setException(context, allies,
													StringArgumentType.getString(context, "block"), true))))
							.then(Commands.literal("remove")
									.then(Commands.argument("block", StringArgumentType.string())
											.executes(context -> setException(context, allies,
													StringArgumentType.getString(context, "block"), false))))));
		}

		LiteralArgumentBuilder<CommandSourceStack> claim = Commands.literal("claim")
				.executes(context -> describeChunk(context.getSource()))
				.then(Commands.literal("here").executes(context -> claimHere(context.getSource())))
				.then(Commands.literal("release").executes(context -> releaseHere(context.getSource())))
				.then(Commands.literal("list").executes(context -> list(context.getSource())))
				.then(Commands.literal("settings").executes(context -> describeSettings(context.getSource())))
				.then(Commands.literal("claiming")
						.then(Commands.literal("anyone").executes(context -> setClaiming(context, true)))
						.then(Commands.literal("leader").executes(context -> setClaiming(context, false))))
				.then(Commands.literal("ally")
						.then(Commands.literal("add")
								.then(Commands.argument("team", TeamArgument.team())
										.executes(context -> setAlly(context, true))))
						.then(Commands.literal("remove")
								.then(Commands.argument("team", TeamArgument.team())
										.executes(context -> setAlly(context, false)))))
				.then(access)
				.then(TeamCommands.subtree())
				.then(Commands.literal("leader")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("team", TeamArgument.team())
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ClaimCommands::setLeader))));

		dispatcher.register(Commands.literal("overhaul").then(claim));
	}

	// Looking at land ------------------------------------------------------------------------------

	private static int describeChunk(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		ChunkPos chunk = ChunkPos.containing(BlockPos.containing(source.getPosition()));
		String owner = Claims.ownerOf(level, chunk);

		source.sendSuccess(() -> Component.literal("Chunk " + chunk.x() + ", " + chunk.z())
				.withStyle(ChatFormatting.GOLD), false);

		if (owner == null) {
			source.sendSuccess(() -> line("Owner", "unclaimed"), false);
			return 1;
		}

		source.sendSuccess(() -> Component.literal("  Owner: ").withStyle(ChatFormatting.GRAY)
				.append(Protection.teamName(level, owner)), false);
		source.sendSuccess(() -> line("Team holds here",
				Claims.countHeldBy(level, owner) + " chunk(s)"), false);
		return 1;
	}

	private static int list(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlayerTeam team = player.getTeam();

		if (team == null) {
			source.sendFailure(Component.literal("You are not on a team, so you hold no land."));
			return 0;
		}

		ServerLevel level = source.getLevel();
		List<ChunkPos> held = Claims.heldBy(level, team.getName());

		if (held.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Your team holds nothing in this dimension."), false);
			return 0;
		}

		source.sendSuccess(() -> Component.literal(held.size() + " chunk(s) held here")
				.withStyle(ChatFormatting.GOLD), false);

		// A team with a hundred chunks would otherwise fill the chat with coordinates nobody reads.
		held.stream().limit(20).forEach(chunk -> source.sendSuccess(
				() -> line("Chunk", chunk.x() + ", " + chunk.z() + "  (block " + chunk.getMiddleBlockX()
						+ ", " + chunk.getMiddleBlockZ() + ")"), false));

		if (held.size() > 20) {
			source.sendSuccess(() -> Component.literal("  ... and " + (held.size() - 20) + " more")
					.withStyle(ChatFormatting.DARK_GRAY), false);
		}

		return held.size();
	}

	private static int describeSettings(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlayerTeam team = player.getTeam();

		if (team == null) {
			source.sendFailure(Component.literal("You are not on a team."));
			return 0;
		}

		Settings settings = TeamClaims.settings(source.getServer(), team.getName());

		source.sendSuccess(() -> Component.literal("Claim settings for ").withStyle(ChatFormatting.GOLD)
				.append(team.getDisplayName()), false);
		source.sendSuccess(() -> line("Leader", settings.leader()
				.map(uuid -> nameOf(source, uuid))
				.orElse("nobody yet — an operator sets one with /overhaul claim leader")), false);
		source.sendSuccess(() -> line("Claiming", settings.anyMemberMayClaim()
				? "any member" : "the leader only"), false);
		source.sendSuccess(() -> line("Allies", settings.allyTeams().isEmpty()
				? "none" : String.join(", ", settings.allyTeams())), false);
		describeAccess(source, "Outsiders", settings.outsiders());
		describeAccess(source, "Allies", settings.allies());
		return 1;
	}

	private static void describeAccess(CommandSourceStack source, String label, Access access) {
		String summary = (access.build() ? "may build" : "may not build")
				+ ", " + (access.interact() ? "may interact" : "may not interact");
		String exceptions = access.interactExceptions().isEmpty()
				? "" : "  except " + String.join(", ", access.interactExceptions());
		source.sendSuccess(() -> line(label, summary + exceptions), false);
	}

	// Taking and giving up land ---------------------------------------------------------------------

	private static int claimHere(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ChunkPos chunk = ChunkPos.containing(player.blockPosition());
		Claiming.Result result = Claiming.claim(player, source.getLevel(), chunk);

		if (!result.claimed()) {
			source.sendFailure(Component.literal(result.reason()));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Claimed chunk " + chunk.x() + ", " + chunk.z() + ".")
				.withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int releaseHere(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getLevel();
		ChunkPos chunk = ChunkPos.containing(player.blockPosition());
		String owner = Claims.ownerOf(level, chunk);

		if (owner == null) {
			source.sendFailure(Component.literal("This chunk is not claimed."));
			return 0;
		}

		PlayerTeam team = player.getTeam();

		if (team == null || !team.getName().equals(owner)) {
			source.sendFailure(Component.literal("This chunk belongs to another team."));
			return 0;
		}

		if (!Claiming.mayClaimFor(source.getServer(), player, owner)) {
			source.sendFailure(Component.literal("Only your team's leader may release land."));
			return 0;
		}

		Claims.release(level, chunk);
		source.sendSuccess(() -> Component.literal("Released chunk " + chunk.x() + ", " + chunk.z() + ".")
				.withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	// Team settings ---------------------------------------------------------------------------------

	private static int setLeader(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		PlayerTeam team = TeamArgument.getTeam(context, "team");
		ServerPlayer player = EntityArgument.getPlayer(context, "player");

		if (!team.getPlayers().contains(player.getScoreboardName())) {
			context.getSource().sendFailure(Component.literal(player.getScoreboardName()
					+ " is not on team " + team.getName() + "."));
			return 0;
		}

		TeamClaims.update(context.getSource().getServer(), team.getName(),
				settings -> settings.withLeader(player.getUUID()));

		context.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName()
				+ " now leads ").withStyle(ChatFormatting.GREEN).append(team.getDisplayName()), true);
		return 1;
	}

	private static int setClaiming(CommandContext<CommandSourceStack> context, boolean anyone)
			throws CommandSyntaxException {
		String team = leaderTeam(context.getSource());

		if (team == null) {
			return 0;
		}

		TeamClaims.update(context.getSource().getServer(), team,
				settings -> settings.withAnyMemberMayClaim(anyone));
		context.getSource().sendSuccess(() -> Component.literal(anyone
				? "Any member of your team may now claim land."
				: "Only you may claim land for your team now.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int setAlly(CommandContext<CommandSourceStack> context, boolean allied)
			throws CommandSyntaxException {
		String team = leaderTeam(context.getSource());

		if (team == null) {
			return 0;
		}

		PlayerTeam ally = TeamArgument.getTeam(context, "team");

		if (ally.getName().equals(team)) {
			context.getSource().sendFailure(Component.literal("A team is already its own ally."));
			return 0;
		}

		TeamClaims.update(context.getSource().getServer(), team,
				settings -> settings.withAlly(ally.getName(), allied));
		context.getSource().sendSuccess(() -> Component.literal(allied ? "Allied with " : "No longer allied with ")
				.withStyle(ChatFormatting.GREEN).append(ally.getDisplayName()), false);
		return 1;
	}

	private static int setBuild(CommandContext<CommandSourceStack> context, boolean allies, boolean allowed)
			throws CommandSyntaxException {
		return changeAccess(context, allies, access -> access.withBuild(allowed),
				(allies ? "Allies" : "Outsiders") + (allowed ? " may now build here." : " may no longer build here."));
	}

	private static int setInteract(CommandContext<CommandSourceStack> context, boolean allies, boolean allowed)
			throws CommandSyntaxException {
		return changeAccess(context, allies, access -> access.withInteract(allowed),
				(allies ? "Allies" : "Outsiders")
						+ (allowed ? " may now use blocks here." : " may no longer use blocks here."));
	}

	private static int setException(CommandContext<CommandSourceStack> context, boolean allies,
			String block, boolean present) throws CommandSyntaxException {
		return changeAccess(context, allies, access -> access.withException(block, present),
				(present ? "Added " : "Removed ") + block + " as an exception for "
						+ (allies ? "allies" : "outsiders") + ".");
	}

	private static int changeAccess(CommandContext<CommandSourceStack> context, boolean allies,
			java.util.function.UnaryOperator<Access> change, String message) throws CommandSyntaxException {
		String team = leaderTeam(context.getSource());

		if (team == null) {
			return 0;
		}

		TeamClaims.update(context.getSource().getServer(), team, settings -> allies
				? settings.withAllies(change.apply(settings.allies()))
				: settings.withOutsiders(change.apply(settings.outsiders())));

		context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	// Helpers -----------------------------------------------------------------------------------------

	/**
	 * The team this source is entitled to configure, or null after explaining why there isn't one.
	 *
	 * <p>An operator counts as a leader for every team. Without that, a team whose leader has left
	 * for good would be frozen with whatever settings it had, and the only way out would be to
	 * delete the team and its land with it.
	 */
	static @Nullable String leaderTeam(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlayerTeam team = player.getTeam();

		if (team == null) {
			source.sendFailure(Component.literal("You are not on a team."));
			return null;
		}

		Settings settings = TeamClaims.settings(source.getServer(), team.getName());

		if (settings.isLeader(player) || Permissions.check("gamemasters", source)) {
			return team.getName();
		}

		source.sendFailure(Component.literal(settings.leader().isPresent()
				? "Only your team's leader may change this."
				: "Your team has no leader yet. An operator names one with "
						+ "/overhaul claim leader " + team.getName() + " <player>."));
		return null;
	}

	private static String nameOf(CommandSourceStack source, java.util.UUID uuid) {
		ServerPlayer player = source.getServer().getPlayerList().getPlayer(uuid);
		return player != null ? player.getScoreboardName() : uuid.toString();
	}

	private static Component line(String label, String value) {
		return Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}
}
