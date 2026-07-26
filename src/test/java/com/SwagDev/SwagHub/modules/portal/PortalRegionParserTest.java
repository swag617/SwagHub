package com.SwagDev.SwagHub.modules.portal;

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
 * Unit tests for {@link PortalRegionParser} — a plain {@link YamlConfiguration} in
 * memory, no live Bukkit server (mirrors {@code HologramConfigParserTest}/
 * {@code LaunchpadConfigParserTest}'s exact shape, see DECISIONS.md Step 7).
 */
class PortalRegionParserTest {

    @Test
    void nullSectionIsSkippedWithAWarning() {
        List<String> warnings = new ArrayList<>();
        Optional<PortalRegion> result = PortalRegionParser.parse(null, "gate", warnings::add);
        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void missingWorldIsSkippedWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("gate.server", "survival");
        root.set("gate.corner1.x", 0);
        root.set("gate.corner1.y", 0);
        root.set("gate.corner1.z", 0);
        root.set("gate.corner2.x", 1);
        root.set("gate.corner2.y", 1);
        root.set("gate.corner2.z", 1);
        List<String> warnings = new ArrayList<>();

        Optional<PortalRegion> result = PortalRegionParser.parse(
                root.getConfigurationSection("gate"), "gate", warnings::add);

        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void missingServerIsSkippedWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("gate.world", "world");
        root.set("gate.corner1.x", 0);
        root.set("gate.corner1.y", 0);
        root.set("gate.corner1.z", 0);
        root.set("gate.corner2.x", 1);
        root.set("gate.corner2.y", 1);
        root.set("gate.corner2.z", 1);
        List<String> warnings = new ArrayList<>();

        Optional<PortalRegion> result = PortalRegionParser.parse(
                root.getConfigurationSection("gate"), "gate", warnings::add);

        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void missingCornerIsSkippedWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("gate.world", "world");
        root.set("gate.server", "survival");
        root.set("gate.corner1.x", 0);
        root.set("gate.corner1.y", 0);
        root.set("gate.corner1.z", 0);
        // corner2 missing entirely
        List<String> warnings = new ArrayList<>();

        Optional<PortalRegion> result = PortalRegionParser.parse(
                root.getConfigurationSection("gate"), "gate", warnings::add);

        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void validEntryParsesAndNormalizesCornersToMinMax() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("gate.world", "world");
        root.set("gate.server", "survival");
        root.set("gate.corner1.x", 10);
        root.set("gate.corner1.y", 65);
        root.set("gate.corner1.z", 10);
        root.set("gate.corner2.x", 5);
        root.set("gate.corner2.y", 60);
        root.set("gate.corner2.z", 5);
        List<String> warnings = new ArrayList<>();

        Optional<PortalRegion> result = PortalRegionParser.parse(
                root.getConfigurationSection("gate"), "gate", warnings::add);

        assertTrue(result.isPresent());
        PortalRegion region = result.get();
        assertEquals("gate", region.getId());
        assertEquals("world", region.getWorldName());
        assertEquals("survival", region.getServerName());
        assertEquals(5.0, region.getMinX());
        assertEquals(60.0, region.getMinY());
        assertEquals(5.0, region.getMinZ());
        assertEquals(10.0, region.getMaxX());
        assertEquals(65.0, region.getMaxY());
        assertEquals(10.0, region.getMaxZ());
        assertNull(region.getCooldownSecondsOverride());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void positiveCooldownOverrideIsParsed() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("gate.world", "world");
        root.set("gate.server", "survival");
        root.set("gate.corner1.x", 0);
        root.set("gate.corner1.y", 0);
        root.set("gate.corner1.z", 0);
        root.set("gate.corner2.x", 1);
        root.set("gate.corner2.y", 1);
        root.set("gate.corner2.z", 1);
        root.set("gate.cooldown-seconds", 10);
        List<String> warnings = new ArrayList<>();

        Optional<PortalRegion> result = PortalRegionParser.parse(
                root.getConfigurationSection("gate"), "gate", warnings::add);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getCooldownSecondsOverride());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void negativeCooldownOverrideFallsBackToNullWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("gate.world", "world");
        root.set("gate.server", "survival");
        root.set("gate.corner1.x", 0);
        root.set("gate.corner1.y", 0);
        root.set("gate.corner1.z", 0);
        root.set("gate.corner2.x", 1);
        root.set("gate.corner2.y", 1);
        root.set("gate.corner2.z", 1);
        root.set("gate.cooldown-seconds", -1);
        List<String> warnings = new ArrayList<>();

        Optional<PortalRegion> result = PortalRegionParser.parse(
                root.getConfigurationSection("gate"), "gate", warnings::add);

        assertTrue(result.isPresent());
        assertNull(result.get().getCooldownSecondsOverride());
        assertFalse(warnings.isEmpty());
    }
}
