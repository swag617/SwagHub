package com.SwagDev.SwagHub.modules.spawn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the exact bug class §4/§11 build step 2 is about: "/setlobby
 * doesn't persist across restarts". Exercises {@link SpawnLocationIO}'s
 * save-then-load round trip using ONLY plain data (a world-name string + numeric
 * fields) — no live Bukkit {@code World}/{@code Location} object is ever
 * constructed here, which is exactly what makes this testable without a running
 * Paper server.
 *
 * <p>A JVM restart is simulated by writing to a real file on disk and reading it
 * back with a brand-new {@link org.bukkit.configuration.file.YamlConfiguration}
 * parse — {@link SpawnLocationIO} never caches anything itself, so re-reading the
 * file from a fresh {@code load()} call with no shared in-memory state is a
 * faithful stand-in for "the server process restarted".</p>
 */
class SpawnLocationIOTest {

    @TempDir
    Path tempDir;

    private File file;

    @BeforeEach
    void setUp() {
        file = new File(tempDir.toFile(), "data/spawn.yml");
    }

    @Test
    void loadReturnsEmptyWhenFileDoesNotExist() {
        Optional<SpawnLocation> loaded = SpawnLocationIO.load(file);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void saveThenLoadRoundTripsEveryField() throws IOException {
        SpawnLocation original = new SpawnLocation("hub_world", 123.5, 64.0, -78.25, 179.9f, -45.5f);

        SpawnLocationIO.save(file, original);
        assertTrue(file.exists(), "save() must create the file, including parent directories");

        Optional<SpawnLocation> reloaded = SpawnLocationIO.load(file);
        assertTrue(reloaded.isPresent());
        assertEquals(original, reloaded.get());
    }

    @Test
    void savedFileSurvivesBeingReparsedFromScratchSimulatingAJvmRestart() throws IOException {
        SpawnLocation original = new SpawnLocation("world_the_end", -1000.0, 0.0, 1000.0, 0f, 90f);
        SpawnLocationIO.save(file, original);

        Optional<SpawnLocation> afterRestart = SpawnLocationIO.load(file);
        assertTrue(afterRestart.isPresent());
        assertEquals(original.getWorldName(), afterRestart.get().getWorldName());
        assertEquals(original.getX(), afterRestart.get().getX(), 0.0001);
        assertEquals(original.getY(), afterRestart.get().getY(), 0.0001);
        assertEquals(original.getZ(), afterRestart.get().getZ(), 0.0001);
        assertEquals(original.getYaw(), afterRestart.get().getYaw(), 0.001f);
        assertEquals(original.getPitch(), afterRestart.get().getPitch(), 0.001f);
    }

    @Test
    void saveOverwritesPreviousLocation() throws IOException {
        SpawnLocationIO.save(file, new SpawnLocation("world", 0, 0, 0, 0, 0));
        SpawnLocationIO.save(file, new SpawnLocation("world_nether", 10, 20, 30, 45f, 10f));

        Optional<SpawnLocation> loaded = SpawnLocationIO.load(file);
        assertTrue(loaded.isPresent());
        assertEquals("world_nether", loaded.get().getWorldName());
        assertEquals(10.0, loaded.get().getX());
    }
}
