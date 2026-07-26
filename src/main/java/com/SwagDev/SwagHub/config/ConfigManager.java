package com.SwagDev.SwagHub.config;

import com.SwagDev.SwagHub.SwagHub;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Thin wrapper around config.yml load/reload/save.
 *
 * <p><b>Step 1 decision:</b> uses Paper's built-in {@link FileConfiguration}
 * (YamlConfiguration) rather than boostedyaml. This class's public surface
 * ({@link #load()}, {@link #getConfig()}, {@link #reload()}, {@link #save()}) is
 * deliberately the only thing the rest of the plugin touches, so swapping the
 * backing implementation to boostedyaml later (for comment-preserving reloads and
 * auto-updating configs, per the design doc) is an internal change to this one
 * class — see DECISIONS.md.</p>
 */
public class ConfigManager {

    private final SwagHub plugin;

    public ConfigManager(SwagHub plugin) {
        this.plugin = plugin;
    }

    /** Creates config.yml from the bundled default on first run, then loads it. */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        plugin.reloadConfig();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    /** Re-reads config.yml from disk. Does not itself notify any manager/module — callers do that. */
    public void reload() {
        plugin.reloadConfig();
        plugin.getConfig().options().copyDefaults(true);
    }

    public void save() {
        plugin.saveConfig();
    }
}
