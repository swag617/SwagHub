package com.SwagDev.SwagHub.modules.portal;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Parses one {@code portals.yml} entry (under {@code portals.<id>}) into a
 * {@link PortalRegion}. Pure data in, pure data out — {@link ConfigurationSection}
 * only, no live {@code World}/{@code Location} dependency (the parsed
 * {@link PortalRegion}'s {@code contains(Location)} method is Bukkit-facing, but
 * nothing about PARSING one needs a live server) — unit-tested directly with an
 * in-memory {@code YamlConfiguration} (see {@code PortalRegionParserTest}), mirroring
 * {@code HologramConfigParser}/{@code LaunchpadConfigParser}'s exact shape. Unlike
 * {@code PvpZone}/{@code DoubleJumpRegion} (whose parsing is inline inside their owning
 * module, since {@code pvp-zones}/{@code double-jump.regions} are small lists nested
 * inside a broader config section), portals get their own dedicated file with an
 * id-keyed map — the same shape that already earns {@code items.yml}/
 * {@code launchpads.yml}/{@code holograms.yml} a real, separately-tested parser class.
 */
public final class PortalRegionParser {

    private PortalRegionParser() {
    }

    public static Optional<PortalRegion> parse(ConfigurationSection section, String id, Consumer<String> warnings) {
        if (section == null) {
            warnings.accept("Portal '" + id + "' has no configuration body — skipping it.");
            return Optional.empty();
        }
        if (!section.isSet("world") || section.getString("world", "").isBlank()) {
            warnings.accept("Portal '" + id + "' is missing a 'world' key — skipping it.");
            return Optional.empty();
        }
        if (!section.isSet("server") || section.getString("server", "").isBlank()) {
            warnings.accept("Portal '" + id + "' is missing a 'server' key — skipping it.");
            return Optional.empty();
        }
        ConfigurationSection corner1 = section.getConfigurationSection("corner1");
        ConfigurationSection corner2 = section.getConfigurationSection("corner2");
        if (corner1 == null || corner2 == null
                || !corner1.isSet("x") || !corner1.isSet("y") || !corner1.isSet("z")
                || !corner2.isSet("x") || !corner2.isSet("y") || !corner2.isSet("z")) {
            warnings.accept("Portal '" + id + "' is missing 'corner1'/'corner2' x/y/z keys — skipping it.");
            return Optional.empty();
        }

        String world = section.getString("world");
        String server = section.getString("server");
        double x1 = corner1.getDouble("x");
        double y1 = corner1.getDouble("y");
        double z1 = corner1.getDouble("z");
        double x2 = corner2.getDouble("x");
        double y2 = corner2.getDouble("y");
        double z2 = corner2.getDouble("z");

        Long cooldownOverride = null;
        if (section.isSet("cooldown-seconds")) {
            long raw = section.getLong("cooldown-seconds");
            if (raw < 0) {
                warnings.accept("Portal '" + id + "' has a negative 'cooldown-seconds' (" + raw
                        + ") — falling back to the module-wide default instead.");
            } else {
                cooldownOverride = raw;
            }
        }

        return Optional.of(new PortalRegion(id, world, x1, y1, z1, x2, y2, z2, server, cooldownOverride));
    }
}
