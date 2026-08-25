package com.overhaul.core.data;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.overhaul.Overhaul;
import com.overhaul.core.config.RecipeSpec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;

/**
 * Collects the data pack files that Overhaul generates from its config at startup.
 *
 * <p>Everything a module wants to make user configurable ends up here as ordinary vanilla JSON,
 * which the game then loads through its normal data pack pipeline. Nothing in the mod parses
 * recipes itself, so a config edit behaves exactly like editing a data pack by hand.
 */
public final class DataPackBuilder {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final FileToIdConverter RECIPES = FileToIdConverter.registry(Registries.RECIPE);
	private static final FileToIdConverter ADVANCEMENTS = FileToIdConverter.json("advancement");

	private final Map<Identifier, byte[] > files = new LinkedHashMap<>();

	/** Adds a recipe under {@code data/<namespace>/recipe/<path>.json}. */
	public void addRecipe(Identifier id, RecipeSpec spec) {
		JsonElement json = spec.toRecipeJson();

		if (json == null) {
			return;
		}

		addFile(RECIPES.idToFile(id), json);
	}

	/** Convenience for the common case of an Overhaul-namespaced recipe. */
	public void addRecipe(String path, RecipeSpec spec) {
		addRecipe(Overhaul.id(path), spec);
	}

	/** Adds a recipe whose JSON was built directly rather than from a {@link RecipeSpec}. */
	public void addRecipeJson(Identifier id, JsonElement json) {
		addFile(RECIPES.idToFile(id), json);
	}

	public void addAdvancement(Identifier id, JsonElement json) {
		addFile(ADVANCEMENTS.idToFile(id), json);
	}

	public void addFile(Identifier file, JsonElement json) {
		files.put(file, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
	}

	public Map<Identifier, byte[]> files() {
		return files;
	}

	public int size() {
		return files.size();
	}
}
