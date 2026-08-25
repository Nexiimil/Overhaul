package com.overhaul.core.data;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.overhaul.Overhaul;
import com.overhaul.core.ModuleManager;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Serves Overhaul's config-generated data pack straight out of memory.
 *
 * <p>The pack is marked required and fixed at the top of the stack, so it always applies and a
 * player cannot end up in a world where the items exist but their recipes do not. A world data
 * pack can still override individual recipes by declaring the same id, which is the usual way
 * pack precedence works.
 */
public final class RuntimeDataPack implements PackResources, RepositorySource {
	public static final String PACK_ID = Overhaul.MOD_ID + "_generated";

	private static final PackLocationInfo LOCATION = new PackLocationInfo(
			PACK_ID,
			Component.literal("Overhaul (generated)"),
			PackSource.BUILT_IN,
			Optional.empty());

	private static final PackSelectionConfig SELECTION = new PackSelectionConfig(true, Pack.Position.TOP, true);

	private static final RuntimeDataPack INSTANCE = new RuntimeDataPack();

	private Map<Identifier, byte[]> files = Map.of();
	private final Map<Identifier, byte[]> assets = new java.util.LinkedHashMap<>();

	private RuntimeDataPack() {
	}

	public static RuntimeDataPack instance() {
		return INSTANCE;
	}

	/**
	 * Adds a client resource, such as a texture override, that only applies while the feature that
	 * asked for it is enabled. Assets survive a rebuild because resource packs are read once at
	 * client startup, well before a config reload could clear them.
	 */
	public void addAsset(Identifier location, byte[] bytes) {
		assets.put(location, bytes);
	}

	/** Regenerates every module's config-driven data. Safe to call again after a config reload. */
	public static void rebuild() {
		DataPackBuilder builder = new DataPackBuilder();
		ModuleManager.buildRecipes(builder);

		INSTANCE.files = Map.copyOf(builder.files());

		Overhaul.LOGGER.info("Generated {} data pack file(s) from config", INSTANCE.files.size());
	}

	// RepositorySource ------------------------------------------------------------------------

	@Override
	public void loadPacks(Consumer<Pack> output) {
		output.accept(new Pack(LOCATION, new Pack.ResourcesSupplier() {
			@Override
			public PackResources openPrimary(PackLocationInfo location) {
				return INSTANCE;
			}

			@Override
			public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
				return INSTANCE;
			}
		}, metadata(), SELECTION));
	}

	private static Pack.Metadata metadata() {
		return new Pack.Metadata(
				Component.literal("Recipes and data generated from the Overhaul config"),
				net.minecraft.server.packs.repository.PackCompatibility.COMPATIBLE,
				net.minecraft.world.flag.FeatureFlagSet.of(),
				java.util.List.of());
	}

	// PackResources ---------------------------------------------------------------------------

	@Override
	public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
		return null;
	}

	@Override
	public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
		byte[] bytes = contents(type).get(location);
		return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
	}

	@Override
	public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
		String prefix = directory.endsWith("/") ? directory : directory + "/";

		contents(type).forEach((id, bytes) -> {
			if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
				output.accept(id, () -> new ByteArrayInputStream(bytes));
			}
		});
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		return contents(type).keySet().stream()
				.map(Identifier::getNamespace)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private Map<Identifier, byte[]> contents(PackType type) {
		return type == PackType.SERVER_DATA ? files : assets;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) {
		if (type == PackMetadataSection.SERVER_TYPE) {
			return (T) describe(PackType.SERVER_DATA);
		}

		if (type == PackMetadataSection.CLIENT_TYPE || type == PackMetadataSection.FALLBACK_TYPE) {
			return (T) describe(PackType.CLIENT_RESOURCES);
		}

		return null;
	}

	private static PackMetadataSection describe(PackType type) {
		return new PackMetadataSection(
				Component.literal("Generated from the Overhaul config"),
				SharedConstants.getCurrentVersion().packVersion(type).minorRange());
	}

	@Override
	public PackLocationInfo location() {
		return LOCATION;
	}

	@Override
	public void close() {
		// Nothing to release: the pack is a map held for the lifetime of the game.
	}
}
