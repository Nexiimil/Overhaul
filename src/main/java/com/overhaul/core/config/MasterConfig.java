package com.overhaul.core.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code config/overhaul/overhaul.json} — the master switchboard. */
public class MasterConfig {
	public String _comment = "Set a module to false to disable it entirely. Each module has its own file in this folder.";

	public Map<String, Boolean> modules = new LinkedHashMap<>();

	/**
	 * When true, Overhaul rewrites every config file on startup after loading it, which fills in
	 * any options added by a mod update. Turn off if you want to hand-edit files with comments.
	 */
	public boolean rewriteConfigsOnLoad = true;
}
