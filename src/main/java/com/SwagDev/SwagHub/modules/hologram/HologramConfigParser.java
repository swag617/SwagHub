package com.SwagDev.SwagHub.modules.hologram;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Parses one {@code holograms.yml} entry (under {@code holograms.<id>}) into a
 * {@link HologramConfig}. Pure data in, pure data out — no Bukkit {@code World}/
 * {@code Entity} dependency, unit-tested directly with an in-memory
 * {@code YamlConfiguration} (see {@code HologramConfigParserTest}), mirroring
 * {@code LaunchpadConfigParser}'s exact shape.
 *
 * <p>Per §10.4: a hologram entry with no body, a missing/blank world, or a missing
 * x/y/z coordinate is skipped with one specific warning naming the id — never aborts
 * the rest of the file. A hologram with zero configured lines is ALSO skipped with a
 * warning (rather than silently producing an invisible hologram) — {@code
 * HologramModule} itself enforces the matching invariant that {@code removeline} can
 * never remove a hologram's last remaining line (it must be deleted instead), so this
 * parser-side "at least one line" requirement never conflicts with any in-app mutation
 * flow.</p>
 */
public final class HologramConfigParser {

    private HologramConfigParser() {
    }

    public static Optional<HologramConfig> parse(ConfigurationSection section, String id, Consumer<String> warnings) {
        if (section == null) {
            warnings.accept("Hologram '" + id + "' has no configuration body — skipping it.");
            return Optional.empty();
        }
        if (!section.isSet("world") || section.getString("world", "").isBlank()) {
            warnings.accept("Hologram '" + id + "' is missing a 'world' key — skipping it.");
            return Optional.empty();
        }
        if (!section.isSet("x") || !section.isSet("y") || !section.isSet("z")) {
            warnings.accept("Hologram '" + id + "' is missing an 'x'/'y'/'z' key — skipping it.");
            return Optional.empty();
        }

        String world = section.getString("world");
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");

        List<String> lines = new ArrayList<>(section.getStringList("lines"));
        if (lines.isEmpty()) {
            warnings.accept("Hologram '" + id + "' has no configured 'lines' — skipping it.");
            return Optional.empty();
        }

        Long refreshIntervalTicks = null;
        if (section.isSet("refresh-interval-ticks")) {
            long raw = section.getLong("refresh-interval-ticks");
            if (raw <= 0) {
                warnings.accept("Hologram '" + id + "' has a non-positive 'refresh-interval-ticks' (" + raw
                        + ") — falling back to the module-wide default instead.");
            } else {
                refreshIntervalTicks = raw;
            }
        }

        return Optional.of(new HologramConfig(id, world, x, y, z, lines, refreshIntervalTicks));
    }
}
