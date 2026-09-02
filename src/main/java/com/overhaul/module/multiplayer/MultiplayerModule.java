package com.overhaul.module.multiplayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.overhaul.core.ModuleManager;
import com.overhaul.core.OverhaulModule;
import com.overhaul.core.config.ConfigManager;
import com.overhaul.core.config.RecipeSpec;
import com.overhaul.core.data.DataPackBuilder;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.BlockEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

/**
 * Land that belongs to somebody, and chunks that stay awake.
 *
 * <p>Claims are built on vanilla teams rather than on a group system of their own. A server that
 * wants claims already has teams — for name colours, for friendly fire, for the scoreboard — and a
 * second, parallel idea of "who is with whom" would immediately disagree with the first. Building
 * on {@code /team} means the answer to "are we together" has exactly one source, and a server that
 * has never used teams gets claims that are simply inert.
 *
 * <p>Everything here is off by default in the sense that matters: with no claims placed, every
 * check falls through to yes. The module only starts saying no once somebody has said this is mine.
 */
public class MultiplayerModule implements OverhaulModule {
	private static @Nullable MultiplayerConfig config;

	/** Where each player was last time we looked, so entering a claim can be announced once. */
	private static final Map<UUID, ChunkPos> LAST_CHUNK = new HashMap<>();

	private static final int ANNOUNCE_INTERVAL_TICKS = 10;

	private int sinceAnnounceCheck;

	public static MultiplayerConfig.ClaimSettings claimSettings() {
		MultiplayerConfig loaded = config;
		return loaded == null ? new MultiplayerConfig().claims : loaded.claims;
	}

	public static MultiplayerConfig.ChunkLoaderSettings chunkLoaderSettings() {
		MultiplayerConfig loaded = config;
		return loaded == null ? new MultiplayerConfig().chunkLoaders : loaded.chunkLoaders;
	}

	/**
	 * Whether claims are doing anything at all.
	 *
	 * <p>Checked from mixins as well as from this module's own listeners, so it has to answer for
	 * the module being switched off rather than assuming it is on.
	 */
	public static boolean claimsActive() {
		return config != null && config.claims.enabled && ModuleManager.isEnabled("multiplayer");
	}

	@Override
	public String id() {
		return "multiplayer";
	}

	@Override
	public String displayName() {
		return "Multiplayer Module";
	}

	@Override
	public void loadConfig() {
		MultiplayerConfig loaded = ConfigManager.load(id(), MultiplayerConfig.class);
		ConfigManager.save(id(), loaded);
		config = loaded;
	}

	@Override
	public void registerContent() {
		if (config.claims.enabled) {
			// Touching these registers their attachment types before the first world loads.
			Claims.init();
			TeamClaims.init();
		}

		if (config.chunkLoaders.enabled) {
			ChunkLoaders.init();
			MultiplayerContent.register();
		}
	}

	@Override
	public void registerBehaviour() {
		if (config.chunkLoaders.enabled) {
			registerChunkLoaders();
		}

		if (!config.claims.enabled) {
			return;
		}

		ClaimCommands.register();
		registerProtection();

		// Both maps are keyed by player and would otherwise keep growing for the life of the server.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forgetPlayer(handler.getPlayer().getUUID()));

		if (config.claims.announceOnEnter) {
			ServerTickEvents.END_SERVER_TICK.register(this::announceCrossings);
		}
	}

	// Chunk loaders ----------------------------------------------------------------------------

