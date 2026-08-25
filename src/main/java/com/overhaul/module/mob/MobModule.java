package com.overhaul.module.mob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.overhaul.Overhaul;
import com.overhaul.core.ModuleManager;
import com.overhaul.core.OverhaulModule;
import com.overhaul.core.config.ConfigManager;
import com.overhaul.mixin.MobGoalAccess;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Faction-aware, individually tuned mobs.
 *
 * <p>The teams are the load-bearing change. Vanilla mobs that clip each other with an arrow or an
 * explosion turn on one another, which quietly defuses most large fights; keeping a faction from
 * hurting itself means a horde stays a horde. Everything else gives the individual mobs a shape:
 * zombies vary in speed so a group arrives strung out, skeletons trade health for reach, and
 * anything badly wounded tries to leave rather than trading blows to the end.
 */
public class MobModule implements OverhaulModule {
	/** Identifiers for our attribute modifiers, so re-applying on chunk reload replaces cleanly. */
	private static final Identifier SPEED_MODIFIER = Overhaul.id("mob_speed");
	private static final Identifier HEALTH_MODIFIER = Overhaul.id("mob_health");
	private static final Identifier RANGE_MODIFIER = Overhaul.id("mob_follow_range");

	private static @Nullable MobConfig config;

	/** Entity id to team name, flattened from the config once at startup. */
	private static final Map<String, String> TEAM_OF = new HashMap<>();

	private static final Map<java.util.UUID, HelpRecord> HELP_CALLS = new HashMap<>();

	private record HelpRecord(int summoned, long lastCallTick) {
	}

	public static @Nullable MobConfig config() {
		return config;
	}

	/**
	 * Mixins run whether or not the module is on, because they are woven into vanilla classes at
	 * load time. Every entry point they call checks this first so a disabled module really is inert.
	 */
	private static boolean active() {
		return config != null && ModuleManager.isEnabled("mob");
	}

	@Override
	public String id() {
		return "mob";
	}

	@Override
	public String displayName() {
		return "Mob Module";
	}

	@Override
	public void loadConfig() {
		MobConfig loaded = ConfigManager.load(id(), MobConfig.class);
		MobDefaults.fill(loaded);
		ConfigManager.save(id(), loaded);
		config = loaded;

		TEAM_OF.clear();
		loaded.teams.members.forEach((team, members) -> members.forEach(member -> TEAM_OF.put(member, team)));
	}

	@Override
	public void registerBehaviour() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(this::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(this::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(this::afterDeath);
		ServerEntityEvents.ENTITY_LOAD.register(this::onEntityLoad);
	}

	// Teams --------------------------------------------------------------------------------------

	/** @return the team an entity belongs to, or null if it is unaffiliated */
	public static @Nullable String teamOf(Entity entity) {
		if (!active() || !config.teams.enabled) {
			return null;
		}

		return TEAM_OF.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
	}

	public static boolean sameTeam(@Nullable Entity a, @Nullable Entity b) {
		if (a == null || b == null || a == b) {
			return false;
		}

		String team = teamOf(a);
		return team != null && team.equals(teamOf(b));
	}

	/** Used by the targeting mixin as well as by the damage event. */
	public static boolean preventsTargeting() {
		return active() && config.teams.enabled && config.teams.preventTargeting;
	}

	private boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (config.teams.friendlyFire || !config.teams.enabled) {
			return true;
		}

		// The direct entity is the arrow or fireball; the causing entity is whoever fired it, and
		// that is the one whose allegiance matters.
		Entity attacker = source.getEntity();
		return !sameTeam(attacker, entity);
	}

	// Per-mob behaviour --------------------------------------------------------------------------

