package com.SwagDev.SwagHub.modules.hologram;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HologramConfigParser} — a plain {@link YamlConfiguration} in
 * memory, no live Bukkit server (mirrors {@code LaunchpadConfigParserTest}'s exact
 * shape, see DECISIONS.md Step 7).
 */
class HologramConfigParserTest {

    @Test
    void nullSectionIsSkippedWithAWarning() {
        List<String> warnings = new ArrayList<>();
        Optional<HologramConfig> result = HologramConfigParser.parse(null, "sign", warnings::add);
        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void missingWorldIsSkippedWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("sign.x", 1);
        root.set("sign.y", 2);
        root.set("sign.z", 3);
        root.set("sign.lines", List.of("hello"));
        List<String> warnings = new ArrayList<>();

        Optional<HologramConfig> result = HologramConfigParser.parse(
                root.getConfigurationSection("sign"), "sign", warnings::add);

        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void missingCoordinateIsSkippedWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("sign.world", "world");
        root.set("sign.x", 1);
        root.set("sign.y", 2);
        // z missing
        root.set("sign.lines", List.of("hello"));
        List<String> warnings = new ArrayList<>();

        Optional<HologramConfig> result = HologramConfigParser.parse(
                root.getConfigurationSection("sign"), "sign", warnings::add);

        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void emptyLinesIsSkippedWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("sign.world", "world");
        root.set("sign.x", 0);
        root.set("sign.y", 0);
        root.set("sign.z", 0);
        List<String> warnings = new ArrayList<>();

        Optional<HologramConfig> result = HologramConfigParser.parse(
                root.getConfigurationSection("sign"), "sign", warnings::add);

        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void validEntryParsesWithNullRefreshOverride() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("sign.world", "world");
        root.set("sign.x", 10.5);
        root.set("sign.y", 65.0);
        root.set("sign.z", -5.25);
        root.set("sign.lines", List.of("Line 1", "Line 2"));
        List<String> warnings = new ArrayList<>();

        Optional<HologramConfig> result = HologramConfigParser.parse(
                root.getConfigurationSection("sign"), "sign", warnings::add);

        assertTrue(result.isPresent());
        assertEquals("sign", result.get().getId());
        assertEquals("world", result.get().getWorldName());
        assertEquals(10.5, result.get().getX());
        assertEquals(65.0, result.get().getY());
        assertEquals(-5.25, result.get().getZ());
        assertEquals(List.of("Line 1", "Line 2"), result.get().getLines());
        assertNull(result.get().getRefreshIntervalTicks());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void positiveRefreshOverrideIsParsed() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("sign.world", "world");
        root.set("sign.x", 0);
        root.set("sign.y", 0);
        root.set("sign.z", 0);
        root.set("sign.lines", List.of("hello"));
        root.set("sign.refresh-interval-ticks", 40);
        List<String> warnings = new ArrayList<>();

        Optional<HologramConfig> result = HologramConfigParser.parse(
                root.getConfigurationSection("sign"), "sign", warnings::add);

        assertTrue(result.isPresent());
        assertEquals(40L, result.get().getRefreshIntervalTicks());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void nonPositiveRefreshOverrideFallsBackToNullWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("sign.world", "world");
        root.set("sign.x", 0);
        root.set("sign.y", 0);
        root.set("sign.z", 0);
        root.set("sign.lines", List.of("hello"));
        root.set("sign.refresh-interval-ticks", -5);
        List<String> warnings = new ArrayList<>();

        Optional<HologramConfig> result = HologramConfigParser.parse(
                root.getConfigurationSection("sign"), "sign", warnings::add);

        assertTrue(result.isPresent());
        assertNull(result.get().getRefreshIntervalTicks());
        assertFalse(warnings.isEmpty());
    }
}
