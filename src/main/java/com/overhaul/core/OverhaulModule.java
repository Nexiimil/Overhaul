package com.overhaul.core;

import com.overhaul.core.data.DataPackBuilder;

/**
 * A self-contained feature set that can be switched off wholesale from the master config.
 *
 * <p>Lifecycle, in order:
 * <ol>
 *   <li>{@link #loadConfig()} — always called, even for disabled modules, so that the config
 *       file exists on disk and can be edited to turn the module on.</li>
 *   <li>{@link #registerContent()} — only for enabled modules. Registers items, blocks,
 *       entities, components; anything that must land in a registry before freeze.</li>
 *   <li>{@link #registerBehaviour()} — only for enabled modules. Event listeners, callbacks
 *       and anything that does not touch a registry.</li>
 *   <li>{@link #buildRecipes(DataPackBuilder)} — only for enabled modules. Emits the
 *       config-driven recipes into the runtime data pack.</li>
 * </ol>
 */
public interface OverhaulModule {

	/** Config key and resource namespace suffix for this module, e.g. {@code "tasty"}. */
	String id();

	/** Human readable name used in log output. */
	String displayName();

	void loadConfig();

	default void registerContent() {
	}

	default void registerBehaviour() {
	}

	default void buildRecipes(DataPackBuilder pack) {
	}

	/** Client-only setup: model predicates, screens, colour providers, render layers. */
	default void registerClient() {
	}
}
