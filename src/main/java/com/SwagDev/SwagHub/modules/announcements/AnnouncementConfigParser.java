package com.SwagDev.SwagHub.modules.announcements;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Parses {@code announcements.yml}'s {@code worlds:} section into a
 * {@code world name (or "default") -> AnnouncementWorldConfig} map. Pure data in, pure
 * data out — no Bukkit {@code Server}/{@code Plugin} dependency, unit-tested directly
 * in {@code AnnouncementConfigParserTest} with an in-memory {@code YamlConfiguration}.
 *
 * <p>{@code entries:} is a YAML list of maps (each {@code {actions: [...]}}), read via
 * {@link ConfigurationSection#getMapList(String)} — per this project's own
 * cross-project convention note on avoiding {@code Map<?,?>} generic-capture pitfalls
 * with config-parsed map lists, this never calls {@code getOrDefault} on the raw
 * wildcard map; only {@code .get(...)} + {@code instanceof} pattern matching.</p>
 *
 * <p>Per §10.4: an entry with no (or an empty) {@code actions} list logs one warning
 * naming the world and entry index and is skipped — never aborts the rest of that
 * world's entries or the rest of the file. An unrecognized {@code rotation} value logs
 * one warning and defaults to {@code sequential}.</p>
 */
public final class AnnouncementConfigParser {

    private AnnouncementConfigParser() {
    }

    public static Map<String, AnnouncementWorldConfig> parseWorlds(ConfigurationSection worldsSection, Consumer<String> warnings) {
        Map<String, AnnouncementWorldConfig> parsed = new LinkedHashMap<>();
        if (worldsSection == null) {
            return parsed;
        }
        for (String key : worldsSection.getKeys(false)) {
            ConfigurationSection worldSection = worldsSection.getConfigurationSection(key);
            if (worldSection == null) {
                warnings.accept("Announcement world entry '" + key + "' has no configuration body — skipping it.");
                continue;
            }

            Rotation rotation = parseRotation(worldSection.getString("rotation", "sequential"), key, warnings);

            Long intervalOverride = null;
            if (worldSection.isSet("interval-ticks")) {
                long value = worldSection.getLong("interval-ticks");
                if (value > 0) {
                    intervalOverride = value;
                } else {
                    warnings.accept("Announcement world '" + key + "' has a non-positive 'interval-ticks' ("
                            + value + ") — ignoring it and falling back to 'default-interval-ticks'.");
                }
            }

            List<AnnouncementEntry> entries = parseEntries(worldSection, key, warnings);

            parsed.put(key, new AnnouncementWorldConfig(rotation, intervalOverride, entries));
        }
        return parsed;
    }

    private static Rotation parseRotation(String raw, String worldKey, Consumer<String> warnings) {
        if (raw == null || raw.isBlank()) {
            return Rotation.SEQUENTIAL;
        }
        try {
            return Rotation.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            warnings.accept("Announcement world '" + worldKey + "' has an unrecognized 'rotation' value '"
                    + raw + "' — defaulting to 'sequential'.");
            return Rotation.SEQUENTIAL;
        }
    }

    private static List<AnnouncementEntry> parseEntries(ConfigurationSection worldSection, String worldKey, Consumer<String> warnings) {
        List<Map<?, ?>> rawEntries = worldSection.getMapList("entries");
        List<AnnouncementEntry> entries = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> rawEntry : rawEntries) {
            index++;
            Object actionsObj = rawEntry.get("actions");
            if (!(actionsObj instanceof List<?> actionsList) || actionsList.isEmpty()) {
                warnings.accept("Announcement world '" + worldKey + "' entry #" + index
                        + " has no 'actions' list — skipping this entry.");
                continue;
            }
            List<String> actions = new ArrayList<>();
            for (Object action : actionsList) {
                if (action != null) {
                    actions.add(action.toString());
                }
            }
            if (actions.isEmpty()) {
                warnings.accept("Announcement world '" + worldKey + "' entry #" + index
                        + " has an empty 'actions' list — skipping this entry.");
                continue;
            }
            entries.add(new AnnouncementEntry(actions));
        }
        return entries;
    }
}
