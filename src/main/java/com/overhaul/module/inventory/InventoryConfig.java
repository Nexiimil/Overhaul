package com.overhaul.module.inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code config/overhaul/inventory.json}.
 *
 * <p>Entries in the two id lists are block or item ids, or a tag written with a leading
 * {@code #}, which is the same shorthand the tasty module's ingredient lists use.
 */
public class InventoryConfig {
	public String _comment = "Quick-stacking sends a stack to a nearby container that already holds "
			+ "that item. List entries are ids, or tags written with a leading '#'. Sorting works on "
			+ "any container you have open, including your own inventory.";

	public boolean quickStackEnabled = true;

	public boolean sortEnabled = true;

	/** How far a container can be and still be quick-stacked into, measured from the player. */
	public double radius = 5.0;

	/**
	 * What counts as a quick-stack target. Furnaces, brewing stands and droppers are containers
	 * too, so the list is an allowlist rather than a blocklist: without one, a quick-stack would
	 * scatter fuel and ingredients across every machine in the room.
	 */
	public List<String> containers = new ArrayList<>(List.of(
			"minecraft:chest",
			"minecraft:trapped_chest",
			"minecraft:barrel",
			"#minecraft:shulker_boxes"));

	/**
	 * Requires an exact component match, so an enchanted sword only joins other identically
	 * enchanted swords. Off by default: matching on the item alone is what makes a quick-stack
	 * file all your swords into the sword chest.
	 */
	public boolean matchComponents = false;

	/**
	 * Items a quick-stack always leaves where they are. Backpacks are excluded whatever this
	 * says, because posting your storage into a chest is never what the button was meant to do.
	 */
	public List<String> neverStack = new ArrayList<>();

	/** Minimum ticks between one player's quick-stacks, which caps what a spamming client costs. */
	public int cooldownTicks = 10;

	public Buttons buttons = new Buttons();

	/**
	 * Where the client draws the sort and quick-stack buttons. The server sends these to the
	 * client on join, so a server with the module off produces no buttons at all rather than
	 * buttons that quietly do nothing.
	 */
	public static class Buttons {
		public boolean enabled = true;

		/** Also draw them on the player's own inventory screen, next to the recipe book. */
		public boolean inPlayerInventory = true;
	}
}
