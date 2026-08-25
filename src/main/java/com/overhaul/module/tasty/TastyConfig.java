package com.overhaul.module.tasty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.overhaul.core.config.RecipeSpec;
import org.jspecify.annotations.Nullable;

/**
 * {@code config/overhaul/tasty.json}.
 *
 * <p>Every food, crop and meal is a map entry keyed by its item path, so a pack author can turn
 * individual items off, retune their nutrition, rewrite their recipes, or add entries of their own
 * without touching code. Entries the mod does not ship are still registered — they just need a
 * resource pack to supply a texture.
 */
public class TastyConfig {
	public String _comment = "Each food, crop and meal can be enabled, retuned and re-crafted independently. "
			+ "Ingredient effects decide what a food contributes when it is cooked into a meal.";

	public Map<String, FoodEntry> foods = new LinkedHashMap<>();
	public Map<String, CropEntry> crops = new LinkedHashMap<>();
	public Map<String, MealEntry> meals = new LinkedHashMap<>();

	/**
	 * The signature effect of each flavour family. An ingredient lends a meal the effect of the
	 * family it belongs to, so the whole system is learnable from eleven rules rather than from a
	 * lookup table of every food in the game.
	 */
	public Map<String, Flavour> flavours = new LinkedHashMap<>();

	/** Item id to the family it belongs to. Checked before {@link #flavourTags}. */
	public Map<String, Flavouring> ingredients = new LinkedHashMap<>();

	/**
	 * Item tag to family, used for anything not named in {@link #ingredients}. These are the
	 * conventional food tags, so a modded steak lands in the meat family and a modded berry in the
	 * fruit family without anyone having to list them.
	 */
	public Map<String, Flavouring> flavourTags = new LinkedHashMap<>();

	public QualityScaling quality = new QualityScaling();

	/**
	 * Explicit per-item effects that bypass the family rules entirely. Reserved for ingredients
	 * whose behaviour is the point, such as rotten flesh; anything listed here wins over its family.
	 */
	public Map<String, List<EffectEntry>> ingredientEffects = new LinkedHashMap<>();

	public MealTuning mealTuning = new MealTuning();

	/** One flavour family: what it does, and for how long at raw quality. */
	public static class Flavour {
		public String effect = "minecraft:speed";
		public int baseDuration = 300;
		public int amplifier = 0;

		public Flavour() {
		}

		public Flavour(String effect, int baseDuration) {
			this.effect = effect;
			this.baseDuration = baseDuration;
		}
	}

	/** Which family an ingredient belongs to, and how well prepared it is. */
	public static class Flavouring {
		public String family = "";
		/** One of {@code raw}, {@code cooked} or {@code golden}. */
		public @Nullable String quality = "raw";

		public Flavouring() {
		}

		public Flavouring(String family, String quality) {
			this.family = family;
			this.quality = quality;
		}
	}

	/**
	 * How much preparing an ingredient is worth. The same food always moves up the same ladder:
	 * cooking it doubles what it contributes, and a golden version triples it and adds a level.
	 */
	public static class QualityScaling {
		public float raw = 1.0F;
		public float cooked = 2.0F;
		public float golden = 3.0F;
		public int goldenAmplifierBonus = 1;
	}

	/** A plain edible item. */
	public static class FoodEntry {
		public boolean enabled = true;
		/** Non-edible entries are plain crafting ingredients, such as flour. */
		public boolean edible = true;
		public int nutrition = 2;
		/** Vanilla saturation modifier: absolute saturation works out to nutrition x this x 2. */
		public float saturationModifier = 0.3F;
		public boolean alwaysEdible = false;
		public float eatSeconds = 1.6F;
		/** Uses the drinking animation and sound instead of eating. */
		public boolean drink = false;
		public int stackSize = 64;
		/** Item left behind after eating, e.g. {@code minecraft:bowl}. Empty for none. */
		public String usingConvertsTo = "";
		/** Effects applied when this item is eaten on its own. */
		public List<EffectEntry> effects = new ArrayList<>();
		/** Named recipes producing this item. The name only decides the recipe id. */
		public Map<String, RecipeSpec> recipes = new LinkedHashMap<>();
	}

	/** A crop block plus its seed item. The harvested produce is a separate {@link FoodEntry}. */
	public static class CropEntry {
		public boolean enabled = true;
		/** Item path of the produce dropped when fully grown. */
		public String produce = "";
		/** Extra produce dropped at full growth, on top of the guaranteed one. */
		public int bonusDropsMax = 2;
		/** Recipes producing the seed item, if any. */
		public Map<String, RecipeSpec> seedRecipes = new LinkedHashMap<>();
	}

	/** A composite food assembled from a container plus a handful of ingredients. */
	public static class MealEntry {
		public boolean enabled = true;
		/** Item that carries the meal and is consumed when crafting it. */
		public String base = "minecraft:bowl";
		public int minIngredients = 2;
		public int maxIngredients = 4;
		/** Only ingredients that are themselves cooked count, for meals like stew. */
		public boolean requiresCookedIngredients = false;

		/**
		 * Counts the base towards the meal's flavour as well as carrying it. A bowl contributes
		 * nothing worth eating, but a meal built on a corn cob or a slice of bread very much tastes
		 * of it, and this is what lets that come through.
		 */
		public boolean baseCountsAsIngredient = false;

		/**
		 * Restricts what may go into this meal. Leave it empty for an open-ended meal like a salad;
		 * fill it in for a named dish that is meant to be one specific thing. Accepts item ids and
		 * tags written with a leading {@code #}.
		 */
		public @Nullable List<String> allowedIngredients = new ArrayList<>();
		/** Flat nutrition added per ingredient on top of the ingredients' own nutrition. */
		public int nutritionPerIngredient = 1;
		/** Fraction of each ingredient's own nutrition that carries into the meal. */
		public float ingredientNutritionScale = 0.5F;
		/** Flat saturation points added on top of what the ingredients contribute. */
		public float saturationBonus = 1.0F;
		/** Multiplies the duration of every effect the ingredients contribute. */
		public float effectDurationScale = 1.0F;
		/** Item returned after eating, e.g. the bowl. Empty for none. */
		public @Nullable String convertsTo = "minecraft:bowl";
		public float eatSeconds = 1.6F;
		public int stackSize = 16;
	}

	/** Rules shared by every meal. */
	public static class MealTuning {
		public String _comment = "Applies to all meals. A meal made of the same ingredient repeated is "
				+ "capped by diminishingRepeats so stacking one strong ingredient is not the best play.";
		public int maxNutrition = 20;
		public float maxSaturation = 20.0F;
		/** Each repeat of an ingredient contributes this fraction of the previous one. */
		public float diminishingRepeats = 0.5F;
		/** Longest any single meal effect may last, in ticks. */
		public int maxEffectDuration = 6000;
		/** A meal made of at least this many distinct ingredients gets an extra amplifier level. */
		public int varietyBonusThreshold = 4;
		public boolean varietyBonusEnabled = true;
		/** Show the ingredient list and resulting effects on the meal tooltip. */
		public boolean showTooltip = true;
	}

	/** One status effect contribution. */
	public static class EffectEntry {
		public String effect = "minecraft:speed";
		public int duration = 200;
		public int amplifier = 0;
		public float chance = 1.0F;

		public EffectEntry() {
		}

		public EffectEntry(String effect, int duration, int amplifier) {
			this.effect = effect;
			this.duration = duration;
			this.amplifier = amplifier;
		}

		public EffectEntry(String effect, int duration, int amplifier, float chance) {
			this(effect, duration, amplifier);
			this.chance = chance;
		}
	}
}
