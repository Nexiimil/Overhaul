package com.overhaul.module.tasty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.overhaul.core.config.RecipeSpec;
import com.overhaul.module.tasty.TastyConfig.CropEntry;
import com.overhaul.module.tasty.TastyConfig.EffectEntry;
import com.overhaul.module.tasty.TastyConfig.FoodEntry;
import com.overhaul.module.tasty.TastyConfig.MealEntry;

/**
 * The shipped contents of the Tasty module.
 *
 * <p>These are only defaults: they are written to {@code tasty.json} on first launch and every
 * value, including the recipes, is read back from that file afterwards. Nothing below is consulted
 * again once the config exists.
 */
final class TastyDefaults {
	private TastyDefaults() {
	}

	static void fill(TastyConfig config) {
		fillCrops(config);
		fillFoods(config);
		fillVanillaFoods(config);
		fillMeals(config);
		fillFlavours(config);
	}

	// Crops --------------------------------------------------------------------------------------

	private static void fillCrops(TastyConfig config) {
		crop(config, "tomato", 3);
		crop(config, "lettuce", 2);
		crop(config, "corn", 2);
		crop(config, "chili_pepper", 2);
	}

	private static void crop(TastyConfig config, String name, int bonusDrops) {
		CropEntry entry = new CropEntry();
		entry.produce = name;
		entry.bonusDropsMax = bonusDrops;
		entry.seedRecipes.put("from_produce", RecipeSpec.shapeless(
				"overhaul:" + name + "_seeds", 1, List.of("overhaul:" + name)).category("misc"));
		config.crops.putIfAbsent(name, entry);
	}

	// Foods --------------------------------------------------------------------------------------

