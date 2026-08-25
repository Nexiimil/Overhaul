package com.overhaul.module.backpack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.overhaul.Overhaul;
import com.overhaul.core.OverhaulModule;
import com.overhaul.core.Reg;
import com.overhaul.core.config.ConfigManager;
import com.overhaul.core.config.RecipeSpec;
import com.overhaul.core.data.DataPackBuilder;
import com.overhaul.module.backpack.BackpackConfig.TierEntry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.jspecify.annotations.Nullable;

/**
 * Backpacks and their upgrade path.
 *
 * <p>Upgrading is a smithing table transform, which means the upgraded backpack inherits the
 * original's components — including its contents — for free, and it puts the progression on the
 * same footing as netherite gear rather than inventing a new station for it.
 */
public class BackpackModule implements OverhaulModule {
	public static final String TEMPLATE_ID = "backpack_upgrade_smithing_template";

	private static @Nullable BackpackConfig config;
	private static final Map<String, Item> BACKPACKS = new LinkedHashMap<>();
	private static @Nullable Item template;

	public static boolean allowNesting() {
		return config != null && config.allowNesting;
	}

	public static Map<String, Item> backpacks() {
		return BACKPACKS;
	}

	@Override
	public String id() {
		return "backpack";
	}

	@Override
	public String displayName() {
		return "Backpack Module";
	}

	@Override
	public void loadConfig() {
		BackpackConfig loaded = ConfigManager.load(id(), BackpackConfig.class);
		BackpackDefaults.fill(loaded);
		ConfigManager.save(id(), loaded);
		config = loaded;
	}

	@Override
	public void registerContent() {
		config.tiers.forEach((name, tier) -> {
			if (!tier.enabled) {
				return;
			}

			Item.Properties properties = new Item.Properties()
					.stacksTo(1)
					.rarity(rarityFor(tier.rows));

			if (tier.fireResistant) {
				properties.fireResistant();
			}

			BACKPACKS.put(name, Reg.item(name, props -> new BackpackItem(props, tier.rows), properties));
		});

		if (config.upgradeTemplate.enabled) {
			template = Reg.item(TEMPLATE_ID, properties -> new SmithingTemplateItem(
					Component.translatable("tooltip.overhaul.template.backpack"),
					Component.translatable("tooltip.overhaul.template.upgrade_material"),
					Component.translatable("item.overhaul." + TEMPLATE_ID),
					Component.translatable("tooltip.overhaul.template.upgrade_material"),
					List.of(),
					List.of(Identifier.withDefaultNamespace("container/slot/ingot")),
					properties),
					new Item.Properties().rarity(Rarity.UNCOMMON));
		}
	}

	/** Bigger bags read as rarer, which also colours their name in the tooltip. */
	private static Rarity rarityFor(int rows) {
		if (rows >= 6) {
			return Rarity.EPIC;
		}

		return rows >= 4 ? Rarity.RARE : Rarity.COMMON;
	}

	@Override
	public void registerBehaviour() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			BACKPACKS.values().forEach(output::accept);

			Item upgradeTemplate = template;

