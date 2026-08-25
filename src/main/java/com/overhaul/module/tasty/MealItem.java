package com.overhaul.module.tasty;

import java.util.List;
import java.util.function.Consumer;

import com.overhaul.core.OverhaulComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

/**
 * A meal. All of its food behaviour lives in per-stack components; this class only exists to put
 * the ingredient list and the resulting effects on the tooltip, since two meals of the same kind
 * are otherwise indistinguishable in the inventory.
 */
public class MealItem extends Item {
	public MealItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
			Consumer<Component> lines, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, lines, flag);

		TastyConfig config = TastyModule.config();

		if (config != null && !config.mealTuning.showTooltip) {
			return;
		}

		List<Holder<Item>> ingredients = stack.get(OverhaulComponents.MEAL_INGREDIENTS);

		if (ingredients != null && !ingredients.isEmpty()) {
			lines.accept(Component.translatable("tooltip.overhaul.meal.ingredients").withStyle(ChatFormatting.GRAY));

			for (Holder<Item> ingredient : ingredients) {
				lines.accept(Component.literal(" - ")
						.append(Component.translatable(ingredient.value().getDescriptionId()))
						.withStyle(ChatFormatting.DARK_GRAY));
			}
		}

		FoodProperties food = stack.get(DataComponents.FOOD);

		if (food != null && flag.isAdvanced()) {
			lines.accept(Component.translatable("tooltip.overhaul.meal.nutrition", food.nutrition(),
					String.format("%.1f", food.saturation())).withStyle(ChatFormatting.DARK_GRAY));
		}

		Consumable consumable = stack.get(DataComponents.CONSUMABLE);

		if (consumable == null) {
			return;
		}

		consumable.onConsumeEffects().stream()
				.filter(ApplyStatusEffectsConsumeEffect.class::isInstance)
				.map(ApplyStatusEffectsConsumeEffect.class::cast)
				.forEach(effect -> {
					for (MobEffectInstance instance : effect.effects()) {
						Component name = Component.translatable(instance.getDescriptionId());
						Component amount = Component.translatable("potion.withAmplifier", name,
								Component.translatable("potion.potency." + instance.getAmplifier()));
						Component shown = instance.getAmplifier() > 0 ? amount : name;

						lines.accept(Component.translatable("tooltip.overhaul.meal.effect", shown,
								formatDuration(instance.getDuration()))
								.withStyle(instance.getEffect().value().getCategory().getTooltipFormatting()));
					}
				});
	}

	private static String formatDuration(int ticks) {
		int seconds = ticks / 20;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
