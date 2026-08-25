package com.overhaul.module.tasty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.overhaul.core.OverhaulComponents;
import com.overhaul.module.tasty.TastyConfig.EffectEntry;
import com.overhaul.module.tasty.TastyConfig.Flavour;
import com.overhaul.module.tasty.TastyConfig.Flavouring;
import com.overhaul.module.tasty.TastyConfig.MealEntry;
import com.overhaul.module.tasty.TastyConfig.MealTuning;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import org.jspecify.annotations.Nullable;

/**
 * Turns a handful of ingredients into a single meal stack.
 *
 * <p>The result is an ordinary vanilla food: nutrition, saturation and status effects are all
 * baked into the stack as {@code FOOD} and {@code CONSUMABLE} components, so eating a meal runs
 * entirely through vanilla code and behaves correctly with anything else that reads those
 * components. Only the ingredient list is an Overhaul component, and that is purely for the
 * tooltip.
 *
 * <p>Two rules keep meals interesting rather than a stat dump. Repeats of the same ingredient
 * contribute a shrinking share, so five cooked beef is worse than five different things; and a
 * meal built from enough distinct ingredients gets a variety bonus of one extra amplifier level.
 */
public final class MealAssembly {
	private MealAssembly() {
	}

	/**
	 * @param base the container the meal is built on, which is always consumed and only counts
	 *             towards the flavour if the meal says it should
	 * @return the finished meal, or an empty stack if the ingredients do not satisfy the meal's
	 *         own rules (count limits, cooked-only, and so on)
	 */
	public static ItemStack assemble(MealEntry meal, Item resultItem, List<ItemStack> ingredients, @Nullable ItemStack base, TastyConfig config) {
		List<ItemStack> added = ingredients.stream().filter(stack -> !stack.isEmpty()).toList();

		// The limits count what the player added, not the container, so "2 to 4 ingredients" means
		// the same thing whether or not the base happens to be edible.
		if (added.size() < meal.minIngredients || added.size() > meal.maxIngredients) {
			return ItemStack.EMPTY;
		}

		if (meal.requiresCookedIngredients && added.stream().anyMatch(stack -> !isCooked(stack, config))) {
			return ItemStack.EMPTY;
		}

		if (added.stream().anyMatch(stack -> !allows(meal, stack))) {
			return ItemStack.EMPTY;
		}

		List<ItemStack> usable = added;

		if (meal.baseCountsAsIngredient && base != null && !base.isEmpty()) {
			usable = new ArrayList<>(added);
			usable.add(base);
		}

		MealTuning tuning = config.mealTuning;
		Map<Item, Integer> seen = new HashMap<>();

		float nutrition = 0.0F;
		float saturation = 0.0F;
		Map<Identifier, MergedEffect> merged = new LinkedHashMap<>();

		for (ItemStack stack : usable) {
			int repeat = seen.merge(stack.getItem(), 1, Integer::sum) - 1;
			float weight = (float) Math.pow(tuning.diminishingRepeats, repeat);

			FoodProperties food = stack.get(DataComponents.FOOD);
			float ownNutrition = food == null ? 0.0F : food.nutrition();
			float ownSaturation = food == null ? 0.0F : food.saturation();

			nutrition += (ownNutrition * meal.ingredientNutritionScale + meal.nutritionPerIngredient) * weight;
			saturation += ownSaturation * meal.ingredientNutritionScale * weight;

			for (EffectEntry entry : effectsFor(stack, config)) {
				Identifier effectId = Identifier.parse(entry.effect);
				int duration = Math.round(entry.duration * meal.effectDurationScale * weight);
				merged.computeIfAbsent(effectId, id -> new MergedEffect()).add(duration, entry.amplifier, entry.chance);
			}
		}

		saturation += meal.saturationBonus;

		int distinct = seen.size();
		boolean variety = tuning.varietyBonusEnabled && distinct >= tuning.varietyBonusThreshold;

		List<ApplyStatusEffectsConsumeEffect> consumeEffects = new ArrayList<>();

		for (Map.Entry<Identifier, MergedEffect> entry : merged.entrySet()) {
			Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(entry.getKey()).map(holder -> (Holder<MobEffect>) holder).orElse(null);

			if (effect == null) {
				continue;
			}

			MergedEffect value = entry.getValue();
			int duration = Math.min(tuning.maxEffectDuration, Math.max(1, value.duration));
			int amplifier = value.amplifier + (variety ? 1 : 0);

			consumeEffects.add(new ApplyStatusEffectsConsumeEffect(
					new MobEffectInstance(effect, duration, amplifier), value.chance));
		}

		FoodProperties foodProperties = new FoodProperties(
				Math.min(tuning.maxNutrition, Math.max(0, Math.round(nutrition))),
				Math.min(tuning.maxSaturation, Math.max(0.0F, saturation)),
				false);

		Consumable.Builder consumable = Consumable.builder()
				.consumeSeconds(meal.eatSeconds)
				.animation(ItemUseAnimation.EAT)
				.sound(SoundEvents.GENERIC_EAT)
				.hasConsumeParticles(true);

		consumeEffects.forEach(consumable::onConsume);

		ItemStack result = new ItemStack(resultItem);
		result.set(DataComponents.FOOD, foodProperties);
		result.set(DataComponents.CONSUMABLE, consumable.build());
		result.set(OverhaulComponents.MEAL_INGREDIENTS,
				usable.stream().map(stack -> BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem())).map(holder -> (Holder<Item>) holder).toList());

