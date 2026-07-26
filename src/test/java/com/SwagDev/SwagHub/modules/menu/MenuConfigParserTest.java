package com.SwagDev.SwagHub.modules.menu;

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
 * Unit tests for {@link MenuConfigParser} — a plain {@link YamlConfiguration} in
 * memory, no live Bukkit server (see DECISIONS.md Step 4). Covers the "id from
 * filename vs. explicit id:" fallback, rows clamping, and — per §10.4 — that a
 * malformed slot entry is skipped without aborting the rest of the menu.
 */
class MenuConfigParserTest {

    @Test
    void nullRootIsSkippedWithAWarning() {
        List<String> warnings = new ArrayList<>();
        Optional<MenuConfig> result = MenuConfigParser.parse(null, "main-menu", warnings::add);

        assertTrue(result.isEmpty());
        assertEquals(1, warnings.size());
    }

    @Test
    void idFallsBackToTheFilenameWhenNoIdKeyIsPresent() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("title", "<white>Menu</white>");

        Optional<MenuConfig> result = MenuConfigParser.parse(root, "main-menu", ignored -> { });

        assertEquals("main-menu", result.get().getId());
    }

    @Test
    void explicitIdKeyOverridesTheFilenameFallback() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("id", "custom-id");

        Optional<MenuConfig> result = MenuConfigParser.parse(root, "filename-differs", ignored -> { });

        assertEquals("custom-id", result.get().getId());
    }

    @Test
    void rowsBelowOneAreClampedToOneWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("rows", 0);
        List<String> warnings = new ArrayList<>();

        Optional<MenuConfig> result = MenuConfigParser.parse(root, "menu", warnings::add);

        assertEquals(1, result.get().getRows());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void rowsAboveSixAreClampedToSixWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("rows", 12);
        List<String> warnings = new ArrayList<>();

        Optional<MenuConfig> result = MenuConfigParser.parse(root, "menu", warnings::add);

        assertEquals(6, result.get().getRows());
        assertEquals(54, result.get().getSize());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void missingTitleFallsBackToADefault() {
        YamlConfiguration root = new YamlConfiguration();

        Optional<MenuConfig> result = MenuConfigParser.parse(root, "menu", ignored -> { });

        assertEquals("<white>Menu</white>", result.get().getTitle());
    }

    @Test
    void fillerSectionIsParsedIntoAnItemConfig() {
        YamlConfiguration root = new YamlConfiguration();
        root.createSection("filler").set("material", "GRAY_STAINED_GLASS_PANE");

        Optional<MenuConfig> result = MenuConfigParser.parse(root, "menu", ignored -> { });

        assertEquals("GRAY_STAINED_GLASS_PANE", result.get().getFiller().getMaterialName());
    }

    @Test
    void noFillerSectionMeansNullFiller() {
        YamlConfiguration root = new YamlConfiguration();

        Optional<MenuConfig> result = MenuConfigParser.parse(root, "menu", ignored -> { });

        assertNull(result.get().getFiller());
    }

    @Test
    void validSlotsAreParsedByNumericKey() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("rows", 3);
        root.createSection("slots.11").set("material", "GRASS_BLOCK");
        root.createSection("slots.13").set("material", "NETHER_STAR");

        Optional<MenuConfig> result = MenuConfigParser.parse(root, "menu", ignored -> { });

        assertEquals(2, result.get().getSlots().size());
        assertEquals("GRASS_BLOCK", result.get().getSlots().get(11).getMaterialName());
        assertEquals("NETHER_STAR", result.get().getSlots().get(13).getMaterialName());
    }

    @Test
    void nonNumericSlotKeyIsSkippedButOtherSlotsStillLoad() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("rows", 3);
        root.createSection("slots.not-a-number").set("material", "STONE");
        root.createSection("slots.11").set("material", "GRASS_BLOCK");
        List<String> warnings = new ArrayList<>();

        Optional<MenuConfig> result = MenuConfigParser.parse(root, "menu", warnings::add);

        assertEquals(1, result.get().getSlots().size());
        assertTrue(result.get().getSlots().containsKey(11));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("not-a-number")));
    }

    @Test
    void outOfRangeSlotIsSkippedButOtherSlotsStillLoad() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("rows", 1); // valid range: 0-8
        root.createSection("slots.20").set("material", "STONE"); // out of range for 1 row
        root.createSection("slots.4").set("material", "GRASS_BLOCK");
        List<String> warnings = new ArrayList<>();

        Optional<MenuConfig> result = MenuConfigParser.parse(root, "menu", warnings::add);

        assertEquals(1, result.get().getSlots().size());
        assertTrue(result.get().getSlots().containsKey(4));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("out-of-range") || w.contains("20")));
    }

    @Test
    void malformedSlotItemBodyIsSkippedButOtherSlotsStillLoad() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("rows", 3);
        root.createSection("slots.11"); // no "material" key at all — malformed
        root.createSection("slots.13").set("material", "NETHER_STAR");
        List<String> warnings = new ArrayList<>();

        Optional<MenuConfig> result = MenuConfigParser.parse(root, "menu", warnings::add);

        assertEquals(1, result.get().getSlots().size());
        assertTrue(result.get().getSlots().containsKey(13));
        assertFalse(warnings.isEmpty());
    }

    @Test
    void negativeRefreshIntervalIsClampedToZero() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("refresh-interval-ticks", -50);

        Optional<MenuConfig> result = MenuConfigParser.parse(root, "menu", ignored -> { });

        assertEquals(0L, result.get().getRefreshIntervalTicks());
    }

    @Test
    void openSoundIsParsedWhenPresentAndNullWhenAbsent() {
        YamlConfiguration withSound = new YamlConfiguration();
        withSound.set("open-sound", "BLOCK_NOTE_BLOCK_PLING");
        assertEquals("BLOCK_NOTE_BLOCK_PLING", MenuConfigParser.parse(withSound, "menu", ignored -> { }).get().getOpenSound());

        YamlConfiguration withoutSound = new YamlConfiguration();
        assertNull(MenuConfigParser.parse(withoutSound, "menu", ignored -> { }).get().getOpenSound());
    }
}
