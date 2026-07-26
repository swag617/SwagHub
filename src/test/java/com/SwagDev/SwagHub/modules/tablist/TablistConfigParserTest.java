package com.SwagDev.SwagHub.modules.tablist;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TablistConfigParser} — a plain {@link YamlConfiguration} in
 * memory, no live Bukkit server (see DECISIONS.md Step 5).
 */
class TablistConfigParserTest {

    @Test
    void nullWorldsSectionReturnsAnEmptyMap() {
        assertTrue(TablistConfigParser.parseWorlds(null, ignored -> { }).isEmpty());
    }

    @Test
    void defaultKeyIsParsedWithHeaderAndFooter() {
        YamlConfiguration root = new YamlConfiguration();
        root.createSection("worlds.default.header").set("frames", List.of("<white>Header</white>"));
        root.createSection("worlds.default.footer").set("frames", List.of("<white>Footer</white>"));

        Map<String, TablistWorldConfig> result = TablistConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), ignored -> { });

        assertEquals("<white>Header</white>", result.get("default").getHeader().currentFrame(0L));
        assertEquals("<white>Footer</white>", result.get("default").getFooter().currentFrame(0L));
    }

    @Test
    void headerAndFooterAnimateIndependently() {
        YamlConfiguration root = new YamlConfiguration();
        var header = root.createSection("worlds.hub.header");
        header.set("frames", List.of("H1", "H2"));
        header.set("frame-interval-ticks", 10);
        var footer = root.createSection("worlds.hub.footer");
        footer.set("frames", List.of("F1", "F2", "F3"));
        footer.set("frame-interval-ticks", 5);

        Map<String, TablistWorldConfig> result = TablistConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), ignored -> { });

        assertEquals("H1", result.get("hub").getHeader().currentFrame(0L));
        assertEquals("H2", result.get("hub").getHeader().currentFrame(10L));
        assertEquals("F1", result.get("hub").getFooter().currentFrame(0L));
        assertEquals("F2", result.get("hub").getFooter().currentFrame(5L));
        assertEquals("F3", result.get("hub").getFooter().currentFrame(10L));
    }

    @Test
    void missingHeaderOrFooterSectionFallsBackToEmptyStaticTextWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.createSection("worlds.hub.footer").set("frames", List.of("<white>Footer only</white>"));
        List<String> warnings = new ArrayList<>();

        Map<String, TablistWorldConfig> result = TablistConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), warnings::add);

        assertEquals("", result.get("hub").getHeader().currentFrame(0L));
        assertEquals("<white>Footer only</white>", result.get("hub").getFooter().currentFrame(0L));
        assertFalse(warnings.isEmpty());
    }

    @Test
    void worldWithNoConfigurationBodyIsSkippedButOtherWorldsStillLoad() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("worlds.broken", "not-a-section");
        root.createSection("worlds.hub.header").set("frames", List.of("ok"));
        List<String> warnings = new ArrayList<>();

        Map<String, TablistWorldConfig> result = TablistConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), warnings::add);

        assertFalse(result.containsKey("broken"));
        assertTrue(result.containsKey("hub"));
        assertFalse(warnings.isEmpty());
    }
}
