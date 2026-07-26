package com.SwagDev.SwagHub.modules.portal;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Whole-file load/save for {@code portals.yml}'s {@code portals:} id-keyed map —
 * plain {@link File} + {@link YamlConfiguration} only, no dependency on a running
 * {@code Plugin}/{@code Server}/{@code World}, mirroring {@code HologramConfigIO}'s
 * exact shape so it is unit-testable with real temp files and no server bootstrap.
 *
 * <p>{@link #saveAll} preserves the top-level {@code cooldown-seconds} default already
 * in the file by loading it first and only replacing the {@code portals:} section — a
 * command-triggered save ({@code /ah portal create}/{@code delete}) must never
 * silently wipe out an admin's module-wide cooldown setting.</p>
 */
public final class PortalRegionIO {

    private static final String ROOT_KEY = "portals";

    private PortalRegionIO() {
    }

    /** @return the id -> region map, or an empty map if the file doesn't exist yet or has no {@code portals:} section. */
    public static Map<String, PortalRegion> loadAll(File file, Consumer<String> warnings) {
        Map<String, PortalRegion> parsed = new LinkedHashMap<>();
        if (!file.exists()) {
            return parsed;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection portals = yaml.getConfigurationSection(ROOT_KEY);
        if (portals == null) {
            return parsed;
        }
        for (String id : portals.getKeys(false)) {
            PortalRegionParser.parse(portals.getConfigurationSection(id), id, warnings)
                    .ifPresent(region -> parsed.put(id, region));
        }
        return parsed;
    }

    /**
     * Writes {@code portals} to {@code file} synchronously (creating parent
     * directories as needed), preserving any other top-level keys already present in
     * the file (namely {@code cooldown-seconds}). Callers needing "immediately on
     * change" persistence (§4) must call this directly on the calling thread.
     */
    public static void saveAll(File file, Map<String, PortalRegion> portals) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create parent directory: " + parent);
        }

        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        yaml.set(ROOT_KEY, null); // clear stale entries (renamed/removed ids) before rewriting

        for (Map.Entry<String, PortalRegion> entry : portals.entrySet()) {
            String base = ROOT_KEY + "." + entry.getKey();
            PortalRegion region = entry.getValue();
            yaml.set(base + ".world", region.getWorldName());
            yaml.set(base + ".server", region.getServerName());
            yaml.set(base + ".corner1.x", region.getMinX());
            yaml.set(base + ".corner1.y", region.getMinY());
            yaml.set(base + ".corner1.z", region.getMinZ());
            yaml.set(base + ".corner2.x", region.getMaxX());
            yaml.set(base + ".corner2.y", region.getMaxY());
            yaml.set(base + ".corner2.z", region.getMaxZ());
            if (region.getCooldownSecondsOverride() != null) {
                yaml.set(base + ".cooldown-seconds", region.getCooldownSecondsOverride());
            }
        }

        yaml.save(file);
    }
}