		if (meal.stackSize > 0 && meal.stackSize != 64) {
			result.set(DataComponents.MAX_STACK_SIZE, meal.stackSize);
		}

		String convertsTo = meal.convertsTo;

		if (convertsTo != null && !convertsTo.isBlank()) {
			BuiltInRegistries.ITEM.get(Identifier.parse(convertsTo)).ifPresent(remainder ->
					result.set(DataComponents.USE_REMAINDER, new UseRemainder(new ItemStackTemplate(remainder.value()))));
		}

		return result;
	}

	/**
	 * Whether a meal will accept this ingredient at all.
	 *
	 * <p>An open-ended meal takes anything edible; a named dish lists what belongs in it, which is
	 * what separates "a salad" from "an elote". The list holds item ids and, with a leading
	 * {@code #}, tags — so a dish can be pinned to one exact item or opened up to a whole category.
	 */
	private static boolean allows(MealEntry meal, ItemStack stack) {
		if (meal.allowedIngredients == null || meal.allowedIngredients.isEmpty()) {
			return true;
		}

		String id = itemId(stack.getItem()).toString();

		for (String allowed : meal.allowedIngredients) {
			if (allowed.startsWith("#")) {
				if (stack.is(TagKey.create(Registries.ITEM, Identifier.parse(allowed.substring(1))))) {
					return true;
				}
			} else if (allowed.equals(id)) {
				return true;
			}
		}

		return false;
	}

	/** Anything edible, or anything the config gives a flavour to, may go into a meal. */
	public static boolean isIngredient(ItemStack stack, TastyConfig config) {
		if (stack.isEmpty()) {
			return false;
		}

		if (stack.has(DataComponents.FOOD)) {
			return true;
		}

		return config.ingredientEffects.containsKey(itemId(stack.getItem()).toString())
				|| flavouringOf(stack, config) != null;
	}

	/**
	 * Works out what an ingredient contributes, in order of specificity: an explicit per-item
	 * override, then the family it is named in, then the family its food tag implies. The tag step
	 * is what makes a modded steak behave like a vanilla one without anyone listing it.
	 */
	private static List<EffectEntry> effectsFor(ItemStack stack, TastyConfig config) {
		List<EffectEntry> explicit = config.ingredientEffects.get(itemId(stack.getItem()).toString());

		if (explicit != null) {
			return explicit;
		}

		Flavouring flavouring = flavouringOf(stack, config);

		if (flavouring == null) {
			return List.of();
		}

		Flavour flavour = config.flavours.get(flavouring.family);

		if (flavour == null) {
			return List.of();
		}

		boolean golden = "golden".equalsIgnoreCase(flavouring.quality);
		int duration = Math.round(flavour.baseDuration * qualityScale(flavouring.quality, config));
		int amplifier = flavour.amplifier + (golden ? config.quality.goldenAmplifierBonus : 0);

		return List.of(new EffectEntry(flavour.effect, duration, amplifier));
	}

	private static float qualityScale(@Nullable String quality, TastyConfig config) {
		return switch (quality == null ? "raw" : quality.toLowerCase(java.util.Locale.ROOT)) {
			case "cooked" -> config.quality.cooked;
			case "golden" -> config.quality.golden;
			default -> config.quality.raw;
		};
	}

	private static @Nullable Flavouring flavouringOf(ItemStack stack, TastyConfig config) {
		Flavouring named = config.ingredients.get(itemId(stack.getItem()).toString());

		if (named != null) {
			return named;
		}

		for (Map.Entry<String, Flavouring> entry : config.flavourTags.entrySet()) {
			TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(entry.getKey()));

			if (stack.is(tag)) {
				return entry.getValue();
			}
		}

		return null;
	}

	/**
	 * A stew or a skewer wants prepared food, and "prepared" is exactly what the quality ladder
	 * already describes — so the same rule that decides how strong an ingredient is also decides
	 * whether it is allowed in a cooked-only meal.
	 */
	private static boolean isCooked(ItemStack stack, TastyConfig config) {
		Flavouring flavouring = flavouringOf(stack, config);

		if (flavouring == null) {
			return false;
		}

		String quality = flavouring.quality == null ? "raw" : flavouring.quality.toLowerCase(java.util.Locale.ROOT);
		return quality.equals("cooked") || quality.equals("golden");
	}

	private static Identifier itemId(Item item) {
		return BuiltInRegistries.ITEM.getKey(item);
	}

	/** Accumulates one effect id across every ingredient that grants it. */
	private static final class MergedEffect {
		private int duration;
		private int amplifier;
		private float chance = 0.0F;

		void add(int duration, int amplifier, float chance) {
			this.duration += duration;
			this.amplifier = Math.max(this.amplifier, amplifier);
			this.chance = Math.max(this.chance, chance);
		}
	}
}
