package com.overhaul.module.tasty;

import java.util.Map;

import com.overhaul.Overhaul;
import com.overhaul.module.tasty.TastyConfig.FoodEntry;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Makes items that already exist edible, or edible on different terms.
 *
 * <p>Written as the same {@link FoodEntry} the module's own foods use, so a glistering melon slice
 * is configured exactly the way a tomato is. That is the point of doing it here rather than as a
 * one-off: the mechanism that makes glistering melon worth eating is also the mechanism a pack uses
 * to retune bread, or to make a modded item edible without that mod knowing anything about it.
 *
 * <p>Default components are rewritten at registry load rather than patched onto individual stacks,
 * so every glistering melon slice in the world is edible, including ones sitting in chests since
 * before the module was installed.
 */
public final class VanillaFoods {
	private VanillaFoods() {
	}

	public static void apply(Map<String, FoodEntry> entries) {
		if (entries.isEmpty()) {
			return;
		}

		DefaultItemComponentEvents.MODIFY.register(context -> entries.forEach((id, entry) -> {
			if (!entry.enabled) {
				return;
			}

			Item item = BuiltInRegistries.ITEM.get(Identifier.parse(id)).map(holder -> holder.value()).orElse(null);

			if (item == null) {
				Overhaul.LOGGER.warn("Cannot retune '{}' as a food: no such item", id);
				return;
			}

			context.modify(item, builder -> {
				if (!entry.edible) {
					// A pack can also take food away, which is the only way to stop something being
					// eaten short of removing the item itself. A null value is how the builder
					// expresses "no component" — it drops the entry rather than storing a null.
					builder.set(DataComponents.FOOD, null);
					builder.set(DataComponents.CONSUMABLE, null);
					return;
				}

				builder.set(DataComponents.FOOD, TastyContent.foodProperties(entry));
				builder.set(DataComponents.CONSUMABLE, TastyContent.consumable(entry));
			});
		}));
	}
}
