package com.overhaul.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.overhaul.Overhaul;
import com.overhaul.core.config.ConfigManager;
import com.overhaul.core.config.MasterConfig;
import com.overhaul.core.data.DataPackBuilder;

/**
 * Owns the module list and drives the lifecycle described in {@link OverhaulModule}.
 *
 * <p>Registration order is fixed by insertion order so that ids stay stable across
 * launches; nothing here depends on a module being present, so disabling one never
 * breaks another.
 */
public final class ModuleManager {
	private static final Map<String, OverhaulModule> MODULES = new LinkedHashMap<>();
	private static final List<OverhaulModule> ACTIVE = new ArrayList<>();

	private ModuleManager() {
	}

	public static void register(OverhaulModule module) {
		if (MODULES.putIfAbsent(module.id(), module) != null) {
			throw new IllegalStateException("Duplicate Overhaul module id: " + module.id());
		}
	}

	public static boolean isEnabled(String moduleId) {
		return ConfigManager.master().modules.getOrDefault(moduleId, Boolean.TRUE);
	}

	public static List<OverhaulModule> active() {
		return List.copyOf(ACTIVE);
	}

	/** Runs config load for every module, then content + behaviour setup for the enabled ones. */
	public static void bootstrap() {
		MasterConfig master = ConfigManager.master();

		for (OverhaulModule module : MODULES.values()) {
			// Make sure the master config lists every module we know about, so a fresh
			// install produces a complete, self-documenting toggle list.
			master.modules.putIfAbsent(module.id(), Boolean.TRUE);
			module.loadConfig();
		}

		ConfigManager.saveMaster();

		for (OverhaulModule module : MODULES.values()) {
			if (!isEnabled(module.id())) {
				Overhaul.LOGGER.info("Module '{}' is disabled in config, skipping", module.id());
				continue;
			}

			ACTIVE.add(module);
			module.registerContent();
			module.registerBehaviour();
			Overhaul.LOGGER.info("Enabled module: {}", module.displayName());
		}
	}

	public static void initClient() {
		for (OverhaulModule module : ACTIVE) {
			module.registerClient();
		}
	}

	public static void buildRecipes(DataPackBuilder pack) {
		for (OverhaulModule module : ACTIVE) {
			module.buildRecipes(pack);
		}
	}
}
