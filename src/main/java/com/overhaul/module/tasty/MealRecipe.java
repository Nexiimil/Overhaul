package com.overhaul.module.tasty;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.overhaul.module.tasty.TastyConfig.MealEntry;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * A crafting recipe that reads whatever edible things are in the grid and cooks them into one meal.
 *
 * <p>It is a special recipe because the output depends on the inputs in a way no fixed recipe can
 * describe. The JSON only names the container item, the meal's config key and the resulting item;
 * the numbers that decide how good the meal is live in {@code tasty.json}, so retuning meals never
 * means regenerating recipes by hand.
 */
public class MealRecipe extends CustomRecipe {
	public static final MapCodec<MealRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.STRING.fieldOf("meal").forGetter(recipe -> recipe.mealKey),
					Ingredient.CODEC.fieldOf("base").forGetter(recipe -> recipe.base),
					Item.CODEC.fieldOf("result").forGetter(recipe -> recipe.result))
					.apply(instance, MealRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, MealRecipe> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, recipe -> recipe.mealKey,
			Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.base,
			Item.STREAM_CODEC, recipe -> recipe.result,
			MealRecipe::new);

	public static final RecipeSerializer<MealRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final String mealKey;
	private final Ingredient base;
	private final Holder<Item> result;

	public MealRecipe(String mealKey, Ingredient base, Holder<Item> result) {
		this.mealKey = mealKey;
		this.base = base;
		this.result = result;
	}

	// Recipe declares these parameters on the type variable T, whose nullness is unconstrained, so a
	// null-marked override is read as narrowing the inherited contract. The values are never null in
	// practice: the crafting menu builds the input before calling either method.
	@SuppressWarnings("null")
	@Override
	public boolean matches(CraftingInput input, Level level) {
		return !assemble(input).isEmpty();
	}

	@SuppressWarnings("null")
	@Override
	public ItemStack assemble(CraftingInput input) {
		TastyConfig config = TastyModule.config();

		if (config == null) {
			return ItemStack.EMPTY;
		}

		MealEntry meal = config.meals.get(mealKey);

		if (meal == null || !meal.enabled) {
			return ItemStack.EMPTY;
		}

		ItemStack foundBase = ItemStack.EMPTY;
		List<ItemStack> ingredients = new ArrayList<>();

		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);

			if (stack.isEmpty()) {
				continue;
			}

			if (foundBase.isEmpty() && base.test(stack)) {
				foundBase = stack;
				continue;
			}

			if (!MealAssembly.isIngredient(stack, config)) {
				return ItemStack.EMPTY;
			}

			ingredients.add(stack);
		}

		if (foundBase.isEmpty()) {
			return ItemStack.EMPTY;
		}

		return MealAssembly.assemble(meal, result.value(), ingredients, foundBase, config);
	}

	@Override
	public RecipeSerializer<MealRecipe> getSerializer() {
		return SERIALIZER;
	}
}
