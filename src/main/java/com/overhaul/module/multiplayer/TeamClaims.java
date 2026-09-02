package com.overhaul.module.multiplayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.overhaul.Overhaul;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * What each team allows on the land it owns, and who gets to decide.
 *
 * <p>Server-wide rather than per dimension, because teams are: a team that trusts another team
 * trusts them in the Nether too. Stored as a global data attachment, which is saved with the world
 * and so outlives a restart the way the claims themselves do.
 *
 * <p>Leadership is recorded here because vanilla has no such idea. A scoreboard team is a flat list
 * of names with no owner, so somebody has to be named one before there is anyone to ask about the
 * settings below. An operator does the naming once with {@code /overhaul claim leader}; after that
 * the team runs itself.
 */
public final class TeamClaims {
	/** What one class of visitor may do on a team's land. */
	public record Access(boolean build, boolean interact, List<String> interactExceptions) {
		public static final Codec<Access> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.fieldOf("build").forGetter(Access::build),
				Codec.BOOL.fieldOf("interact").forGetter(Access::interact),
				Codec.STRING.listOf().optionalFieldOf("interact_exceptions", List.of())
						.forGetter(Access::interactExceptions))
				.apply(instance, Access::new));

		public Access withBuild(boolean build) {
			return new Access(build, interact, interactExceptions);
		}

		public Access withInteract(boolean interact) {
			return new Access(build, interact, interactExceptions);
		}

		public Access withException(String id, boolean present) {
			List<String> updated = new ArrayList<>(interactExceptions);

			if (present) {
				if (!updated.contains(id)) {
					updated.add(id);
				}
			} else {
				updated.remove(id);
			}

			return new Access(build, interact, List.copyOf(updated));
		}
	}

	/**
	 * One team's rules.
	 *
	 * @param leader who may change any of this, or empty if an operator has not named one yet
	 * @param anyMemberMayClaim whether claiming is open to the team or reserved to the leader
	 * @param allyTeams teams treated as guests rather than strangers
	 */
	public record Settings(Optional<UUID> leader, boolean anyMemberMayClaim, List<String> allyTeams,
			Access outsiders, Access allies) {
		public static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUIDUtil.CODEC.optionalFieldOf("leader").forGetter(Settings::leader),
				Codec.BOOL.optionalFieldOf("any_member_may_claim", Boolean.TRUE)
						.forGetter(Settings::anyMemberMayClaim),
				Codec.STRING.listOf().optionalFieldOf("allies", List.of()).forGetter(Settings::allyTeams),
				Access.CODEC.fieldOf("outsiders").forGetter(Settings::outsiders),
				Access.CODEC.fieldOf("ally_access").forGetter(Settings::allies))
				.apply(instance, Settings::new));

		public Settings withLeader(@Nullable UUID leader) {
			return new Settings(Optional.ofNullable(leader), anyMemberMayClaim, allyTeams, outsiders, allies);
		}

		public Settings withAnyMemberMayClaim(boolean anyMemberMayClaim) {
			return new Settings(leader, anyMemberMayClaim, allyTeams, outsiders, allies);
		}

		public Settings withAlly(String team, boolean allied) {
			List<String> updated = new ArrayList<>(allyTeams);

			if (allied) {
				if (!updated.contains(team)) {
					updated.add(team);
				}
			} else {
				updated.remove(team);
			}

			return new Settings(leader, anyMemberMayClaim, List.copyOf(updated), outsiders, allies);
		}

		public Settings withOutsiders(Access outsiders) {
			return new Settings(leader, anyMemberMayClaim, allyTeams, outsiders, allies);
		}

		public Settings withAllies(Access allies) {
			return new Settings(leader, anyMemberMayClaim, allyTeams, outsiders, allies);
		}

		public boolean isLeader(Player player) {
			return leader.map(player.getUUID()::equals).orElse(Boolean.FALSE);
		}
	}

	private record Entry(String team, Settings settings) {
		private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("team").forGetter(Entry::team),
				Settings.CODEC.fieldOf("settings").forGetter(Entry::settings))
				.apply(instance, Entry::new));
	}

	private static final Codec<Map<String, Settings>> CODEC = Entry.CODEC.listOf().xmap(
			entries -> {
				Map<String, Settings> map = new LinkedHashMap<>();
				entries.forEach(entry -> map.put(entry.team(), entry.settings()));
				return map;
			},
			map -> map.entrySet().stream()
					.map(entry -> new Entry(entry.getKey(), entry.getValue()))
					.toList());

	public static final AttachmentType<Map<String, Settings>> BY_TEAM =
			AttachmentRegistry.create(Overhaul.id("team_claims"), builder -> builder
					.persistent(CODEC)
					.initializer(LinkedHashMap::new));

	private TeamClaims() {
	}

	/** Forces class initialisation, which is what registers the attachment type. */
	public static void init() {
	}

	/**
	 * A team's rules, falling back to the config defaults for a team nobody has configured.
	 *
	 * <p>The fallback is computed rather than written to disk, so a team that has never been
	 * touched keeps following the config: change the defaults later and every such team changes
	 * with them, while any team whose leader has decided something keeps their decision.
	 */
	public static Settings settings(MinecraftServer server, String team) {
		Map<String, Settings> byTeam = server.globalAttachments().getAttached(BY_TEAM);
		Settings stored = byTeam == null ? null : byTeam.get(team);
		return stored != null ? stored : defaults();
	}

	public static void update(MinecraftServer server, String team, UnaryOperator<Settings> change) {
		Map<String, Settings> byTeam = server.globalAttachments().getAttached(BY_TEAM);
		Map<String, Settings> updated = byTeam == null ? new LinkedHashMap<>() : new LinkedHashMap<>(byTeam);
		updated.put(team, change.apply(settings(server, team)));
		server.globalAttachments().setAttached(BY_TEAM, updated);
	}

	/** Drops a team's rules when the team itself is gone, so nothing is left pointing at a name. */
	public static void forget(MinecraftServer server, String team) {
		Map<String, Settings> byTeam = server.globalAttachments().getAttached(BY_TEAM);

		if (byTeam == null || !byTeam.containsKey(team)) {
			return;
		}

		Map<String, Settings> updated = new LinkedHashMap<>(byTeam);
		updated.remove(team);
		server.globalAttachments().setAttached(BY_TEAM, updated);
	}

	private static Settings defaults() {
		MultiplayerConfig.ClaimSettings config = MultiplayerModule.claimSettings();

		return new Settings(Optional.empty(), true, List.of(),
				access(config.outsiders), access(config.allies));
	}

	private static Access access(MultiplayerConfig.AccessDefaults defaults) {
		return new Access(defaults.build, defaults.interact, List.copyOf(defaults.interactExceptions));
	}
}
