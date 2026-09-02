package com.overhaul.module.multiplayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.overhaul.module.multiplayer.TeamClaims.Access;
import com.overhaul.module.multiplayer.TeamClaims.Settings;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

/**
 * The one question this module exists to answer: may this player do this here.
 *
 * <p>Every rule funnels through {@link #access}, which reduces a player and a position to the one
 * class of visitor they are — owner, ally or stranger — and hands back what that class may do.
 * Keeping the reduction in a single place is what stops the answer differing between breaking a
 * block, opening a chest and blowing something up, which is exactly the kind of gap a claim system
 * is judged on.
 *
 * <p>The default answer everywhere is yes. An unclaimed chunk, a disabled module, a player with
 * moderation permissions and a world with no teams in it all fall straight through, so installing
 * this module changes nothing at all until somebody claims something.
 */
public final class Protection {
	/** Owners may do anything on their own land, so they get an access with no restrictions. */
	private static final Access UNRESTRICTED = new Access(true, true, List.of());

	/**
	 * Last time each player was told they could not do something, so that holding down right-click
	 * on a locked door produces one line rather than one a tick.
	 */
	private static final Map<UUID, Long> LAST_TOLD = new HashMap<>();

	private static final long MESSAGE_COOLDOWN_TICKS = 40L;

	private Protection() {
	}

	// The questions ------------------------------------------------------------------------------

	public static boolean mayBuild(Player player, Level level, BlockPos pos) {
		return !(level instanceof ServerLevel server) || access(player, server, pos).build();
	}

	public static boolean mayInteract(Player player, Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel server)) {
			return true;
		}

		Access access = access(player, server, pos);
		return access.interact() != matchesBlock(access.interactExceptions(), state);
	}

	/** Right-clicking an item frame, an armour stand, a minecart or an animal inside a claim. */
	public static boolean mayInteractWith(Player player, Entity target) {
		if (!(target.level() instanceof ServerLevel server) || !protectsEntities()) {
			return true;
		}

		Access access = access(player, server, target.blockPosition());
		return access.interact() != matchesEntity(access.interactExceptions(), target);
	}

	/** Hitting one. Counted as building, because for a painting or an item frame it is breaking it. */
	public static boolean mayAttack(Player player, Entity target) {
		if (!(target.level() instanceof ServerLevel server) || !protectsEntities()) {
			return true;
		}

		// A player fighting a mob that wandered into someone's claim is not vandalism, and a claim
		// that stopped it would be a place monsters could safely stand.
		if (target instanceof net.minecraft.world.entity.Mob && !(target instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
			return true;
		}

		return access(player, server, target.blockPosition()).build();
	}

	/**
	 * Whether an explosion is allowed to take this block.
	 *
	 * <p>No player to ask about, which is the point: an explosion has a cause but not an author the
	 * claim can reason about, so a claimed chunk simply does not lose blocks to one.
	 */
	public static boolean explosionMayBreak(ServerLevel level, BlockPos pos) {
		if (!MultiplayerModule.claimsActive() || !MultiplayerModule.claimSettings().protectFromExplosions) {
			return true;
		}

		return Claims.ownerAt(level, pos) == null;
	}

	// Working out who someone is ------------------------------------------------------------------

	/**
	 * What this player may do at this position.
	 *
	 * <p>Ownership is by team rather than by player on purpose: a claim that belonged to whoever
	 * placed the banner would leave a team unable to build on its own base the moment that person
	 * stopped playing.
	 */
	private static Access access(Player player, ServerLevel level, BlockPos pos) {
		if (!MultiplayerModule.claimsActive() || bypasses(player)) {
			return UNRESTRICTED;
		}

		String owner = Claims.ownerOf(level, ChunkPos.containing(pos));

		if (owner == null) {
			return UNRESTRICTED;
		}

		PlayerTeam team = player.getTeam();

		if (team != null && team.getName().equals(owner)) {
			return UNRESTRICTED;
		}

		Settings settings = TeamClaims.settings(level.getServer(), owner);

		if (team != null && settings.allyTeams().contains(team.getName())) {
			return settings.allies();
		}

		return settings.outsiders();
	}

	private static boolean bypasses(Player player) {
		MultiplayerConfig.ClaimSettings settings = MultiplayerModule.claimSettings();

		if (settings.creativeBypasses && player.hasInfiniteMaterials()) {
			return true;
		}

		return player instanceof ServerPlayer server && Permissions.check(settings.bypassPermission, server);
	}

	private static boolean protectsEntities() {
		return MultiplayerModule.claimsActive() && MultiplayerModule.claimSettings().protectEntities;
	}

	// Exception matching ---------------------------------------------------------------------------

	private static boolean matchesBlock(List<String> exceptions, BlockState state) {
		if (exceptions.isEmpty()) {
			return false;
		}

		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());

		for (String entry : exceptions) {
			if (entry.startsWith("#")) {
				if (state.is(TagKey.create(Registries.BLOCK, Identifier.parse(entry.substring(1))))) {
					return true;
				}
			} else if (id.toString().equals(entry)) {
				return true;
			}
		}

		return false;
	}

	private static boolean matchesEntity(List<String> exceptions, Entity entity) {
		if (exceptions.isEmpty()) {
			return false;
		}

		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

		for (String entry : exceptions) {
			if (entry.startsWith("#")) {
				if (entity.getType().builtInRegistryHolder()
						.is(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(entry.substring(1))))) {
					return true;
				}
			} else if (id.toString().equals(entry)) {
				return true;
			}
		}

		return false;
	}

	// Telling the player why ------------------------------------------------------------------------

	/**
	 * Says whose land refused, at most once every couple of seconds.
	 *
	 * <p>A silent refusal is the worst thing a claim system can do — it reads as the game being
	 * broken rather than as a rule — but a refusal repeated every tick while somebody leans on a
	 * door is nearly as bad.
	 */
	public static void refuse(Player player, ServerLevel level, BlockPos pos) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		long now = level.getGameTime();
		Long told = LAST_TOLD.get(player.getUUID());

		if (told != null && now - told < MESSAGE_COOLDOWN_TICKS) {
			return;
		}

		LAST_TOLD.put(player.getUUID(), now);

		String owner = Claims.ownerOf(level, ChunkPos.containing(pos));
		Component name = owner == null ? Component.literal("another team") : teamName(level, owner);

		serverPlayer.sendOverlayMessage(Component.literal("This chunk belongs to ")
				.withStyle(ChatFormatting.RED).append(name));
	}

	/** A team's display name if the team still exists, or its bare id if it does not. */
	static Component teamName(ServerLevel level, String team) {
		PlayerTeam found = level.getScoreboard().getPlayerTeam(team);
		return found == null ? Component.literal(team) : found.getDisplayName();
	}

	static void forget(UUID player) {
		LAST_TOLD.remove(player);
	}
}
