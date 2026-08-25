package com.overhaul.core;

import java.util.List;

import com.overhaul.Overhaul;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;

/** Data components shared across modules. Registered once, whichever modules are enabled. */
public final class OverhaulComponents {
	/** The ingredients a meal was assembled from, kept for the tooltip. */
	public static final DataComponentType<List<Holder<Item>>> MEAL_INGREDIENTS = DataComponentType.<List<Holder<Item>>>builder()
			.persistent(Item.CODEC.listOf())
			.networkSynchronized(Item.STREAM_CODEC.apply(ByteBufCodecs.list()))
			.build();

	private static boolean registered;

	private OverhaulComponents() {
	}

	public static void register() {
		if (registered) {
			return;
		}

		registered = true;
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Overhaul.id("meal_ingredients"), MEAL_INGREDIENTS);
	}
}