	private static void fillFoods(TastyConfig config) {
		// Raw produce.
		food(config, "tomato", 2, 0.3F);
		food(config, "lettuce", 1, 0.3F);
		food(config, "chili_pepper", 1, 0.1F);

		food(config, "corn", 2, 0.3F);

		// Cooked and processed goods.
		food(config, "cooked_corn", 5, 0.6F, entry -> entry.recipes.putAll(Map.of(
				"smelting", RecipeSpec.cooking("smelting", "overhaul:corn", "overhaul:cooked_corn", 0.35F, 200),
				"smoking", RecipeSpec.cooking("smoking", "overhaul:corn", "overhaul:cooked_corn", 0.35F, 100),
				"campfire", RecipeSpec.cooking("campfire_cooking", "overhaul:corn", "overhaul:cooked_corn", 0.35F, 600))));

		food(config, "popcorn", 3, 0.2F, entry -> entry.recipes.put("blasting",
				RecipeSpec.cooking("blasting", "overhaul:corn", "overhaul:popcorn", 0.35F, 100)));

		food(config, "flour", 0, 0.0F, entry -> {
			entry.edible = false;
			entry.recipes.put("from_wheat", RecipeSpec.shapeless("overhaul:flour", 1,
					List.of("minecraft:wheat", "minecraft:wheat")));
		});
		food(config, "dough", 0, 0.0F, entry -> {
			entry.edible = false;
			entry.recipes.put("from_flour", RecipeSpec.shapeless("overhaul:dough", 1,
					List.of("overhaul:flour", "minecraft:water_bucket")));
		});

		food(config, "cheese", 4, 0.6F, entry -> entry.recipes.put("from_milk",
				RecipeSpec.shapeless("overhaul:cheese", 2, List.of("minecraft:milk_bucket", "overhaul:flour"))));

		food(config, "pasta", 5, 0.6F, entry -> entry.recipes.put("from_dough",
				RecipeSpec.shapeless("overhaul:pasta", 2, List.of("overhaul:dough", "overhaul:flour"))));

		food(config, "toast", 6, 0.7F, entry -> entry.recipes.putAll(Map.of(
				"smoking", RecipeSpec.cooking("smoking", "minecraft:bread", "overhaul:toast", 0.1F, 100),
				"campfire", RecipeSpec.cooking("campfire_cooking", "minecraft:bread", "overhaul:toast", 0.1F, 400))));

		food(config, "tomato_sauce", 4, 0.5F, entry -> {
			entry.usingConvertsTo = "minecraft:bowl";
			entry.stackSize = 16;
			entry.recipes.put("from_tomatoes", RecipeSpec.shapeless("overhaul:tomato_sauce", 1,
					List.of("overhaul:tomato", "overhaul:tomato", "overhaul:chili_pepper", "minecraft:bowl")));
		});

		food(config, "jam", 4, 0.4F, entry -> {
			entry.usingConvertsTo = "minecraft:glass_bottle";
			entry.stackSize = 16;
			entry.recipes.put("from_berries", RecipeSpec.shapeless("overhaul:jam", 1,
					List.of("minecraft:sweet_berries", "minecraft:sweet_berries", "minecraft:sugar", "minecraft:glass_bottle")));
		});

		food(config, "chocolate", 3, 0.4F, entry -> entry.recipes.put("from_cocoa",
				RecipeSpec.shapeless("overhaul:chocolate", 2, List.of("minecraft:cocoa_beans", "minecraft:sugar", "minecraft:milk_bucket"))));

		food(config, "fried_egg", 3, 0.4F, entry -> entry.recipes.putAll(Map.of(
				"smelting", RecipeSpec.cooking("smelting", "minecraft:egg", "overhaul:fried_egg", 0.2F, 200),
				"campfire", RecipeSpec.cooking("campfire_cooking", "minecraft:egg", "overhaul:fried_egg", 0.2F, 400))));

		food(config, "honey_glazed_ham", 10, 1.0F, entry -> {
			entry.stackSize = 16;
			entry.recipes.put("glazing", RecipeSpec.shapeless("overhaul:honey_glazed_ham", 1,
					List.of("minecraft:cooked_porkchop", "minecraft:honey_bottle", "minecraft:sugar")));
		});

		// A baked potato is already the cheapest filling meal in the game; gilding it is the
		// obvious counterpart to the golden carrot, and gives gold nuggets a use that is not
		// eventually a golden apple.
		food(config, "golden_baked_potato", 7, 1.0F, entry -> {
			entry.stackSize = 16;
			entry.recipes.put("from_nuggets", RecipeSpec.shaped("overhaul:golden_baked_potato", 1,
					List.of("NNN", "NPN", "NNN"),
					Map.of("N", "minecraft:gold_nugget", "P", "minecraft:baked_potato")));
		});

		food(config, "trail_mix", 6, 0.8F, entry -> entry.recipes.put("mixing",
				RecipeSpec.shapeless("overhaul:trail_mix", 3, List.of(
						"minecraft:glow_berries", "minecraft:sweet_berries", "minecraft:cocoa_beans", "minecraft:wheat_seeds"))));
	}

	/**
	 * Food this module hands to items that already existed.
	 *
	 * <p>A glistering melon slice has never been edible, which has always been odd for a thing made
	 * of a melon slice and eight gold nuggets. It is a brewing ingredient, and it stays one — this
	 * only means the leftovers are worth something.
	 */
	private static void fillVanillaFoods(TastyConfig config) {
		FoodEntry glisteringMelon = new FoodEntry();
		glisteringMelon.nutrition = 4;

		// Gold makes a food rich rather than filling, the same way it does for a golden carrot:
		// four hunger, but nine and a half saturation behind it.
		glisteringMelon.saturationModifier = 1.2F;
		glisteringMelon.eatSeconds = 1.6F;
		config.vanillaFoods.putIfAbsent("minecraft:glistering_melon_slice", glisteringMelon);
	}

	private static FoodEntry food(TastyConfig config, String name, int nutrition, float saturation) {
		return food(config, name, nutrition, saturation, entry -> {
		});
	}

	private static FoodEntry food(TastyConfig config, String name, int nutrition, float saturation,
			java.util.function.Consumer<FoodEntry> customiser) {
		FoodEntry entry = new FoodEntry();
		entry.nutrition = nutrition;
		entry.saturationModifier = saturation;
		customiser.accept(entry);
		config.foods.putIfAbsent(name, entry);
		return config.foods.get(name);
	}