	private void registerChunkLoaders() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			if (MultiplayerContent.chunkLoaderItem() != null) {
				output.accept(MultiplayerContent.chunkLoaderItem());
			}
		});

		ServerLevelEvents.LOAD.register((server, level) -> ChunkLoaders.refresh(level));
	}

	// Protection -------------------------------------------------------------------------------

	/**
	 * Four ways to change somebody's land, all of them covered.
	 *
	 * <p>Breaking and placing are the obvious pair. The other two are the ones a claim system is
	 * actually judged on: opening a chest is not breaking anything, and neither is riding away on
	 * somebody's minecart, but a claim that allowed either would not be worth placing.
	 */
	private void registerProtection() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (Protection.mayBuild(player, level, pos)) {
				return true;
			}

			refuse(player, level, pos);
			return false;
		});

		// Left-clicking is where a player finds out, since breaking starts long before it finishes.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (Protection.mayBuild(player, level, pos)) {
				return InteractionResult.PASS;
			}

			refuse(player, level, pos);
			return InteractionResult.FAIL;
		});

		// Using an item on a block: placing one, but also bone meal, buckets, hoes and flint.
		// All of them change the world, so all of them are building.
		ItemEvents.USE_ON.register(context -> {
			BlockPos pos = context.getClickedPos();

			if (context.getPlayer() == null || Protection.mayBuild(context.getPlayer(), context.getLevel(), pos)) {
				return null;
			}

			refuse(context.getPlayer(), context.getLevel(), pos);
			return InteractionResult.FAIL;
		});

		BlockEvents.USE_WITHOUT_ITEM.register((state, level, pos, player, hit) ->
				interaction(player, level, pos, state));

		BlockEvents.USE_ITEM_ON.register((stack, state, level, pos, player, hand, hit) ->
				interaction(player, level, pos, state));

		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (Protection.mayInteractWith(player, entity)) {
				return InteractionResult.PASS;
			}

			refuse(player, level, entity.blockPosition());
			return InteractionResult.FAIL;
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (Protection.mayAttack(player, entity)) {
				return InteractionResult.PASS;
			}

			refuse(player, level, entity.blockPosition());
			return InteractionResult.FAIL;
		});
	}

	private static @Nullable InteractionResult interaction(net.minecraft.world.entity.player.Player player,
			net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
		if (Protection.mayInteract(player, level, pos, state)) {
			return null;
		}

		refuse(player, level, pos);
		return InteractionResult.FAIL;
	}

	private static void refuse(net.minecraft.world.entity.player.Player player,
			net.minecraft.world.level.Level level, BlockPos pos) {
		if (level instanceof ServerLevel server) {
			Protection.refuse(player, server, pos);
		}
	}

	// Banners ------------------------------------------------------------------------------------

	/**
	 * A banner planted in an unclaimed chunk claims it.
	 *
	 * <p>A banner because it is the thing in vanilla that already means "this is ours", and it
	 * leaves a visible marker standing on the land afterwards. Breaking it does not release the
	 * chunk: several banners in one claim is a normal thing to build, and land that unclaimed
	 * itself when any one of them came down would be land nobody could decorate.
	 */
	public static void onBannerPlaced(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		if (!claimsActive() || !claimSettings().claimWithBanner || !state.is(BlockTags.BANNERS)) {
			return;
		}

		ChunkPos chunk = ChunkPos.containing(pos);
		String owner = Claims.ownerOf(level, chunk);
		PlayerTeam team = player.getTeam();

		// Silent when the chunk is already theirs — a team decorating its own base does not want a
		// line of chat per banner.
		if (owner != null && team != null && owner.equals(team.getName())) {
			return;
		}

		Claiming.Result result = Claiming.claim(player, level, chunk);

		player.sendOverlayMessage(result.claimed()
				? Component.literal("Claimed chunk " + chunk.x() + ", " + chunk.z() + " for your team.")
						.withStyle(ChatFormatting.GREEN)
				: Component.literal(result.reason()).withStyle(ChatFormatting.YELLOW));
	}

	// Walking around --------------------------------------------------------------------------------

	/**
	 * Says whose land you have just walked into, on the action bar.
	 *
	 * <p>Claims are invisible, which makes them feel arbitrary the first time one refuses you.
	 * Being told at the boundary turns the refusal into something you saw coming.
	 */
	private void announceCrossings(net.minecraft.server.MinecraftServer server) {
		if (++sinceAnnounceCheck < ANNOUNCE_INTERVAL_TICKS) {
			return;
		}

		sinceAnnounceCheck = 0;
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		LAST_CHUNK.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);

		for (ServerPlayer player : players) {
			ServerLevel level = player.level();
			ChunkPos chunk = ChunkPos.containing(player.blockPosition());
			ChunkPos previous = LAST_CHUNK.put(player.getUUID(), chunk);

			if (chunk.equals(previous)) {
				continue;
			}

			String owner = Claims.ownerOf(level, chunk);
			String before = previous == null ? null : Claims.ownerOf(level, previous);

			if (java.util.Objects.equals(owner, before)) {
				continue;
			}

			player.sendOverlayMessage(owner == null
					? Component.literal("Wilderness").withStyle(ChatFormatting.GRAY)
					: Component.literal("Entering ").withStyle(ChatFormatting.GRAY)
							.append(Protection.teamName(level, owner)));
		}
	}

	// Data --------------------------------------------------------------------------------------

	@Override
	public void buildRecipes(DataPackBuilder pack) {
		if (!config.chunkLoaders.enabled || MultiplayerContent.chunkLoader() == null) {
			return;
		}

		// The enchanting table's shape, with every ingredient replaced by the expensive version of
		// itself. Reading as "an enchanting table, but" is the point: it tells you what it costs
		// before you have looked it up.
		pack.addRecipe("chunk_loader", RecipeSpec.shaped("overhaul:chunk_loader", 1,
				List.of(" E ", "NON", "OOO"),
				Map.of("E", "minecraft:ender_eye", "N", "minecraft:nether_star", "O", "minecraft:obsidian"))
				.category("redstone"));

		pack.addSelfDropLootTable(com.overhaul.Overhaul.id("chunk_loader"));
	}

	/**
	 * Called when a team is deleted, from the vanilla command that deletes it.
	 *
	 * <p>Land held by a team that no longer exists would be unbuildable by everyone and releasable
	 * by nobody, so the claims go with the team rather than outliving it.
	 */
	public static void onTeamRemoved(net.minecraft.server.MinecraftServer server, String team) {
		if (!claimsActive()) {
			return;
		}

		int released = Claims.forgetTeam(server.getAllLevels(), team);
		TeamClaims.forget(server, team);

		if (released > 0) {
			com.overhaul.Overhaul.LOGGER.info("Team '{}' was removed; released {} claimed chunk(s)",
					team, released);
		}
	}

	private static void forgetPlayer(UUID player) {
		LAST_CHUNK.remove(player);
		Protection.forget(player);
		TeamInvites.forget(player);
	}
}
