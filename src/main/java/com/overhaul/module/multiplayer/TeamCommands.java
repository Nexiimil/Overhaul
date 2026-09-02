package com.overhaul.module.multiplayer;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

/**
 * {@code /overhaul claim team} — forming and running a team without an operator.
 *
 * <p>Vanilla's {@code /team} is gated at gamemaster level on its root literal, so every subcommand
 * of it, including simply joining, needs operator permissions. Left at that, claims would not be a
 * player feature at all: an operator would have to run a command for every player joining every
 * team, forever.
 *
 * <p>These do the same work on the same scoreboard teams — there is still exactly one idea of who
 * is with whom — but gate it on being that team's leader instead. {@code /team} keeps its operator
 * requirement for everything else it can do, which matters, because it can also delete other
 * people's teams and now that takes their land with it.
 */
public final class TeamCommands {
	private TeamCommands() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> subtree() {
		return Commands.literal("team")
				.then(Commands.literal("create")
						.then(Commands.argument("name", StringArgumentType.word())
								.executes(context -> create(context.getSource(),
										StringArgumentType.getString(context, "name")))))
				.then(Commands.literal("invite")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(TeamCommands::invite)))
				.then(Commands.literal("invites")
						.executes(context -> listInvites(context.getSource())))
				.then(Commands.literal("join")
						.then(Commands.argument("team", StringArgumentType.word())
								.suggests(TeamCommands::suggestInvites)
								.executes(context -> join(context.getSource(),
										StringArgumentType.getString(context, "team")))))
				.then(Commands.literal("leave").executes(context -> leave(context.getSource())))
				.then(Commands.literal("kick")
						.then(Commands.argument("member", StringArgumentType.word())
								.suggests(TeamCommands::suggestMembers)
								.executes(context -> kick(context.getSource(),
										StringArgumentType.getString(context, "member")))))
				.then(Commands.literal("transfer")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(TeamCommands::transfer)))
				.then(Commands.literal("disband")
						.then(Commands.argument("name", StringArgumentType.word())
								.suggests(TeamCommands::suggestOwnTeam)
								.executes(context -> disband(context.getSource(),
										StringArgumentType.getString(context, "name")))));
	}

	// Forming one ---------------------------------------------------------------------------------

	/**
	 * Makes a team and hands it to whoever made it.
	 *
	 * <p>The founder becoming the leader is not the same rule as "whoever claimed first owns it":
	 * there is no question about who a team belongs to at the moment somebody types its name into
	 * existence. An operator can still reassign it afterwards with {@code /overhaul claim leader}.
	 */
	private static int create(CommandSourceStack source, String name) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MultiplayerConfig.ClaimSettings settings = MultiplayerModule.claimSettings();

		if (!settings.playersMayCreateTeams) {
			source.sendFailure(Component.literal("Teams are made by operators on this server. Ask one to "
					+ "run /team add and /overhaul claim leader for you."));
			return 0;
		}

		if (player.getTeam() != null) {
			source.sendFailure(Component.literal("You are already on a team. Leave it first."));
			return 0;
		}

		String problem = validate(name, settings);

		if (problem != null) {
			source.sendFailure(Component.literal(problem));
			return 0;
		}

		Scoreboard scoreboard = source.getServer().getScoreboard();

		if (scoreboard.getPlayerTeam(name) != null) {
			source.sendFailure(Component.literal("A team called " + name + " already exists."));
			return 0;
		}

		PlayerTeam team = scoreboard.addPlayerTeam(name);
		scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
		TeamClaims.update(source.getServer(), name, stored -> stored.withLeader(player.getUUID()));

		source.sendSuccess(() -> Component.literal("Created ").withStyle(ChatFormatting.GREEN)
				.append(team.getDisplayName())
				.append(Component.literal(" and put you in charge of it. Invite people with "
						+ "/overhaul claim team invite.").withStyle(ChatFormatting.GREEN)), false);
		return 1;
	}

	/** @return what is wrong with the name, or null if nothing is */
	private static @Nullable String validate(String name, MultiplayerConfig.ClaimSettings settings) {
		if (name.isBlank()) {
			return "A team needs a name.";
		}

		if (name.length() > Math.max(1, settings.maxTeamNameLength)) {
			return "That name is longer than " + settings.maxTeamNameLength + " characters.";
		}

		// The name is an id as well as a label: it goes into the claim records and into every
		// command that names the team, so it has to survive being typed as one word.
		if (!name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '-')) {
			return "Team names may only use letters, digits, underscores and hyphens.";
		}

		return null;
	}

	// Membership ------------------------------------------------------------------------------------

	private static int invite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		String team = ClaimCommands.leaderTeam(source);

		if (team == null) {
			return 0;
		}

		ServerPlayer invitee = EntityArgument.getPlayer(context, "player");

		if (invitee.getTeam() != null) {
			source.sendFailure(Component.literal(invitee.getScoreboardName() + " is already on a team."));
			return 0;
		}

		PlayerTeam existing = source.getServer().getScoreboard().getPlayerTeam(team);
		int max = MultiplayerModule.claimSettings().maxTeamSize;

		if (existing != null && max > 0 && existing.getPlayers().size() >= max) {
			source.sendFailure(Component.literal("Your team is full at " + max + " members."));
			return 0;
		}

		TeamInvites.offer(source.getServer(), invitee.getUUID(), team);

		invitee.sendSystemMessage(Component.literal(source.getTextName() + " invited you to ")
				.withStyle(ChatFormatting.GREEN)
				.append(existing == null ? Component.literal(team) : existing.getDisplayName())
				.append(Component.literal(". Accept with /overhaul claim team join " + team)
						.withStyle(ChatFormatting.GRAY)));

		source.sendSuccess(() -> Component.literal("Invited " + invitee.getScoreboardName() + ".")
				.withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int listInvites(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		List<String> invites = TeamInvites.pending(source.getServer(), player.getUUID());

		if (invites.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Nobody has invited you anywhere."), false);
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Invitations: " + String.join(", ", invites))
				.withStyle(ChatFormatting.GOLD), false);
		return invites.size();
	}

	private static int join(CommandSourceStack source, String name) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();

		if (player.getTeam() != null) {
			source.sendFailure(Component.literal("You are already on a team. Leave it first."));
			return 0;
		}

		PlayerTeam team = source.getServer().getScoreboard().getPlayerTeam(name);

		if (team == null) {
			source.sendFailure(Component.literal("There is no team called " + name + "."));
			return 0;
		}

		if (!TeamInvites.redeem(source.getServer(), player.getUUID(), name)) {
			source.sendFailure(Component.literal("You have no standing invitation to " + name
					+ ". Invitations lapse after a while."));
			return 0;
		}

		source.getServer().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team);
		announce(source.getServer(), team, Component.literal(player.getScoreboardName() + " joined ")
				.withStyle(ChatFormatting.GREEN).append(team.getDisplayName()));
		return 1;
	}

	/**
	 * Leaving, with the one case that cannot simply be allowed: a leader walking out of a team that
	 * still has people in it would leave them with land nobody can configure and no way to name a
	 * new leader without an operator.
	 */
	private static int leave(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlayerTeam team = player.getTeam();

		if (team == null) {
			source.sendFailure(Component.literal("You are not on a team."));
			return 0;
		}

		boolean leader = TeamClaims.settings(source.getServer(), team.getName()).isLeader(player);

		if (leader && team.getPlayers().size() > 1) {
			source.sendFailure(Component.literal("You lead this team. Hand it on with /overhaul claim "
					+ "team transfer, or close it with /overhaul claim team disband " + team.getName() + "."));
			return 0;
		}

		if (leader) {
			// The last one out: leaving an empty team standing would leave its claims held by
			// nobody, which is exactly what disbanding is for.
			return disband(source, team.getName());
		}

		source.getServer().getScoreboard().removePlayerFromTeam(player.getScoreboardName(), team);
		source.sendSuccess(() -> Component.literal("You left ").withStyle(ChatFormatting.GREEN)
				.append(team.getDisplayName()), false);
		return 1;
	}

	private static int kick(CommandSourceStack source, String member) throws CommandSyntaxException {
		String name = ClaimCommands.leaderTeam(source);

		if (name == null) {
			return 0;
		}

		PlayerTeam team = source.getServer().getScoreboard().getPlayerTeam(name);

		if (team == null || !team.getPlayers().contains(member)) {
			source.sendFailure(Component.literal(member + " is not on your team."));
			return 0;
		}

		if (member.equals(source.getTextName())) {
			source.sendFailure(Component.literal("Use /overhaul claim team leave for that."));
			return 0;
		}

		source.getServer().getScoreboard().removePlayerFromTeam(member, team);
		source.sendSuccess(() -> Component.literal("Removed " + member + " from ")
				.withStyle(ChatFormatting.GREEN).append(team.getDisplayName()), false);
		return 1;
	}

	private static int transfer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		String name = ClaimCommands.leaderTeam(source);

		if (name == null) {
			return 0;
		}

		ServerPlayer heir = EntityArgument.getPlayer(context, "player");
		PlayerTeam team = source.getServer().getScoreboard().getPlayerTeam(name);

		if (team == null || !team.getPlayers().contains(heir.getScoreboardName())) {
			source.sendFailure(Component.literal(heir.getScoreboardName() + " is not on your team."));
			return 0;
		}

		TeamClaims.update(source.getServer(), name, settings -> settings.withLeader(heir.getUUID()));
		announce(source.getServer(), team, Component.literal(heir.getScoreboardName() + " now leads ")
				.withStyle(ChatFormatting.GREEN).append(team.getDisplayName()));
		return 1;
	}

	/**
	 * Closes a team down, and everything it owned with it.
	 *
	 * <p>The name has to be typed out. Disbanding gives up every chunk the team holds in every
	 * dimension at once, and that is not something anyone should be able to do by pressing up-enter
	 * on the wrong command.
	 */
	private static int disband(CommandSourceStack source, String name) throws CommandSyntaxException {
		String team = ClaimCommands.leaderTeam(source);

		if (team == null) {
			return 0;
		}

		if (!team.equals(name)) {
			source.sendFailure(Component.literal("Type your team's name to confirm: /overhaul claim team "
					+ "disband " + team));
			return 0;
		}

		PlayerTeam existing = source.getServer().getScoreboard().getPlayerTeam(team);

		if (existing == null) {
			source.sendFailure(Component.literal("There is no team called " + team + "."));
			return 0;
		}

		// Removing the team is what releases its land: the scoreboard mixin hears about it and
		// clears the claims, so there is nothing to undo here if that changes.
		announce(source.getServer(), existing, Component.literal("Team ").withStyle(ChatFormatting.YELLOW)
				.append(existing.getDisplayName())
				.append(Component.literal(" has been disbanded and its land released.")
						.withStyle(ChatFormatting.YELLOW)));
		source.getServer().getScoreboard().removePlayerTeam(existing);
		return 1;
	}

	// Helpers -----------------------------------------------------------------------------------------

	/** Tells everyone on a team something that happened to it, wherever they are. */
	private static void announce(MinecraftServer server, PlayerTeam team, Component message) {
		Collection<String> members = List.copyOf(team.getPlayers());

		for (String member : members) {
			ServerPlayer player = server.getPlayerList().getPlayerByName(member);

			if (player != null) {
				player.sendSystemMessage(message);
			}
		}
	}

	private static CompletableFuture<Suggestions> suggestInvites(CommandContext<CommandSourceStack> context,
			SuggestionsBuilder builder) {
		ServerPlayer player = context.getSource().getPlayer();
		return player == null
				? Suggestions.empty()
				: SharedSuggestionProvider.suggest(
						TeamInvites.pending(context.getSource().getServer(), player.getUUID()), builder);
	}

	private static CompletableFuture<Suggestions> suggestMembers(CommandContext<CommandSourceStack> context,
			SuggestionsBuilder builder) {
		ServerPlayer player = context.getSource().getPlayer();
		PlayerTeam team = player == null ? null : player.getTeam();

		if (team == null) {
			return Suggestions.empty();
		}

		return SharedSuggestionProvider.suggest(team.getPlayers().stream()
				.filter(member -> !member.equals(player.getScoreboardName()))
				.toList(), builder);
	}

	private static CompletableFuture<Suggestions> suggestOwnTeam(CommandContext<CommandSourceStack> context,
			SuggestionsBuilder builder) {
		ServerPlayer player = context.getSource().getPlayer();
		PlayerTeam team = player == null ? null : player.getTeam();
		return team == null ? Suggestions.empty()
				: SharedSuggestionProvider.suggest(List.of(team.getName()), builder);
	}
}
