package com.overhaul.core.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.overhaul.Overhaul;

import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * Reads and writes the JSON config tree under {@code config/overhaul/}.
 *
 * <p>Missing fields in a user's file keep the value they were initialised with in the POJO, so
 * adding a new option to a config class is always backwards compatible. Files are rewritten
 * after load (unless the user turns that off) so new options show up on disk immediately.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private static @Nullable Path root;
	private static @Nullable MasterConfig master;

	private ConfigManager() {
	}

	public static Path root() {
		Path directory = root;

		if (directory == null) {
			directory = FabricLoader.getInstance().getConfigDir().resolve(Overhaul.MOD_ID);

			try {
				Files.createDirectories(directory);
			} catch (IOException e) {
				throw new IllegalStateException("Could not create Overhaul config directory", e);
			}

			// Publish the field only once the directory exists, so a caller can never see a path
			// that has not been created yet.
			root = directory;
		}

		return directory;
	}

	public static MasterConfig master() {
		MasterConfig current = master;

		if (current == null) {
			current = load(Overhaul.MOD_ID, MasterConfig.class);
			master = current;
		}

		return current;
	}

	public static void saveMaster() {
		save(Overhaul.MOD_ID, master());
	}

	/**
	 * Loads {@code <name>.json}, falling back to a fresh default instance if the file is absent
	 * or unreadable. A corrupt file is moved aside rather than deleted so nothing is ever lost.
	 */
	// The analysis cannot tell that a free type variable is non-null even once flow analysis has
	// proved the value is; T is always a config class here, and config classes are never null.
	@SuppressWarnings("null")
	public static <T> T load(String name, Class<T> type) {
		Path file = root().resolve(name + ".json");
		@Nullable T parsed = null;

		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				parsed = GSON.fromJson(reader, type);
			} catch (IOException | JsonSyntaxException e) {
				Overhaul.LOGGER.error("Could not read config {}, backing it up and regenerating", file, e);
				backup(file);
			}
		}

		// Gson returns null for an empty or all-comment file as well as for a missing one, so the
		// fallback covers more than just "the file was not there".
		T value = parsed != null ? parsed : instantiate(type);
		MasterConfig current = master;

		if (current == null || current.rewriteConfigsOnLoad || !Files.exists(file)) {
			save(name, value);
		}

		return value;
	}

	public static void save(String name, Object value) {
		Path file = root().resolve(name + ".json");

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(value, writer);
		} catch (IOException e) {
			Overhaul.LOGGER.error("Could not write config {}", file, e);
		}
	}

	private static void backup(Path file) {
		Path target = file.resolveSibling(file.getFileName() + ".broken");

		try {
			Files.deleteIfExists(target);
			Files.move(file, target);
		} catch (IOException e) {
			Overhaul.LOGGER.error("Could not back up broken config {}", file, e);
		}
	}

	private static <T> T instantiate(Class<T> type) {
		try {
			return type.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Config class " + type.getName() + " needs a no-arg constructor", e);
		}
	}
}
