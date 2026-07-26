package com.SwagDev.SwagHub.modules.spawn;

import com.SwagDev.SwagHub.SwagHub;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Owns the single stored lobby/spawn location. Per §5.1, only one global lobby
 * location is supported in v1 (the spec doesn't describe per-world lobbies — a
 * single stored location is correct DeluxeHub parity).
 *
 * <p>Backed by {@link SpawnLocationIO} for the actual YAML round trip; this class
 * adds the Bukkit-facing parts {@link SpawnLocationIO} deliberately does NOT have:
 * where the file lives under the plugin's data folder, logging, and lazy
 * {@link World} resolution.</p>
 *
 * <p><b>Persistence-correctness (§4):</b> {@link #set(Location)} writes to disk
 * synchronously and immediately — never deferred to an interval or to shutdown
 * only. {@code SpawnModule#onDisable()} additionally calls {@link #flush()} as a
 * defensive second write, per §4's exact wording ("immediately on change AND on
 * shutdown").</p>
 */
public class SpawnStore {

    private final SwagHub plugin;
    private final File file;

    private SpawnLocation location;

    public SpawnStore(SwagHub plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data" + File.separator + "spawn.yml");
    }

    /** Reads the stored location from disk, if any. Safe to call multiple times (e.g. on reload). */
    public void load() {
        try {
            location = SpawnLocationIO.load(file).orElse(null);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load data/spawn.yml — the lobby location will be treated as unset.", ex);
            location = null;
        }
    }

    /**
     * Stores {@code bukkitLocation} as the new lobby location and writes it to disk
     * immediately (synchronously) — this is the exact fix for the DeluxeHub
     * setlobby-restart bug (§4): the location is durable on disk before this method
     * returns, not just held in memory until the next shutdown/interval flush.
     */
    public void set(Location bukkitLocation) {
        World world = bukkitLocation.getWorld();
        this.location = new SpawnLocation(
                world != null ? world.getName() : "world",
                bukkitLocation.getX(),
                bukkitLocation.getY(),
                bukkitLocation.getZ(),
                bukkitLocation.getYaw(),
                bukkitLocation.getPitch());
        flush();
    }

    /**
     * Writes the current in-memory location to disk immediately, if one is set.
     * A no-op if nothing has ever been set. Called both by {@link #set(Location)}
     * (immediately on change) and by the owning module's {@code onDisable()}
     * (defensively, on shutdown) — see §4.
     */
    public void flush() {
        if (location == null) {
            return;
        }
        try {
            SpawnLocationIO.save(file, location);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to write data/spawn.yml — the lobby location may not survive a restart!", ex);
        }
    }

    public boolean isSet() {
        return location != null;
    }

    /**
     * Resolves the stored location against the live {@link World} registry. The
     * world is looked up by name lazily, here, and nowhere else in this class —
     * never cached across a world reload/unload. Empty if nothing is set yet, or
     * if the stored world isn't currently loaded.
     */
    public Optional<Location> getBukkitLocation() {
        if (location == null) {
            return Optional.empty();
        }
        World world = Bukkit.getWorld(location.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Lobby location's world '" + location.getWorldName()
                    + "' is not currently loaded — cannot teleport to it.");
            return Optional.empty();
        }
        return Optional.of(new Location(world, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()));
    }

    /** The raw pure-data record, if set — exposed for the web editor / diagnostics in a later build step. */
    public Optional<SpawnLocation> getRawLocation() {
        return Optional.ofNullable(location);
    }
}
