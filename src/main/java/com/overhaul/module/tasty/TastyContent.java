package com.overhaul.module.tasty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.overhaul.Overhaul;
import com.overhaul.core.OverhaulComponents;
import com.overhaul.core.Reg;
import com.overhaul.module.tasty.TastyConfig.CropEntry;
import com.overhaul.module.tasty.TastyConfig.EffectEntry;
import com.overhaul.module.tasty.TastyConfig.FoodEntry;
import com.overhaul.module.tasty.TastyConfig.MealEntry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Registers everything the Tasty module owns, driven entirely by {@link TastyConfig}. */
public final class TastyContent {
	private static final Map<String, Item> FOODS = new LinkedHashMap<>();
	private static final Map<String, Item> SEEDS = new LinkedHashMap<>();
	private static final Map<String, Block> CROPS = new LinkedHashMap<>();
	private static final Map<String, Item> MEALS = new LinkedHashMap<>();

	private TastyContent() {
	}

	public static Map<String, Item> foods() {
		return FOODS;
	}

	public static Map<String, Item> seeds() {
		return SEEDS;
	}

	public static Map<String, Block> crops() {
		return CROPS;
	}

	public static Map<String, Item> meals() {
		return MEALS;
	}

	/** Every item this module contributes, for the creative tab. */
	public static List<Item> allItems() {
		List<Item> items = new ArrayList<>();
		items.addAll(SEEDS.values());
		items.addAll(FOODS.values());
		items.addAll(MEALS.values());
		return items;
	}

	public static void register(TastyConfig config) {
		OverhaulComponents.register();

		// Foods first: a crop's produce is an ordinary food entry, and the crop block needs to be
		// able to hand out that item when it is harvested.
		config.foods.forEach((name, entry) -> {
			if (entry.enabled) {
				FOODS.put(name, registerFood(name, entry));
			}
		});

		config.crops.forEach((name, entry) -> {
			if (entry.enabled) {
				registerCrop(name, entry);
			}
		});

		config.meals.forEach((name, entry) -> {
			if (entry.enabled) {
				MEALS.put(name, registerMeal(name, entry));
			}
		});

		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Overhaul.id("meal"), MealRecipe.SERIALIZER);
	}

	private static Item registerFood(String name, FoodEntry entry) {
		Item.Properties properties = new Item.Properties().stacksTo(Math.max(1, entry.stackSize));

		if (entry.edible) {
			FoodProperties.Builder food = new FoodProperties.Builder()
					.nutrition(entry.nutrition)
					.saturationModifier(entry.saturationModifier);

			if (entry.alwaysEdible) {
				food.alwaysEdible();
			}

			Consumable.Builder consumable = Consumable.builder()
					.consumeSeconds(entry.eatSeconds)
					.animation(entry.drink ? ItemUseAnimation.DRINK : ItemUseAnimation.EAT)
					.sound(entry.drink ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT)
					.hasConsumeParticles(!entry.drink);

			for (ApplyStatusEffectsConsumeEffect effect : consumeEffects(entry.effects)) {
				consumable.onConsume(effect);
			}

			properties.food(food.build(), consumable.build());

			if (!entry.usingConvertsTo.isBlank()) {
				BuiltInRegistries.ITEM.get(Identifier.parse(entry.usingConvertsTo))
						.ifPresent(remainder -> properties.usingConvertsTo(remainder.value()));
			}
		}

		return Reg.item(name, properties);
	}

	private static void registerCrop(String name, CropEntry entry) {
		String seedName = name + "_seeds";
		String produceName = entry.produce.isBlank() ? name : entry.produce;

		Block crop = Reg.block(name,
				properties -> new OverhaulCropBlock(properties,
						() -> SEEDS.get(name),
						() -> FOODS.getOrDefault(produceName, SEEDS.get(name)),
						entry.bonusDropsMax),
				BlockBehaviour.Properties.of()
						.mapColor(MapColor.PLANT)
						.noCollision()
						.randomTicks()
						.instabreak()
						.sound(SoundType.CROP)
						.pushReaction(PushReaction.DESTROY)
						.noLootTable());

		CROPS.put(name, crop);

		Item seed = Reg.item(seedName,
				properties -> new BlockItem(crop, properties.useItemDescriptionPrefix()),
				new Item.Properties());

		SEEDS.put(name, seed);
	}

	private static Item registerMeal(String name, MealEntry entry) {
		Item.Properties properties = new Item.Properties().stacksTo(Math.max(1, entry.stackSize));

		// A freshly registered meal item with no components is never obtained in survival: every
		// meal comes out of MealRecipe with its food data already attached. The placeholder food
		// data below only decides how a creative-mode copy behaves.
		properties.food(new FoodProperties.Builder()
				.nutrition(entry.nutritionPerIngredient * entry.minIngredients)
				.saturationModifier(0.6F)
				.build(), Consumables.DEFAULT_FOOD);

		return Reg.item(name, MealItem::new, properties);
	}

	private static List<ApplyStatusEffectsConsumeEffect> consumeEffects(List<EffectEntry> entries) {
		List<ApplyStatusEffectsConsumeEffect> effects = new ArrayList<>();

		for (EffectEntry entry : entries) {
			Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(entry.effect))
					.map(holder -> (Holder<MobEffect>) holder)
					.orElse(null);

			if (effect == null) {
				Overhaul.LOGGER.warn("Unknown status effect '{}' in tasty config", entry.effect);
				continue;
			}

			effects.add(new ApplyStatusEffectsConsumeEffect(
					new MobEffectInstance(effect, entry.duration, entry.amplifier), entry.chance));
		}

		return effects;
	}
}