			if (upgradeTemplate != null) {
				output.accept(upgradeTemplate);
			}
		});

		addTemplateToLoot();
		registerKeybindNetworking();

		if (config.overburden.enabled) {
			ServerTickEvents.END_SERVER_TICK.register(this::tickOverburden);
		}
	}

	// Overburdening ------------------------------------------------------------------------------

	private int sinceLastCheck;

	/**
	 * Tops up the overburdened effects on anyone carrying too much.
	 *
	 * <p>Running on a timer rather than every tick keeps the inventory scan off the hot path, and
	 * re-applying a short effect rather than tracking state means nothing has to be cleaned up when
	 * a player drops a bag, dies, or logs out — the effect simply stops being renewed and expires.
	 */
	private void tickOverburden(MinecraftServer server) {
		BackpackConfig.Overburden settings = config.overburden;

		if (++sinceLastCheck < Math.max(1, settings.checkIntervalTicks)) {
			return;
		}

		sinceLastCheck = 0;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyOverburden(player, settings);
		}
	}

	private static void applyOverburden(ServerPlayer player, BackpackConfig.Overburden settings) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}

		int excess = burdenOf(player, settings) - settings.maxCarried;

		if (excess <= 0) {
			return;
		}

		int duration = Math.max(1, settings.checkIntervalTicks) * 3;

		for (BackpackConfig.BurdenEffect burden : settings.effects) {
			Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(burden.effect))
					.map(holder -> (Holder<MobEffect>) holder)
					.orElse(null);

			if (effect == null) {
				Overhaul.LOGGER.warn("Unknown status effect '{}' in backpack config", burden.effect);
				continue;
			}

			// The first bag over the limit is worth the configured amplifier; each one after that
			// adds more, up to the ceiling.
			int amplifier = Math.min(settings.maxAmplifier,
					burden.amplifier + (excess - 1) * settings.amplifierPerExtra);

			player.addEffect(new MobEffectInstance(effect, duration, Math.max(0, amplifier),
					true, settings.showParticles, true));
		}
	}

	/** How heavily a player is loaded, in bags or in rows depending on the config. */
	public static int burdenOf(ServerPlayer player, BackpackConfig.Overburden settings) {
		Inventory inventory = player.getInventory();
		int burden = 0;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!(stack.getItem() instanceof BackpackItem backpack)) {
				continue;
			}

			if (settings.ignoreEmpty && stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
					.nonEmptyItemCopyStream().findAny().isEmpty()) {
				continue;
			}

			burden += settings.weighBySize ? backpack.rows() : 1;
		}

		return burden;
	}

	/**
	 * Lets the client ask for its backpack to be opened without holding it. The server searches the
	 * inventory itself rather than trusting a slot index from the client.
	 */
	private void registerKeybindNetworking() {
		PayloadTypeRegistry.serverboundPlay().register(OpenBackpackPayload.TYPE, OpenBackpackPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(OpenBackpackPayload.TYPE, (payload, context) -> {
			if (!config.openWithKeybind) {
				return;
			}

			ServerPlayer player = context.player();
			ItemStack found = findBackpack(player);

			if (!found.isEmpty()) {
				BackpackItem.open(player, found);
			}
		});
	}

	/** The held backpack wins, otherwise the first one found scanning the inventory in order. */
	private static ItemStack findBackpack(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);

			if (held.getItem() instanceof BackpackItem) {
				return held;
			}
		}

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);

			if (stack.getItem() instanceof BackpackItem) {
				return stack;
			}
		}

		return ItemStack.EMPTY;
	}

	/**
	 * The template is the gate on the whole upgrade path, so it is only found rather than crafted.
	 * Once a player has one they can duplicate it, which keeps the gate to "find one" instead of
	 * "find five".
	 */
	private void addTemplateToLoot() {
		if (!config.upgradeTemplate.enabled) {
			return;
		}

		Set<ResourceKey<LootTable>> tables = config.upgradeTemplate.lootTables.stream()
				.map(id -> ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, Identifier.parse(id)))
				.collect(Collectors.toSet());

		float chance = config.upgradeTemplate.lootChance;

		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			Item upgradeTemplate = template;

			if (upgradeTemplate == null || !tables.contains(key)) {
				return;
			}

			builder.pool(LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.when(LootItemRandomChanceCondition.randomChance(chance))
					.add(LootItem.lootTableItem(upgradeTemplate))
					.build());
		});
	}

	@Override
	public void buildRecipes(DataPackBuilder pack) {
		config.tiers.forEach((name, tier) -> {
			if (!tier.enabled || !BACKPACKS.containsKey(name)) {
				return;
			}

			tier.recipes.forEach((recipeName, spec) -> pack.addRecipe(name + "_" + recipeName, spec));

			if (tier.upgradeFrom.isBlank() || tier.upgradeMaterial.isBlank() || !config.upgradeTemplate.enabled) {
				return;
			}

			pack.addRecipe(name + "_upgrade", RecipeSpec.smithing(
					Overhaul.id(TEMPLATE_ID).toString(),
					tier.upgradeFrom,
					tier.upgradeMaterial,
					BuiltInRegistries.ITEM.getKey(BACKPACKS.get(name)).toString()));
		});

		if (config.upgradeTemplate.enabled) {
			config.upgradeTemplate.recipes.forEach((recipeName, spec) -> pack.addRecipe(TEMPLATE_ID + "_" + recipeName, spec));
			addAccessoryTags(pack);
		}
	}

	/**
	 * Curios and its Fabric equivalents assign items to accessory slots through item tags, so
	 * publishing those tags is the whole integration: with such a mod installed a backpack drops
	 * into the back slot on its own, and without one the tag files sit unused and harm nothing.
	 */
	private void addAccessoryTags(DataPackBuilder pack) {
		JsonArray values = new JsonArray();
		BACKPACKS.values().forEach(item -> values.add(BuiltInRegistries.ITEM.getKey(item).toString()));

		JsonObject tag = new JsonObject();
		tag.addProperty("replace", false);
		tag.add("values", values);

		for (String namespace : List.of("curios", "accessories", "trinkets")) {
			pack.addFile(Identifier.fromNamespaceAndPath(namespace, "tags/item/back.json"), tag);
		}

		pack.addFile(Overhaul.id("tags/item/backpacks.json"), tag);
	}
}
