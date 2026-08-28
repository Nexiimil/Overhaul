package com.overhaul;

import com.overhaul.core.ModuleManager;
import com.overhaul.core.MoonLock;
import com.overhaul.core.OverhaulCommands;
import com.overhaul.core.data.RuntimeDataPack;
import com.overhaul.module.backpack.BackpackModule;
import com.overhaul.module.magical.MagicalModule;
import com.overhaul.module.mob.MobModule;
import com.overhaul.module.quickstack.QuickStackModule;
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
		ModuleManager.register(new TastyModule());
		ModuleManager.register(new BackpackModule());
		ModuleManager.register(new MagicalModule());
		ModuleManager.register(new MobModule());
		ModuleManager.register(new QuickStackModule());

		ModuleManager.bootstrap();
		RuntimeDataPack.rebuild();
		OverhaulCommands.register();
		MoonLock.register();
	}
}
