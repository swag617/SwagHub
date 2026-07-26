package com.SwagDev.SwagHub.modules.hologram;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link HologramConfigIO}'s save-then-load round trip — mirrors
 * {@code SpawnLocationIOTest}'s exact shape (plain {@link File} + real temp files, no
 * live Bukkit server), plus the "other top-level keys survive a save" contract that's
 * unique to this class (see its own javadoc).
 */
class HologramConfigIOTest {

    @TempDir
    Path tempDir;

    private File file;

    @BeforeEach
    void setUp() {
        file = new File(tempDir.toFile(), "holograms.yml");
    }

    @Test
    void loadAllReturnsEmptyMapWhenFileDoesNotExist() {
        Map<String, HologramConfig> loaded = HologramConfigIO.loadAll(file, msg -> { });
        assertTrue(loaded.isEmpty());
    }

    @Test
    void saveThenLoadRoundTripsEveryField() throws IOException {
        Map<String, HologramConfig> holograms = new LinkedHashMap<>();
        holograms.put("sign", new HologramConfig("sign", "world", 1.5, 65.0, -3.25,
                List.of("Line 1", "Line 2"), 40L));

        HologramConfigIO.saveAll(file, holograms);
        assertTrue(file.exists(), "saveAll() must create the file, including parent directories");

        List<String> warnings = new ArrayList<>();
        Map<String, HologramConfig> reloaded = HologramConfigIO.loadAll(file, warnings::add);

        assertTrue(warnings.isEmpty());
        assertEquals(1, reloaded.size());
        HologramConfig reloadedConfig = reloaded.get("sign");
        assertEquals("sign", reloadedConfig.getId());
        assertEquals("world", reloadedConfig.getWorldName());
        assertEquals(1.5, reloadedConfig.getX());
        assertEquals(65.0, reloadedConfig.getY());
        assertEquals(-3.25, reloadedConfig.getZ());
        assertEquals(List.of("Line 1", "Line 2"), reloadedConfig.getLines());
        assertEquals(40L, reloadedConfig.getRefreshIntervalTicks());
    }

    @Test
    void saveAllPreservesOtherTopLevelKeysAlreadyInTheFile() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("refresh-interval-ticks", 100);
        yaml.set("line-spacing", 0.28);
        yaml.save(file);

        Map<String, HologramConfig> holograms = new LinkedHashMap<>();
        holograms.put("sign", new HologramConfig("sign", "world", 0, 0, 0, List.of("hi"), null));
        HologramConfigIO.saveAll(file, holograms);

        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(file);
        assertEquals(100, reloaded.getInt("refresh-interval-ticks"));
        assertEquals(0.28, reloaded.getDouble("line-spacing"));
    }

    @Test
    void saveAllClearsRenamedOrRemovedIds() throws IOException {
        Map<String, HologramConfig> first = new LinkedHashMap<>();
        first.put("old-sign", new HologramConfig("old-sign", "world", 0, 0, 0, List.of("hi"), null));
        HologramConfigIO.saveAll(file, first);

        Map<String, HologramConfig> second = new LinkedHashMap<>();
        second.put("new-sign", new HologramConfig("new-sign", "world", 0, 0, 0, List.of("hi"), null));
        HologramConfigIO.saveAll(file, second);

        Map<String, HologramConfig> reloaded = HologramConfigIO.loadAll(file, msg -> { });
        assertEquals(1, reloaded.size());
        assertTrue(reloaded.containsKey("new-sign"));
    }
}
