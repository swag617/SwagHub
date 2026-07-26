package com.SwagDev.SwagHub.modules.spawn;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Plain YAML load/save for a single {@link SpawnLocation}, with NO dependency on a
 * running {@code Plugin}/{@code Server}/{@code World} instance — only {@link File}
 * and {@link YamlConfiguration} (which only parses/writes YAML text; it never
 * itself touches the live Bukkit server).
 *
 * <p>This is exactly the persistence bug class §4/§11 build step 2 is about
 * ("<code>/setlobby</code> doesn't persist across restarts") and is why this class
 * is split out from {@link SpawnStore}: it is unit-testable in plain JUnit with a
 * temp file and no server bootstrap — see {@code SpawnLocationIOTest}.</p>
 *
 * <p>Package-private on purpose — {@link SpawnStore} is the only public-facing
 * entry point the rest of the plugin should use; this class is the tested
 * implementation detail underneath it.</p>
 */
final class SpawnLocationIO {

    private static final String KEY_WORLD = "world";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_YAW = "yaw";
    private static final String KEY_PITCH = "pitch";

    private SpawnLocationIO() {
    }

    /**
     * @return the stored location, or {@link Optional#empty()} if the file doesn't
     * exist yet or has no location saved in it (e.g. a fresh install before the
     * first {@code /setlobby}).
     */
    static Optional<SpawnLocation> load(File file) {
        if (!file.exists()) {
            return Optional.empty();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String world = yaml.getString(KEY_WORLD);
        if (world == null || world.isBlank()) {
            return Optional.empty();
        }
        double x = yaml.getDouble(KEY_X);
        double y = yaml.getDouble(KEY_Y);
        double z = yaml.getDouble(KEY_Z);
        float yaw = (float) yaml.getDouble(KEY_YAW);
        float pitch = (float) yaml.getDouble(KEY_PITCH);
        return Optional.of(new SpawnLocation(world, x, y, z, yaw, pitch));
    }

    /**
     * Writes {@code location} to {@code file} synchronously (creating parent
     * directories as needed) and returns only once the write has completed —
     * callers that need "immediately on change" persistence (§4) must call this
     * directly on the calling thread, not hand it off to an async task.
     */
    static void save(File file, SpawnLocation location) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create parent directory: " + parent);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(KEY_WORLD, location.getWorldName());
        yaml.set(KEY_X, location.getX());
        yaml.set(KEY_Y, location.getY());
        yaml.set(KEY_Z, location.getZ());
        yaml.set(KEY_YAW, (double) location.getYaw());
        yaml.set(KEY_PITCH, (double) location.getPitch());
        yaml.save(file);
    }
}