	// Meals --------------------------------------------------------------------------------------

	private static void fillMeals(TastyConfig config) {
		MealEntry salad = new MealEntry();
		salad.base = "minecraft:bowl";
		salad.minIngredients = 2;
		salad.maxIngredients = 4;
		salad.nutritionPerIngredient = 1;
		salad.ingredientNutritionScale = 0.5F;
		salad.saturationBonus = 1.0F;
		salad.effectDurationScale = 1.0F;
		salad.convertsTo = "minecraft:bowl";
		config.meals.putIfAbsent("salad", salad);

		MealEntry stew = new MealEntry();
		stew.base = "minecraft:bowl";
		stew.minIngredients = 2;
		stew.maxIngredients = 5;
		stew.requiresCookedIngredients = true;
		stew.nutritionPerIngredient = 2;
		stew.ingredientNutritionScale = 0.6F;
		stew.saturationBonus = 2.0F;
		stew.effectDurationScale = 1.5F;
		stew.convertsTo = "minecraft:bowl";
		config.meals.putIfAbsent("stew", stew);

		MealEntry sandwich = new MealEntry();
		sandwich.base = "minecraft:bread";
		sandwich.minIngredients = 1;
		sandwich.maxIngredients = 3;
		sandwich.nutritionPerIngredient = 2;
		sandwich.ingredientNutritionScale = 0.7F;
		sandwich.saturationBonus = 1.5F;
		sandwich.effectDurationScale = 1.0F;
		sandwich.convertsTo = "";
		sandwich.stackSize = 16;
		config.meals.putIfAbsent("sandwich", sandwich);

		MealEntry skewer = new MealEntry();
		skewer.base = "minecraft:stick";
		skewer.minIngredients = 2;
		skewer.maxIngredients = 4;
		skewer.requiresCookedIngredients = true;
		skewer.nutritionPerIngredient = 2;
		skewer.ingredientNutritionScale = 0.8F;
		skewer.saturationBonus = 1.0F;
		skewer.effectDurationScale = 0.75F;
		skewer.convertsTo = "";
		skewer.stackSize = 16;
		config.meals.putIfAbsent("skewer", skewer);

		// Elote is a named dish rather than an open-ended meal: a cooked cob and a chilli, nothing
		// else. It still draws its effects from the flavour families like every other meal, so the
		// cob's speed and the chilli's fire resistance both come through without being written down
		// anywhere as an elote-specific rule. The cob counts as an ingredient because, unlike a bowl
		// or a stick, it is the larger half of the dish.
		MealEntry elote = new MealEntry();
		elote.base = "overhaul:cooked_corn";
		elote.baseCountsAsIngredient = true;
		elote.allowedIngredients = new ArrayList<>(List.of("overhaul:chili_pepper"));
		elote.minIngredients = 1;
		elote.maxIngredients = 1;
		elote.nutritionPerIngredient = 2;
		elote.ingredientNutritionScale = 0.7F;
		elote.saturationBonus = 1.5F;
		elote.effectDurationScale = 1.5F;
		elote.convertsTo = "";
		elote.stackSize = 16;
		config.meals.putIfAbsent("elote", elote);

		MealEntry pie = new MealEntry();
		pie.base = "overhaul:dough";
		pie.minIngredients = 2;
		pie.maxIngredients = 3;
		pie.nutritionPerIngredient = 3;
		pie.ingredientNutritionScale = 0.6F;
		pie.saturationBonus = 2.5F;
		pie.effectDurationScale = 2.0F;
		pie.convertsTo = "";
		pie.stackSize = 8;
		config.meals.putIfAbsent("pie", pie);
	}


	// Flavours -------------------------------------------------------------------------------------

