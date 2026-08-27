package com.overhaul.module.backpack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.overhaul.core.config.RecipeSpec;

/**
 * {@code config/overhaul/backpack.json}.
 *
 * <p>Tiers are an ordered map, and each one names the tier it upgrades from plus the material the
 * smithing table asks for. Adding a tier is therefore a config edit: give it a name, a row count
 * and an upgrade material, and the smithing recipe is generated for it. Removing one is the same
 * in reverse — just re-point the next tier's {@code upgradeFrom}.
 */
public class BackpackConfig {
	public String _comment = "Tier order is the order of the 'tiers' map. Each tier upgrades from the one named "
			+ "in upgradeFrom using upgradeMaterial in a smithing table with the upgrade template. "
			+ "Rows must be 1-6 because backpacks reuse the vanilla chest screens.";

	/** Letting a backpack hold another backpack makes it easy to lose track of items. */
	public boolean allowNesting = false;

	/**
	 * Whether the backpack key opens the first backpack the player is carrying. With this off the
	 * server ignores the request; a backpack can still be opened by holding it and using it.
	 */
	public boolean openWithKeybind = true;

	public Map<String, TierEntry> tiers = new LinkedHashMap<>();

	public TemplateEntry upgradeTemplate = new TemplateEntry();

	public Overburden overburden = new Overburden();

	/**
	 * The cost of carrying too much at once.
	 *
	 * <p>Without this there is no reason not to walk around with a bag for every kind of loot, which
	 * makes the upgrade ladder pointless: six leather bags beat one netherite one. Putting a limit on
	 * how many you can carry comfortably turns the ladder into the answer — one bigger bag instead of
	 * more bags.
	 */
	public static class Overburden {
		public boolean enabled = true;

		/**
		 * How many backpacks you can carry before it starts to weigh on you. Counted across your
		 * whole inventory, including armour and offhand slots.
		 */
		public int maxCarried = 3;

		/**
		 * Counts each backpack's rows rather than counting bags, so a netherite bag weighs six times
		 * what a leather one does. Turning this on changes what {@link #maxCarried} is measured in,
		 * from bags to rows.
		 */
		public boolean weighBySize = false;

		/** An empty bag is just leather; only what you have actually filled counts against you. */
		public boolean ignoreEmpty = true;

		/** Every backpack past the limit raises each effect below by this many levels. */
		public int amplifierPerExtra = 1;

		/** Ceiling on that escalation, so twenty bags is bad rather than unplayable. */
		public int maxAmplifier = 3;

		/** Effect particles are noisy for something that is permanent while you are loaded up. */
		public boolean showParticles = false;

		/** How often the check runs. The effects are refreshed for three times this long. */
		public int checkIntervalTicks = 20;

		/** What being overburdened does to you. */
		public List<BurdenEffect> effects = new ArrayList<>();
	}

	/**
	 * One effect applied while overburdened. There is no duration here on purpose: the effect is
	 * topped up for as long as you are over the limit and fades a few seconds after you drop below.
	 */
	public static class BurdenEffect {
		public String effect = "minecraft:slowness";
		public int amplifier = 0;

		public BurdenEffect() {
		}

		public BurdenEffect(String effect, int amplifier) {
			this.effect = effect;
			this.amplifier = amplifier;
		}
	}

	public static class TierEntry {
		public boolean enabled = true;
		/** Rows of nine slots, 1 to 6. */
		public int rows = 1;
		public boolean fireResistant = false;
		/** Item id of the tier this upgrades from. Empty means this tier is crafted from scratch. */
		public String upgradeFrom = "";
		/** Item the smithing table consumes to perform the upgrade. */
		public String upgradeMaterial = "";
		/** Crafting recipes producing this tier directly, usually only used by the base tier. */
		public Map<String, RecipeSpec> recipes = new LinkedHashMap<>();
	}

	public static class TemplateEntry {
		public boolean enabled = true;
		/** Loot tables the template can appear in. */
		public List<String> lootTables = new ArrayList<>();
		/** Chance for the template to appear in each of those chests. */
		public float lootChance = 0.2F;
		public Map<String, RecipeSpec> recipes = new LinkedHashMap<>();
	}
}
