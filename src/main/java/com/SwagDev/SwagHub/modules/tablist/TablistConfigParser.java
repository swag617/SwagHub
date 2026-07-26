package com.SwagDev.SwagHub.modules.tablist;

import com.SwagDev.SwagHub.util.AnimatedText;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Parses {@code tablist.yml}'s {@code worlds:} section into a
 * {@code world name (or "default") -> TablistWorldConfig} map. Pure data in, pure data
 * out — no Bukkit {@code Server}/{@code Plugin} dependency, unit-tested directly in
 * {@code TablistConfigParserTest} with an in-memory {@code YamlConfiguration}.
 */
public final class TablistConfigParser {

    private TablistConfigParser() {
    }

    public static Map<String, TablistWorldConfig> parseWorlds(ConfigurationSection worldsSection, Consumer<String> warnings) {
        Map<String, TablistWorldConfig> parsed = new LinkedHashMap<>();
        if (worldsSection == null) {
            return parsed;
        }
        for (String key : worldsSection.getKeys(false)) {
            ConfigurationSection worldSection = worldsSection.getConfigurationSection(key);
            if (worldSection == null) {
                warnings.accept("Tablist world entry '" + key + "' has no configuration body — skipping it.");
                continue;
            }

            AnimatedText header = AnimatedText.parse(worldSection.getConfigurationSection("header"),
                    "Tablist world '" + key + "'s header", warnings);
            AnimatedText footer = AnimatedText.parse(worldSection.getConfigurationSection("footer"),
                    "Tablist world '" + key + "'s footer", warnings);

            parsed.put(key, new TablistWorldConfig(header, footer));
        }
        return parsed;
    }
}
