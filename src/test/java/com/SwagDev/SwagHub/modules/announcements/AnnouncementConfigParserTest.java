package com.SwagDev.SwagHub.modules.announcements;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AnnouncementConfigParser} — a plain {@link YamlConfiguration}
 * in memory, no live Bukkit server (see DECISIONS.md Step 5).
 */
class AnnouncementConfigParserTest {

    @Test
    void nullWorldsSectionReturnsAnEmptyMap() {
        assertTrue(AnnouncementConfigParser.parseWorlds(null, ignored -> { }).isEmpty());
    }

    @Test
    void defaultRotationIsSequentialWhenOmitted() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("worlds.default.entries", List.of(
                Map.of("actions", List.of("[message] hi"))
        ));

        Map<String, AnnouncementWorldConfig> result = AnnouncementConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), ignored -> { });

        assertEquals(Rotation.SEQUENTIAL, result.get("default").getRotation());
    }

    @Test
    void explicitRandomRotationIsParsedCaseInsensitively() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("worlds.hub.rotation", "RaNdOm");
        root.set("worlds.hub.entries", List.of(Map.of("actions", List.of("[message] hi"))));

        Map<String, AnnouncementWorldConfig> result = AnnouncementConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), ignored -> { });

        assertEquals(Rotation.RANDOM, result.get("hub").getRotation());
    }

    @Test
    void unrecognizedRotationFallsBackToSequentialWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("worlds.hub.rotation", "shuffle-ish");
        root.set("worlds.hub.entries", List.of(Map.of("actions", List.of("[message] hi"))));
        List<String> warnings = new ArrayList<>();

        Map<String, AnnouncementWorldConfig> result = AnnouncementConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), warnings::add);

        assertEquals(Rotation.SEQUENTIAL, result.get("hub").getRotation());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void intervalTicksOverrideIsParsedWhenPositive() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("worlds.hub.interval-ticks", 1200);
        root.set("worlds.hub.entries", List.of(Map.of("actions", List.of("[message] hi"))));

        Map<String, AnnouncementWorldConfig> result = AnnouncementConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), ignored -> { });

        assertEquals(1200L, result.get("hub").getIntervalTicksOverride());
    }

    @Test
    void absentIntervalTicksIsNullMeaningUseTheDefault() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("worlds.hub.entries", List.of(Map.of("actions", List.of("[message] hi"))));

        Map<String, AnnouncementWorldConfig> result = AnnouncementConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), ignored -> { });

        assertNull(result.get("hub").getIntervalTicksOverride());
    }

    @Test
    void nonPositiveIntervalTicksIsIgnoredWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("worlds.hub.interval-ticks", -5);
        root.set("worlds.hub.entries", List.of(Map.of("actions", List.of("[message] hi"))));
        List<String> warnings = new ArrayList<>();

        Map<String, AnnouncementWorldConfig> result = AnnouncementConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), warnings::add);

        assertNull(result.get("hub").getIntervalTicksOverride());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void multiActionEntryIsParsedAsOneEntryWithAllActions() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("worlds.hub.entries", List.of(
                Map.of("actions", List.of("[actionbar] hi", "[sound] ENTITY_EXPERIENCE_ORB_PICKUP"))
        ));

        Map<String, AnnouncementWorldConfig> result = AnnouncementConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), ignored -> { });

        assertEquals(1, result.get("hub").getEntries().size());
        assertEquals(2, result.get("hub").getEntries().get(0).getActions().size());
    }

    @Test
    void entryWithNoActionsListIsSkippedButOtherEntriesStillLoad() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("worlds.hub.entries", List.of(
                Map.of("not-actions", "oops"),
                Map.of("actions", List.of("[message] valid"))
        ));
        List<String> warnings = new ArrayList<>();

        Map<String, AnnouncementWorldConfig> result = AnnouncementConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), warnings::add);

        assertEquals(1, result.get("hub").getEntries().size());
        assertEquals(List.of("[message] valid"), result.get("hub").getEntries().get(0).getActions());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void worldWithNoEntriesConfiguredHasAnEmptyEntryList() {
        YamlConfiguration root = new YamlConfiguration();
        root.createSection("worlds.hub");

        Map<String, AnnouncementWorldConfig> result = AnnouncementConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), ignored -> { });

        assertTrue(result.get("hub").getEntries().isEmpty());
    }

    @Test
    void worldWithNoConfigurationBodyIsSkippedButOtherWorldsStillLoad() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("worlds.broken", "not-a-section");
        root.set("worlds.hub.entries", List.of(Map.of("actions", List.of("[message] hi"))));
        List<String> warnings = new ArrayList<>();

        Map<String, AnnouncementWorldConfig> result = AnnouncementConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), warnings::add);

        assertFalse(result.containsKey("broken"));
        assertTrue(result.containsKey("hub"));
        assertFalse(warnings.isEmpty());
    }
}
