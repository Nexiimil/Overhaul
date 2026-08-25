package com.overhaul.core.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

/**
 * A recipe as it appears in an Overhaul config file.
 *
 * <p>This is deliberately a thin, flat mirror of the vanilla recipe JSON rather than a bespoke
 * format: everything the pack builder emits is a plain vanilla recipe, so a user who changes
 * {@link #type} from {@code crafting_shaped} to {@code smelting} gets a real smelting recipe with
 * no special support needed here, and anything vanilla can express stays expressible.
 *
 * <p>Ingredient fields accept a single id ({@code "minecraft:leather"}), a tag
 * ({@code "#c:strings"}), or a comma separated list meaning "any of these"
 * ({@code "minecraft:oak_log,minecraft:birch_log"}).
 */
public class RecipeSpec {
	private static final Gson GSON = new Gson();

	/** Turn a single recipe off without disabling the item it produces. */
	public boolean enabled = true;

	/** Vanilla recipe type, with or without the {@code minecraft:} prefix. */
	public String type = "crafting_shaped";

	/** Rows of the crafting grid; a space means "empty". Shaped recipes only. */
	public List<String> pattern = new ArrayList<>();

	/** Symbol used in {@link #pattern} mapped to the ingredient it stands for. Shaped recipes only. */
	public Map<String, String> key = new LinkedHashMap<>();

	/** Ingredient list for shapeless recipes, or the single input for cooking and stonecutting. */
	public List<String> ingredients = new ArrayList<>();

	/** Smithing table slots. */
	public String template = "";
	public String base = "";
	public String addition = "";

	/** Item id produced. */
	public @Nullable String result = "";
	public int count = 1;

	/** Optional data components applied to the result, in vanilla component JSON form. */
	public @Nullable Map<String, Object> resultComponents = new LinkedHashMap<>();

	/** Recipe book grouping and tab. */
	public @Nullable String group = "";
	public @Nullable String category = "misc";

	public float experience = 0.0F;
	public int cookingTime = 200;

	public static RecipeSpec shaped(String result, int count, List<String> pattern, Map<String, String> key) {
		RecipeSpec spec = new RecipeSpec();
		spec.type = "crafting_shaped";
		spec.result = result;
		spec.count = count;
		spec.pattern = new ArrayList<>(pattern);
		spec.key = new LinkedHashMap<>(key);
		return spec;
	}

	public static RecipeSpec shapeless(String result, int count, List<String> ingredients) {
		RecipeSpec spec = new RecipeSpec();
		spec.type = "crafting_shapeless";
		spec.result = result;
		spec.count = count;
		spec.ingredients = new ArrayList<>(ingredients);
		return spec;
	}

	public static RecipeSpec cooking(String type, String input, String result, float experience, int cookingTime) {
		RecipeSpec spec = new RecipeSpec();
		spec.type = type;
		spec.ingredients = new ArrayList<>(List.of(input));
		spec.result = result;
		spec.experience = experience;
		spec.cookingTime = cookingTime;
		spec.category = "food";
		return spec;
	}

	public static RecipeSpec smithing(String template, String base, String addition, String result) {
		RecipeSpec spec = new RecipeSpec();
		spec.type = "smithing_transform";
		spec.template = template;
		spec.base = base;
		spec.addition = addition;
		spec.result = result;
		return spec;
	}

	public RecipeSpec category(String category) {
		this.category = category;
		return this;
	}

	public RecipeSpec group(String group) {
		this.group = group;
		return this;
	}

	public RecipeSpec resultComponent(String component, Object value) {
		this.resultComponents.put(component, value);
		return this;
	}

	/** Builds the vanilla recipe JSON, or {@code null} if the spec is disabled or incomplete. */
	public @Nullable JsonObject toRecipeJson() {
		if (!enabled || result == null || result.isBlank()) {
			return null;
		}

		String fullType = type.contains(":") ? type : "minecraft:" + type;
		JsonObject json = new JsonObject();
		json.addProperty("type", fullType);

		String bare = fullType.substring(fullType.indexOf(':') + 1);

		switch (bare) {
			case "crafting_shaped" -> {
				if (pattern.isEmpty() || key.isEmpty()) {
					return null;
				}

				json.add("pattern", stringArray(pattern));
				json.add("key", keyObject());
				addBookInfo(json);
			}
			case "crafting_shapeless" -> {
				if (ingredients.isEmpty()) {
					return null;
				}

				json.add("ingredients", ingredientArray(ingredients));
				addBookInfo(json);
			}
			case "smelting", "smoking", "blasting", "campfire_cooking" -> {
				if (ingredients.isEmpty()) {
					return null;
				}

				json.add("ingredient", ingredient(ingredients.getFirst()));
				json.addProperty("experience", experience);
				json.addProperty("cookingtime", cookingTime);
				addBookInfo(json);
			}
			case "stonecutting" -> {
				if (ingredients.isEmpty()) {
					return null;
				}

				json.add("ingredient", ingredient(ingredients.getFirst()));
			}
			case "smithing_transform" -> {
				if (base.isBlank()) {
					return null;
				}

				if (!template.isBlank()) {
					json.add("template", ingredient(template));
				}

				json.add("base", ingredient(base));

				if (!addition.isBlank()) {
					json.add("addition", ingredient(addition));
				}
			}
			default -> {
				// Unknown or third party type: emit whichever fields were filled in and let the
				// game's own serialiser decide whether the result makes sense.
				if (!pattern.isEmpty()) {
					json.add("pattern", stringArray(pattern));
				}

				if (!key.isEmpty()) {
					json.add("key", keyObject());
				}

				if (!ingredients.isEmpty()) {
					json.add("ingredients", ingredientArray(ingredients));
				}
			}
		}

		json.add("result", resultStack());
		return json;
	}

	private void addBookInfo(JsonObject json) {
		if (category != null && !category.isBlank()) {
			json.addProperty("category", category);
		}

		if (group != null && !group.isBlank()) {
			json.addProperty("group", group);
		}
	}

	private JsonObject keyObject() {
		JsonObject keys = new JsonObject();
		key.forEach((symbol, value) -> keys.add(symbol, ingredient(value)));
		return keys;
	}

	private static JsonArray stringArray(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array;
	}

	private static JsonArray ingredientArray(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(value -> array.add(ingredient(value)));
		return array;
	}

	private JsonElement resultStack() {
		JsonObject stack = new JsonObject();
		stack.addProperty("id", result);
		stack.addProperty("count", Math.max(1, count));

		if (resultComponents != null && !resultComponents.isEmpty()) {
			stack.add("components", GSON.toJsonTree(resultComponents));
		}

		return stack;
	}

	/** A comma separated value becomes an array, meaning "any one of these". */
	private static JsonElement ingredient(String value) {
		String trimmed = value.trim();

		if (!trimmed.contains(",")) {
			return new JsonPrimitive(trimmed);
		}

		JsonArray array = new JsonArray();

		for (String part : trimmed.split(",")) {
			if (!part.isBlank()) {
				array.add(part.trim());
			}
		}

		return array;
	}
}
