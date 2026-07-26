package com.SwagDev.SwagHub.modules.scoreboard;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ScoreboardConfigParser} — a plain {@link YamlConfiguration} in
 * memory, no live Bukkit server (see DECISIONS.md Step 5).
 */
class ScoreboardConfigParserTest {

    @Test
    void nullWorldsSectionReturnsAnEmptyMap() {
        Map<String, ScoreboardWorldConfig> result = ScoreboardConfigParser.parseWorlds(null, ignored -> { });
        assertTrue(result.isEmpty());
    }

    @Test
    void defaultKeyIsParsedLikeAnyOtherWorldEntry() {
        YamlConfiguration root = new YamlConfiguration();
        root.createSection("worlds.default.title").set("frames", List.of("<white>Title</white>"));
        root.set("worlds.default.lines", List.of("line1", "line2"));

        Map<String, ScoreboardWorldConfig> result = ScoreboardConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), ignored -> { });

        assertTrue(result.containsKey("default"));
        assertEquals(2, result.get("default").getLines().size());
        assertEquals(List.of("<white>Title</white>"), result.get("default").getTitle().getFrames());
    }

    @Test
    void singleFrameWithZeroIntervalIsStaticTitle() {
        YamlConfiguration root = new YamlConfiguration();
        root.createSection("worlds.hub.title").set("frames", List.of("<white>Static</white>"));
        root.getConfigurationSection("worlds.hub.title").set("frame-interval-ticks", 0);

        Map<String, ScoreboardWorldConfig> result = ScoreboardConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), ignored -> { });

        assertEquals("<white>Static</white>", result.get("hub").getTitle().currentFrame(1000L));
    }

    @Test
    void multiFrameTitleCyclesByFrameInterval() {
        YamlConfiguration root = new YamlConfiguration();
        var titleSection = root.createSection("worlds.hub.title");
        titleSection.set("frames", List.of("A", "B", "C"));
        titleSection.set("frame-interval-ticks", 20);

        Map<String, ScoreboardWorldConfig> result = ScoreboardConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), ignored -> { });

        var title = result.get("hub").getTitle();
        assertEquals("A", title.currentFrame(0L));
        assertEquals("B", title.currentFrame(20L));
        assertEquals("C", title.currentFrame(40L));
        assertEquals("A", title.currentFrame(60L));
    }

    @Test
    void negativeFrameIntervalIsClampedToZeroWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        var titleSection = root.createSection("worlds.hub.title");
        titleSection.set("frames", List.of("A"));
        titleSection.set("frame-interval-ticks", -50);
        List<String> warnings = new ArrayList<>();

        Map<String, ScoreboardWorldConfig> result = ScoreboardConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), warnings::add);

        assertEquals(0L, result.get("hub").getTitle().getFrameIntervalTicks());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void missingTitleSectionFallsBackToAnEmptyStaticFrameWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("worlds.hub.lines", List.of("only a line"));
        List<String> warnings = new ArrayList<>();

        Map<String, ScoreboardWorldConfig> result = ScoreboardConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), warnings::add);

        assertEquals("", result.get("hub").getTitle().currentFrame(0L));
        assertFalse(warnings.isEmpty());
    }

    @Test
    void linesBeyondTheSidebarLimitAreClampedWithAWarning() {
        YamlConfiguration root = new YamlConfiguration();
        List<String> manyLines = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            manyLines.add("line" + i);
        }
        root.set("worlds.hub.lines", manyLines);
        List<String> warnings = new ArrayList<>();

        Map<String, ScoreboardWorldConfig> result = ScoreboardConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), warnings::add);

        assertEquals(ScoreboardConfigParser.MAX_LINES, result.get("hub").getLines().size());
        assertEquals("line0", result.get("hub").getLines().get(0));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("15")));
    }

    @Test
    void worldWithNoConfigurationBodyIsSkippedButOtherWorldsStillLoad() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("worlds.broken", "not-a-section");
        root.set("worlds.hub.lines", List.of("a line"));
        List<String> warnings = new ArrayList<>();

        Map<String, ScoreboardWorldConfig> result = ScoreboardConfigParser.parseWorlds(
                root.getConfigurationSection("worlds"), warnings::add);

        assertFalse(result.containsKey("broken"));
        assertTrue(result.containsKey("hub"));
        assertFalse(warnings.isEmpty());
    }
}