	/**
	 * The flavour table: eleven families, each with one signature effect.
	 *
	 * <p>The point of grouping rather than listing is that a player can predict a meal before
	 * making it. Meat means strength, grain means speed, roots mean night vision — and that holds
	 * for every food in the family, including modded ones picked up through the tag fallback below.
	 * Preparing an ingredient moves it up the quality ladder rather than changing what it does, so
	 * cooked beef is still strength, just twice as much of it.
	 */
	private static void fillFlavours(TastyConfig config) {
		flavour(config, "meat", "minecraft:strength", 300);
		flavour(config, "fish", "minecraft:water_breathing", 400);
		flavour(config, "grain", "minecraft:speed", 300);
		flavour(config, "root", "minecraft:night_vision", 300);
		flavour(config, "leaf", "minecraft:jump_boost", 300);
		flavour(config, "fruit", "minecraft:regeneration", 120);
		flavour(config, "sweet", "minecraft:absorption", 400);
		flavour(config, "dairy", "minecraft:haste", 400);
		flavour(config, "spice", "minecraft:fire_resistance", 300);
		flavour(config, "fungus", "minecraft:resistance", 300);
		flavour(config, "foul", "minecraft:hunger", 300);

		// Meat and eggs: protein of any kind.
		ingredient(config, "minecraft:beef", "meat", "raw");
		ingredient(config, "minecraft:porkchop", "meat", "raw");
		ingredient(config, "minecraft:chicken", "meat", "raw");
		ingredient(config, "minecraft:mutton", "meat", "raw");
		ingredient(config, "minecraft:rabbit", "meat", "raw");
		ingredient(config, "minecraft:egg", "meat", "raw");
		ingredient(config, "minecraft:cooked_beef", "meat", "cooked");
		ingredient(config, "minecraft:cooked_porkchop", "meat", "cooked");
		ingredient(config, "minecraft:cooked_chicken", "meat", "cooked");
		ingredient(config, "minecraft:cooked_mutton", "meat", "cooked");
		ingredient(config, "minecraft:cooked_rabbit", "meat", "cooked");
		ingredient(config, "overhaul:fried_egg", "meat", "cooked");
		ingredient(config, "overhaul:honey_glazed_ham", "meat", "golden");

		ingredient(config, "minecraft:cod", "fish", "raw");
		ingredient(config, "minecraft:salmon", "fish", "raw");
		ingredient(config, "minecraft:tropical_fish", "fish", "raw");
		ingredient(config, "minecraft:cooked_cod", "fish", "cooked");
		ingredient(config, "minecraft:cooked_salmon", "fish", "cooked");

		// Grain: anything built out of wheat, corn or flour.
		ingredient(config, "minecraft:wheat", "grain", "raw");
		ingredient(config, "overhaul:corn", "grain", "raw");
		ingredient(config, "overhaul:flour", "grain", "raw");
		ingredient(config, "overhaul:dough", "grain", "raw");
		ingredient(config, "minecraft:bread", "grain", "cooked");
		ingredient(config, "overhaul:cooked_corn", "grain", "cooked");
		ingredient(config, "overhaul:popcorn", "grain", "cooked");
		ingredient(config, "overhaul:toast", "grain", "cooked");
		ingredient(config, "overhaul:pasta", "grain", "cooked");

		ingredient(config, "minecraft:carrot", "root", "raw");
		ingredient(config, "minecraft:potato", "root", "raw");
		ingredient(config, "minecraft:beetroot", "root", "raw");
		ingredient(config, "minecraft:baked_potato", "root", "cooked");
		ingredient(config, "minecraft:golden_carrot", "root", "golden");
		ingredient(config, "overhaul:golden_baked_potato", "root", "golden");

		ingredient(config, "overhaul:lettuce", "leaf", "raw");
		ingredient(config, "minecraft:kelp", "leaf", "raw");
		ingredient(config, "minecraft:dried_kelp", "leaf", "cooked");

		ingredient(config, "overhaul:tomato", "fruit", "raw");
		ingredient(config, "minecraft:apple", "fruit", "raw");
		ingredient(config, "minecraft:melon_slice", "fruit", "raw");
		ingredient(config, "minecraft:sweet_berries", "fruit", "raw");
		ingredient(config, "minecraft:glow_berries", "fruit", "raw");
		ingredient(config, "minecraft:chorus_fruit", "fruit", "raw");
		ingredient(config, "overhaul:jam", "fruit", "cooked");
		ingredient(config, "overhaul:tomato_sauce", "fruit", "cooked");
		ingredient(config, "overhaul:trail_mix", "fruit", "cooked");
		ingredient(config, "minecraft:golden_apple", "fruit", "golden");
		ingredient(config, "minecraft:enchanted_golden_apple", "fruit", "golden");
		ingredient(config, "minecraft:glistering_melon_slice", "fruit", "golden");

		ingredient(config, "minecraft:sugar", "sweet", "raw");
		ingredient(config, "minecraft:cocoa_beans", "sweet", "raw");
		ingredient(config, "minecraft:honey_bottle", "sweet", "raw");
		ingredient(config, "minecraft:cookie", "sweet", "cooked");
		ingredient(config, "minecraft:pumpkin_pie", "sweet", "cooked");
		ingredient(config, "overhaul:chocolate", "sweet", "cooked");

		ingredient(config, "minecraft:milk_bucket", "dairy", "raw");
		ingredient(config, "overhaul:cheese", "dairy", "cooked");

		ingredient(config, "overhaul:chili_pepper", "spice", "raw");

		ingredient(config, "minecraft:brown_mushroom", "fungus", "raw");
		ingredient(config, "minecraft:red_mushroom", "fungus", "raw");

		ingredient(config, "minecraft:rotten_flesh", "foul", "raw");
		ingredient(config, "minecraft:poisonous_potato", "foul", "raw");

		// Tag fallback, so a modded steak behaves like a vanilla one with nothing listed by hand.
		tagFlavour(config, "c:foods/cooked_meat", "meat", "cooked");
		tagFlavour(config, "c:foods/raw_meat", "meat", "raw");
		tagFlavour(config, "c:foods/cooked_fish", "fish", "cooked");
		tagFlavour(config, "c:foods/raw_fish", "fish", "raw");
		tagFlavour(config, "c:foods/bread", "grain", "cooked");
		tagFlavour(config, "c:foods/dough", "grain", "raw");
		tagFlavour(config, "c:foods/cookie", "sweet", "cooked");
		tagFlavour(config, "c:foods/candy", "sweet", "cooked");
		tagFlavour(config, "c:foods/pie", "sweet", "cooked");
		tagFlavour(config, "c:foods/berry", "fruit", "raw");
		tagFlavour(config, "c:foods/fruit", "fruit", "raw");
		tagFlavour(config, "c:foods/vegetable", "root", "raw");
		tagFlavour(config, "c:foods/golden", "fruit", "golden");
		tagFlavour(config, "c:foods/food_poisoning", "foul", "raw");

		// The exceptions. These three are defined by what they do to you, so they keep bespoke
		// effects rather than a family; anything listed here overrides the rules above.
		effects(config, "minecraft:spider_eye", new EffectEntry("minecraft:poison", 200, 0));
		effects(config, "minecraft:pufferfish", new EffectEntry("minecraft:nausea", 200, 0),
				new EffectEntry("minecraft:poison", 200, 0));
		effects(config, "minecraft:suspicious_stew", new EffectEntry("minecraft:blindness", 200, 0, 0.5F));
	}

	private static void flavour(TastyConfig config, String family, String effect, int baseDuration) {
		config.flavours.putIfAbsent(family, new TastyConfig.Flavour(effect, baseDuration));
	}

	private static void ingredient(TastyConfig config, String item, String family, String quality) {
		config.ingredients.putIfAbsent(item, new TastyConfig.Flavouring(family, quality));
	}

	private static void tagFlavour(TastyConfig config, String tag, String family, String quality) {
		config.flavourTags.putIfAbsent(tag, new TastyConfig.Flavouring(family, quality));
	}

	private static void effects(TastyConfig config, String item, EffectEntry... entries) {
		config.ingredientEffects.putIfAbsent(item, new java.util.ArrayList<>(List.of(entries)));
	}
}
