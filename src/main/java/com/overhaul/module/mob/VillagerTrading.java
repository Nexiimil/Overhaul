package com.overhaul.module.mob;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.overhaul.Overhaul;
import com.overhaul.core.data.DataPackBuilder;
import com.overhaul.module.mob.MobConfig.TradeEntry;
import com.overhaul.module.mob.MobConfig.VillagerSettings;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Writes the configured extra trades out as real villager trade files.
 *
 * <p>Trades became data in 26.2, which is the only reason this module can widen them at all
 * without a mixin: each entry in the config turns into an ordinary trade file plus one line in the
 * vanilla pool tag for that profession and level. Tags merge across data packs, so declaring only
 * our own entries adds to what a villager might offer instead of replacing it.
 *
 * <p>Adding to a pool widens the range rather than lengthening the list. A villager still rolls the
 * usual number of trades per level; there are simply more things it could have rolled. That is the
 * behaviour worth having — a farmer who might buy tomatoes is more interesting than a farmer who
 * always does.
 *
 * <p>Every item id is checked against the registry before anything is written. Most of these
 * trades sell items belonging to other Overhaul modules, and a module the player has switched off
 * has not registered its items — so the trade for them has to disappear with it rather than
 * become a data pack error on every world load.
 */
public final class VillagerTrading {
	private VillagerTrading() {
	}

	public static void build(DataPackBuilder pack, VillagerSettings settings) {
		if (!settings.expandedTrades || settings.addedTrades.isEmpty()) {
			return;
		}

		Map<String, List<String>> pools = new LinkedHashMap<>();

		settings.addedTrades.forEach((key, entry) -> {
			if (!entry.enabled) {
				return;
			}

			String[] parts = key.split("/");

			if (parts.length != 3) {
				Overhaul.LOGGER.warn("Villager trade key '{}' should be <profession>/<level>/<name>", key);
				return;
			}

			JsonObject json = toJson(entry);

			if (json == null) {
				// Names an item nothing registered. Expected whenever a module is switched off, so
				// this is a debug line rather than a warning.
				Overhaul.LOGGER.debug("Skipping villager trade '{}': it names an item that does not exist", key);
				return;
			}

			Identifier id = Overhaul.id(key);
			pack.addFile(Identifier.fromNamespaceAndPath(id.getNamespace(), "villager_trade/" + id.getPath() + ".json"),
					json);
			pools.computeIfAbsent(parts[0] + "/" + poolName(parts[1]), pool -> new ArrayList<>()).add(id.toString());
		});

		pools.forEach((pool, trades) -> {
			JsonArray values = new JsonArray();
			trades.forEach(values::add);

			JsonObject tag = new JsonObject();
			tag.add("values", values);

			pack.addFile(Identifier.withDefaultNamespace("tags/villager_trade/" + pool + ".json"), tag);
		});
	}

	/**
	 * Vanilla names the numbered pools {@code level_1} through {@code level_5} while the trades
	 * themselves sit in a folder named by the bare number, so the two have to be spelled
	 * differently. Anything that is not a number is passed through, which is what lets a pack point
	 * an entry at the wandering trader's {@code common} and {@code uncommon} pools.
	 */
	private static String poolName(String level) {
		return level.chars().allMatch(Character::isDigit) ? "level_" + level : level;
	}

	private static @Nullable JsonObject toJson(TradeEntry entry) {
		JsonObject wants = stack(entry.wants, entry.wantsCount);
		JsonObject gives = stack(entry.gives, entry.givesCount);

		if (wants == null || gives == null) {
			return null;
		}

		JsonObject json = new JsonObject();
		json.add("wants", wants);

		if (!entry.alsoWants.isBlank()) {
			JsonObject alsoWants = stack(entry.alsoWants, entry.alsoWantsCount);

			if (alsoWants == null) {
				return null;
			}

			json.add("additional_wants", alsoWants);
		}

		json.add("gives", gives);
		json.addProperty("max_uses", Math.max(1, entry.maxUses));
		json.addProperty("xp", Math.max(0, entry.villagerExperience));
		json.addProperty("reputation_discount", entry.reputationDiscount);
		return json;
	}

	private static @Nullable JsonObject stack(String id, int count) {
		if (id.isBlank() || BuiltInRegistries.ITEM.get(Identifier.parse(id)).isEmpty()) {
			return null;
		}

		JsonObject stack = new JsonObject();
		stack.addProperty("id", id);
		stack.addProperty("count", Math.max(1, count));
		return stack;
	}
}
