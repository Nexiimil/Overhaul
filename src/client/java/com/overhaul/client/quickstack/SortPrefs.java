package com.overhaul.client.quickstack;

import com.overhaul.core.config.ConfigManager;
import com.overhaul.module.quickstack.FillOrder;
import com.overhaul.module.quickstack.SortMode;

import org.jspecify.annotations.Nullable;

/**
 * Which way the sort buttons are currently set, remembered between sessions.
 *
 * <p>This is a client preference rather than server state: the two buttons show what they will do
 * next, and the choice travels with every sort request. Keeping it here means a player's preferred
 * ordering follows them onto every server instead of being reset by each one, and the server needs
 * to remember nothing per player.
 */
public final class SortPrefs {
	private static final String FILE = "quickstack-client";

	private static @Nullable Stored stored;

	private SortPrefs() {
	}

	/** {@code config/overhaul/quickstack-client.json}. */
	public static class Stored {
		public String _comment = "Client-side only: which way the sort buttons in container screens "
				+ "are set. Changed by clicking them in game.";

		public SortMode mode = SortMode.ALPHABETICAL;
		public FillOrder order = FillOrder.HORIZONTAL;
	}

	private static Stored stored() {
		Stored current = stored;

		if (current == null) {
			current = ConfigManager.load(FILE, Stored.class);

			// Gson leaves an enum field null when the file names a constant that does not exist,
			// which a hand-edited typo is enough to produce.
			if (current.mode == null) {
				current.mode = SortMode.ALPHABETICAL;
			}

			if (current.order == null) {
				current.order = FillOrder.HORIZONTAL;
			}

			stored = current;
		}

		return current;
	}

	public static SortMode mode() {
		return stored().mode;
	}

	public static FillOrder order() {
		return stored().order;
	}

	public static SortMode cycleMode() {
		Stored current = stored();
		current.mode = current.mode.next();
		ConfigManager.save(FILE, current);
		return current.mode;
	}

	public static FillOrder cycleOrder() {
		Stored current = stored();
		current.order = current.order.next();
		ConfigManager.save(FILE, current);
		return current.order;
	}
}
