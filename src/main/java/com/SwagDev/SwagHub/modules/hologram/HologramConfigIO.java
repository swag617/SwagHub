package com.SwagDev.SwagHub.modules.hologram;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Whole-file load/save for {@code holograms.yml}'s {@code holograms:} id-keyed map —
 * plain {@link File} + {@link YamlConfiguration} only, NO dependency on a running
 * {@code Plugin}/{@code Server}/{@code World}, mirroring {@code SpawnLocationIO}'s
 * exact shape (see DECISIONS.md Step 2 ambiguity 8) so it's unit-testable with real
 * temp files and no server bootstrap (see {@code HologramConfigIOTest}).
 *
 * <p>{@link #saveAll} preserves every OTHER top-level key already in the file (namely
 * {@code update-interval-ticks}/{@code line-spacing}) by loading the existing file
 * first and only replacing the {@code holograms:} section — a command-triggered save
 * (create/delete/addline/setline/removeline/movehere) must never silently wipe out an
 * admin's module-wide settings.</p>
 */
public final class HologramConfigIO {

    private static final String ROOT_KEY = "holograms";

    private HologramConfigIO() {
    }

    /** @return the id -> config map, or an empty map if the file doesn't exist yet or has no {@code holograms:} section. */
    public static Map<String, HologramConfig> loadAll(File file, Consumer<String> warnings) {
        Map<String, HologramConfig> parsed = new LinkedHashMap<>();
        if (!file.exists()) {
            return parsed;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection holograms = yaml.getConfigurationSection(ROOT_KEY);
        if (holograms == null) {
            return parsed;
        }
        for (String id : holograms.getKeys(false)) {
            HologramConfigParser.parse(holograms.getConfigurationSection(id), id, warnings)
                    .ifPresent(config -> parsed.put(id, config));
        }
        return parsed;
    }

    /**
     * Writes {@code holograms} to {@code file} synchronously (creating parent
     * directories as needed), preserving any other top-level keys already present in
     * the file. Callers needing "immediately on change" persistence (§4) must call
     * this directly on the calling thread, not hand it off to an async task.
     */
    public static void saveAll(File file, Map<String, HologramConfig> holograms) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create parent directory: " + parent);
        }

        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        yaml.set(ROOT_KEY, null); // clear stale entries (renamed/removed ids) before rewriting

        for (Map.Entry<String, HologramConfig> entry : holograms.entrySet()) {
            String base = ROOT_KEY + "." + entry.getKey();
            HologramConfig config = entry.getValue();
            yaml.set(base + ".world", config.getWorldName());
            yaml.set(base + ".x", config.getX());
            yaml.set(base + ".y", config.getY());
            yaml.set(base + ".z", config.getZ());
            yaml.set(base + ".lines", config.getLines());
            if (config.getRefreshIntervalTicks() != null) {
                yaml.set(base + ".refresh-interval-ticks", config.getRefreshIntervalTicks());
            }
        }

        yaml.save(file);
    }
}