	private void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damage, boolean blocked) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		if (config.zombies.enabled && entity instanceof Zombie zombie) {
			callForHelp(level, zombie, source);
		}

		if (config.spiders.enabled && entity instanceof Spider spider && config.spiders.webOnHurtChance > 0.0F
				&& entity.getHealth() < entity.getMaxHealth() * 0.5F
				&& level.getRandom().nextFloat() < config.spiders.webOnHurtChance) {
			BlockPos pos = spider.blockPosition();

			if (level.getBlockState(pos).isAir()) {
				level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
			}
		}
	}

	/**
	 * A wounded zombie drags one of its own up out of the ground nearby. The count and cooldown
	 * are tracked per zombie so a long fight does not turn into an unbounded spawner.
	 */
	private void callForHelp(ServerLevel level, Zombie zombie, DamageSource source) {
		if (config.zombies.callForHelpChance <= 0.0F || config.zombies.maxHelpers <= 0) {
			return;
		}

		if (level.getRandom().nextFloat() >= config.zombies.callForHelpChance) {
			return;
		}

		HelpRecord record = HELP_CALLS.getOrDefault(zombie.getUUID(), new HelpRecord(0, Long.MIN_VALUE));

		if (record.summoned() >= config.zombies.maxHelpers
				|| level.getGameTime() - record.lastCallTick() < config.zombies.helpCooldownTicks) {
			return;
		}

		BlockPos spot = findGround(level, zombie.blockPosition(), config.zombies.helpSearchRadius);

		if (spot == null) {
			return;
		}

		Zombie helper = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.REINFORCEMENT);

		if (helper == null) {
			return;
		}

		helper.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		helper.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.REINFORCEMENT, null);

		if (source.getEntity() instanceof LivingEntity attacker) {
			helper.setTarget(attacker);
		}

		level.addFreshEntity(helper);
		level.levelEvent(2001, spot, net.minecraft.world.level.block.Block.getId(level.getBlockState(spot.below())));

		HELP_CALLS.put(zombie.getUUID(), new HelpRecord(record.summoned() + 1, level.getGameTime()));
	}

	/** Finds a spot with air to stand in and solid ground under it, near the given position. */
	private static @Nullable BlockPos findGround(ServerLevel level, BlockPos origin, int radius) {
		for (int attempt = 0; attempt < 12; attempt++) {
			BlockPos candidate = origin.offset(
					level.getRandom().nextInt(radius * 2 + 1) - radius,
					level.getRandom().nextInt(3) - 1,
					level.getRandom().nextInt(radius * 2 + 1) - radius);

			BlockState below = level.getBlockState(candidate.below());

			if (level.getBlockState(candidate).isAir()
					&& level.getBlockState(candidate.above()).isAir()
					&& below.isSolidRender()) {
				return candidate;
			}
		}

		return null;
	}

	private void afterDeath(LivingEntity entity, DamageSource source) {
		HELP_CALLS.remove(entity.getUUID());

		if (!config.zombies.enabled || config.zombies.riseAsSkeletonChance <= 0.0F) {
			return;
		}

		if (!(entity instanceof Zombie) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		if (level.getRandom().nextFloat() >= config.zombies.riseAsSkeletonChance) {
			return;
		}

		Skeleton risen = EntityTypes.SKELETON.create(level, EntitySpawnReason.CONVERSION);

		if (risen == null) {
			return;
		}

		risen.snapTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
		risen.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.CONVERSION, null);

		if (source.getEntity() instanceof LivingEntity killer) {
			risen.setTarget(killer);
		}

		level.addFreshEntity(risen);
		level.levelEvent(1009, entity.blockPosition(), 0);
	}

	// Attributes and goals -----------------------------------------------------------------------

	private void onEntityLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof Mob mob)) {
			return;
		}

		if (config.zombies.enabled && mob instanceof Zombie) {
			applySpeedVariance(mob);
		}

		if (config.skeletons.enabled && mob instanceof AbstractSkeleton) {
			applySkeletonTuning(mob);
		}

		if (config.fleeing.enabled && mob instanceof PathfinderMob pathfinder) {
			addFleeGoal(pathfinder);
		}
	}

	/**
	 * Seeds the random from the mob's own id, so a zombie keeps the speed it was born with across
	 * chunk unloads instead of being re-rolled every time it comes back into view.
	 */
	private void applySpeedVariance(Mob mob) {
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed == null) {
			return;
		}

		float min = config.zombies.minSpeedMultiplier;
		float max = Math.max(min, config.zombies.maxSpeedMultiplier);
		float roll = min + new Random(mob.getUUID().getLeastSignificantBits()).nextFloat() * (max - min);

		speed.addOrReplacePermanentModifier(
				new AttributeModifier(SPEED_MODIFIER, roll - 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}

	private void applySkeletonTuning(Mob mob) {
		multiply(mob, Attributes.MAX_HEALTH, HEALTH_MODIFIER, config.skeletons.healthMultiplier);
		multiply(mob, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, config.skeletons.speedMultiplier);
		multiply(mob, Attributes.FOLLOW_RANGE, RANGE_MODIFIER, config.skeletons.followRangeMultiplier);

		// Cutting max health leaves the current value above the new maximum until something clamps
		// it, so bring it down here rather than showing a skeleton at 20/13 hearts.
		if (mob.getHealth() > mob.getMaxHealth()) {
			mob.setHealth(mob.getMaxHealth());
		}
	}

	private static void multiply(Mob mob, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			Identifier id, float multiplier) {
		AttributeInstance instance = mob.getAttribute(attribute);

		if (instance == null || multiplier == 1.0F) {
			return;
		}

		instance.addOrReplacePermanentModifier(
				new AttributeModifier(id, multiplier - 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}

	private void addFleeGoal(PathfinderMob mob) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();

		if (config.fleeing.excluded.contains(id)) {
			return;
		}

		boolean hostile = mob instanceof net.minecraft.world.entity.monster.Monster;

		if (hostile ? !config.fleeing.hostilesFlee : !config.fleeing.passivesFlee) {
			return;
		}

		((MobGoalAccess) mob).overhaul$goalSelector().addGoal(0, new FleeWhenHurtGoal(
				mob, config.fleeing.healthFraction, config.fleeing.speedMultiplier, config.fleeing.durationTicks));
	}

	// Creeper effects ----------------------------------------------------------------------------

	/** Picks a random effect for a creeper about to explode, or null if it should be a plain blast. */
	public static @Nullable MobEffectInstance rollCreeperEffect(net.minecraft.util.RandomSource random, boolean charged) {
		if (!active() || !config.creepers.enabled || config.creepers.effectPool.isEmpty()) {
			return null;
		}

		if (random.nextFloat() >= config.creepers.lingeringChance) {
			return null;
		}

		List<String> pool = config.creepers.effectPool;
		Identifier id = Identifier.parse(pool.get(random.nextInt(pool.size())));
		Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(id).map(holder -> (Holder<MobEffect>) holder).orElse(null);

		if (effect == null) {
			Overhaul.LOGGER.warn("Unknown status effect '{}' in mob config", id);
			return null;
		}

		int amplifier = config.creepers.maxAmplifier <= 0 ? 0 : random.nextInt(config.creepers.maxAmplifier + 1);
		int duration = charged ? config.creepers.durationTicks * 2 : config.creepers.durationTicks;
		return new MobEffectInstance(effect, duration, amplifier);
	}

	// Skeleton tuning ----------------------------------------------------------------------------

	public static float skeletonBowRange(float vanillaRange) {
		return !active() || !config.skeletons.enabled ? vanillaRange : config.skeletons.bowRange;
	}

	public static float skeletonInaccuracy(float vanillaSpread) {
		return !active() || !config.skeletons.enabled ? vanillaSpread : vanillaSpread * config.skeletons.inaccuracyMultiplier;
	}

	// Enderman block picking ---------------------------------------------------------------------

	public static boolean endermanCanHold(BlockState state) {
		if (!active() || !config.endermen.enabled) {
			return false;
		}

		if (state.hasBlockEntity() || state.getBlock().defaultDestroyTime() < 0.0F) {
			return false;
		}

		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

		if (config.endermen.blocked.contains(id)) {
			return false;
		}

		if (config.endermen.carryAnySolidBlock && state.isSolidRender()) {
			return true;
		}

		if (!config.endermen.carryPartialBlocks) {
			return false;
		}

		return state.is(net.minecraft.tags.BlockTags.STAIRS)
				|| state.is(net.minecraft.tags.BlockTags.SLABS)
				|| state.is(net.minecraft.tags.BlockTags.IMPERMEABLE)
				|| state.is(net.minecraft.tags.BlockTags.WALLS)
				|| state.is(net.minecraft.tags.BlockTags.FENCES);
	}
}
