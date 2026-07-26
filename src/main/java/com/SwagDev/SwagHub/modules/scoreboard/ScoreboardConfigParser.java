package com.SwagDev.SwagHub.modules.scoreboard;

import com.SwagDev.SwagHub.util.AnimatedText;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Parses {@code scoreboard.yml}'s {@code worlds:} section into a
 * {@code world name (or "default") -> ScoreboardWorldConfig} map. Pure data in, pure
 * data out — no Bukkit {@code Server}/{@code Plugin} dependency, unit-tested directly
 * in {@code ScoreboardConfigParserTest} with an in-memory {@code YamlConfiguration}.
 *
 * <p>Per §10.4: a world entry with no body, or more than {@link #MAX_LINES} configured
 * lines, never aborts the rest of the file — a missing body is skipped with a warning;
 * an oversized {@code lines:} list is clamped to Minecraft's real sidebar line limit
 * (15) with a warning, keeping the first {@link #MAX_LINES} entries.</p>
 */
public final class ScoreboardConfigParser {

    /** Minecraft's real sidebar objective line limit. */
    public static final int MAX_LINES = 15;

    private ScoreboardConfigParser() {
    }

    public static Map<String, ScoreboardWorldConfig> parseWorlds(ConfigurationSection worldsSection, Consumer<String> warnings) {
        Map<String, ScoreboardWorldConfig> parsed = new LinkedHashMap<>();
        if (worldsSection == null) {
            return parsed;
        }
        for (String key : worldsSection.getKeys(false)) {
            ConfigurationSection worldSection = worldsSection.getConfigurationSection(key);
            if (worldSection == null) {
                warnings.accept("Scoreboard world entry '" + key + "' has no configuration body — skipping it.");
                continue;
            }

            AnimatedText title = AnimatedText.parse(worldSection.getConfigurationSection("title"),
                    "Scoreboard world '" + key + "'s title", warnings);

            List<String> lines = worldSection.getStringList("lines");
            if (lines.size() > MAX_LINES) {
                warnings.accept("Scoreboard world '" + key + "' has " + lines.size()
                        + " lines configured — clamping to the sidebar's " + MAX_LINES + "-line limit.");
                lines = lines.subList(0, MAX_LINES);
            }

            parsed.put(key, new ScoreboardWorldConfig(title, lines));
        }
        return parsed;
    }
}
