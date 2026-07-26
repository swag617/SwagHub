package com.SwagDev.SwagHub.modules.launchpad;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LaunchpadConfigParser} — a plain {@link YamlConfiguration} in
 * memory, no live Bukkit server (see DECISIONS.md Step 6).
 */
class LaunchpadConfigParserTest {

    @Test
    void nullSectionIsSkippedWithAWarning() {
        List<String> warnings = new ArrayList<>();
        Optional<LaunchpadConfig> result = LaunchpadConfigParser.parse(null, "pad", warnings::add);
        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void missingWorldIsSkippedWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("pad.x", 1);
        root.set("pad.y", 2);
        root.set("pad.z", 3);
        List<String> warnings = new ArrayList<>();

        Optional<LaunchpadConfig> result = LaunchpadConfigParser.parse(
                root.getConfigurationSection("pad"), "pad", warnings::add);

        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void missingCoordinateIsSkippedWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("pad.world", "world");
        root.set("pad.x", 1);
        root.set("pad.y", 2);
        // z missing
        List<String> warnings = new ArrayList<>();

        Optional<LaunchpadConfig> result = LaunchpadConfigParser.parse(
                root.getConfigurationSection("pad"), "pad", warnings::add);

        assertTrue(result.isEmpty());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void validEntryParsesWithDefaults() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("pad.world", "world");
        root.set("pad.x", 10);
        root.set("pad.y", 65);
        root.set("pad.z", -5);
        List<String> warnings = new ArrayList<>();

        Optional<LaunchpadConfig> result = LaunchpadConfigParser.parse(
                root.getConfigurationSection("pad"), "pad", warnings::add);

        assertTrue(result.isPresent());
        assertEquals("world", result.get().getWorldName());
        assertEquals(10, result.get().getX());
        assertEquals(65, result.get().getY());
        assertEquals(-5, result.get().getZ());
        assertEquals(1.6, result.get().getPower());
        assertEquals(1.4, result.get().getHeight());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void nonPositivePowerFallsBackToDefaultWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("pad.world", "world");
        root.set("pad.x", 0);
        root.set("pad.y", 0);
        root.set("pad.z", 0);
        root.set("pad.power", -5.0);
        List<String> warnings = new ArrayList<>();

        Optional<LaunchpadConfig> result = LaunchpadConfigParser.parse(
                root.getConfigurationSection("pad"), "pad", warnings::add);

        assertTrue(result.isPresent());
        assertEquals(1.6, result.get().getPower());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void nonPositiveHeightFallsBackToDefaultWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("pad.world", "world");
        root.set("pad.x", 0);
        root.set("pad.y", 0);
        root.set("pad.z", 0);
        root.set("pad.height", 0.0);
        List<String> warnings = new ArrayList<>();

        Optional<LaunchpadConfig> result = LaunchpadConfigParser.parse(
                root.getConfigurationSection("pad"), "pad", warnings::add);

        assertTrue(result.isPresent());
        assertEquals(1.4, result.get().getHeight());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void optionalParticleAndSoundDefaultToNull() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("pad.world", "world");
        root.set("pad.x", 0);
        root.set("pad.y", 0);
        root.set("pad.z", 0);
        List<String> warnings = new ArrayList<>();

        Optional<LaunchpadConfig> result = LaunchpadConfigParser.parse(
                root.getConfigurationSection("pad"), "pad", warnings::add);

        assertTrue(result.isPresent());
        assertEquals(null, result.get().getParticleName());
        assertEquals(null, result.get().getSoundName());
    }

    @Test
    void particleAndSoundArePassedThroughVerbatim() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("pad.world", "world");
        root.set("pad.x", 0);
        root.set("pad.y", 0);
        root.set("pad.z", 0);
        root.set("pad.particle", "CLOUD");
        root.set("pad.sound", "ENTITY_SLIME_JUMP");
        List<String> warnings = new ArrayList<>();

        Optional<LaunchpadConfig> result = LaunchpadConfigParser.parse(
                root.getConfigurationSection("pad"), "pad", warnings::add);

        assertTrue(result.isPresent());
        assertEquals("CLOUD", result.get().getParticleName());
        assertEquals("ENTITY_SLIME_JUMP", result.get().getSoundName());
    }
}
