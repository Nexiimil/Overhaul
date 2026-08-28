package com.overhaul;

import com.overhaul.core.ModuleManager;
import com.overhaul.core.config.ConfigManager;
import com.overhaul.core.MoonLock;
import com.overhaul.core.OverhaulCommands;
import com.overhaul.core.data.RuntimeDataPack;
import com.overhaul.module.backpack.BackpackModule;
import com.overhaul.module.magical.MagicalModule;
import com.overhaul.module.mob.MobModule;
import com.overhaul.module.inventory.InventoryModule;
import com.overhaul.module.tasty.TastyModule;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Overhaul implements ModInitializer {
	public static final String MOD_ID = "overhaul";
	public static final Logger LOGGER = LoggerFactory.getLogger("Overhaul");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// Renames this mod has made to its own config, applied before anything reads a module id.
		// A fact about the mod's history rather than about any one module, so it is stated here.
		ConfigManager.renameModule("quickstack", "inventory");
		ConfigManager.renameFile("quickstack-client", "inventory-client");

		ModuleManager.register(new TastyModule());
		ModuleManager.register(new BackpackModule());
		ModuleManager.register(new MagicalModule());
		ModuleManager.register(new MobModule());
		ModuleManager.register(new InventoryModule());

		ModuleManager.bootstrap();
		RuntimeDataPack.rebuild();
		OverhaulCommands.register();
		MoonLock.register();
	}
}
