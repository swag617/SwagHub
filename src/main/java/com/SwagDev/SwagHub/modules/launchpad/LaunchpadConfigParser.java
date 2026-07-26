package com.SwagDev.SwagHub.modules.launchpad;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Parses one {@code launchpads.yml} entry (under {@code launchpads.<id>}) into a
 * {@link LaunchpadConfig}. Pure data in, pure data out — no Bukkit {@code World}/
 * {@code Material} dependency, unit-tested directly with an in-memory
 * {@code YamlConfiguration} (see {@code LaunchpadConfigParserTest}), mirroring
 * {@code JoinItemConfigParser}'s exact shape.
 */
public final class LaunchpadConfigParser {

    private static final double DEFAULT_POWER = 1.6;
    private static final double DEFAULT_HEIGHT = 1.4;

    private LaunchpadConfigParser() {
    }

    public static Optional<LaunchpadConfig> parse(ConfigurationSection section, String id, Consumer<String> warnings) {
        if (section == null) {
            warnings.accept("Launchpad '" + id + "' has no configuration body — skipping it.");
            return Optional.empty();
        }
        if (!section.isSet("world") || section.getString("world", "").isBlank()) {
            warnings.accept("Launchpad '" + id + "' is missing a 'world' key — skipping it.");
            return Optional.empty();
        }
        if (!section.isSet("x") || !section.isSet("y") || !section.isSet("z")) {
            warnings.accept("Launchpad '" + id + "' is missing an 'x'/'y'/'z' key — skipping it.");
            return Optional.empty();
        }

        String world = section.getString("world");
        int x = section.getInt("x");
        int y = section.getInt("y");
        int z = section.getInt("z");

        double power = section.getDouble("power", DEFAULT_POWER);
        if (power <= 0) {
            warnings.accept("Launchpad '" + id + "' has a non-positive 'power' (" + power
                    + ") — falling back to the default (" + DEFAULT_POWER + ").");
            power = DEFAULT_POWER;
        }

        double height = section.getDouble("height", DEFAULT_HEIGHT);
        if (height <= 0) {
            warnings.accept("Launchpad '" + id + "' has a non-positive 'height' (" + height
                    + ") — falling back to the default (" + DEFAULT_HEIGHT + ").");
            height = DEFAULT_HEIGHT;
        }

        String particle = section.isSet("particle") ? section.getString("particle") : null;
        String sound = section.isSet("sound") ? section.getString("sound") : null;

        return Optional.of(new LaunchpadConfig(id, world, x, y, z, power, height, particle, sound));
    }
}
