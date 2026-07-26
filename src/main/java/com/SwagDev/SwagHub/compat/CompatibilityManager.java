package com.SwagDev.SwagHub.compat;

import com.SwagDev.SwagHub.SwagHub;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Detects overlapping plugins and decides which SwagHub modules should "yield" to
 * them (§6.3). Runs before {@link com.SwagDev.SwagHub.module.ModuleManager} enables
 * anything, and is re-consulted on every {@code /ah reload}.
 *
 * <p>This class has no real {@link com.SwagDev.SwagHub.module.Module}s to gate yet in
 * step 1 (those arrive in later build steps) — it is a complete, correct, standalone
 * decision engine so later steps just call {@link #shouldEnable(String, boolean)} with
 * their module's config key.</p>
 */
public class CompatibilityManager {

    /**
     * Shipped conflict registry: SwagHub module key -> plugin name that owns it.
     * Verified against SwagCore-1.0.0.jar (plugin name "SwagCore") per §6.3, plus
     * EssentialsX's well-known overlapping command set.
     */
    private static final Map<String, Set<String>> DEFAULT_CONFLICTS = buildDefaults();

    private static Map<String, Set<String>> buildDefaults() {
        Map<String, Set<String>> defaults = new LinkedHashMap<>();
        defaults.put("SwagCore", Set.of(
                "scoreboard", "tablist", "announcements", "join-quit-messages", "holograms", "vanish"
        ));
        defaults.put("EssentialsX", Set.of(
                "fly", "gamemode", "vanish", "clearchat"
        ));
        return Collections.unmodifiableMap(defaults);
    }

    private final SwagHub plugin;

    private final Map<String, Set<String>> conflictRegistry = new LinkedHashMap<>();
    private final Map<String, OverrideMode> overrides = new LinkedHashMap<>();
    private final Map<String, String> yieldedModules = new LinkedHashMap<>();

    private ServerRole serverRole = ServerRole.HUB;
    private boolean autoYield = true;

    public CompatibilityManager(SwagHub plugin) {
        this.plugin = plugin;
    }

    /** Re-reads server-role, auto-yield, overrides, and the config-extended conflict registry. */
    public void load() {
        FileConfiguration cfg = plugin.getConfig();

        serverRole = ServerRole.fromConfig(cfg.getString("server-role", "hub"));
        autoYield = cfg.getBoolean("compatibility.auto-yield", true);

        conflictRegistry.clear();
        for (Map.Entry<String, Set<String>> entry : DEFAULT_CONFLICTS.entrySet()) {
            conflictRegistry.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        // Config-extensible: compatibility.conflicts.<PluginName>: [module keys...]
        // Merged (unioned) with the shipped defaults per plugin name, never replacing them,
        // so a typo'd user entry can't accidentally erase a built-in mapping.
        ConfigurationSection conflictsSection = cfg.getConfigurationSection("compatibility.conflicts");
        if (conflictsSection != null) {
            for (String pluginName : conflictsSection.getKeys(false)) {
                List<String> extraModules = conflictsSection.getStringList(pluginName);
                if (extraModules.isEmpty()) {
                    continue;
                }
                conflictRegistry.computeIfAbsent(pluginName, key -> new LinkedHashSet<>())
                        .addAll(extraModules);
            }
        }

        overrides.clear();
        ConfigurationSection overridesSection = cfg.getConfigurationSection("compatibility.overrides");
        if (overridesSection != null) {
            for (String moduleKey : overridesSection.getKeys(false)) {
                String raw = overridesSection.getString(moduleKey, "auto");
                overrides.put(moduleKey.toLowerCase(Locale.ROOT), OverrideMode.fromConfig(raw));
            }
        }

        // Yield decisions are re-derived fresh on every load()/reload — stale entries
        // from a previous config state must not linger and be misreported by /ah info.
        yieldedModules.clear();
    }

    /**
     * Resolves whether a module should ultimately be enabled, applying (in priority order)
     * per-module override, then auto-yield conflict detection, then the module's own
     * config-requested state.
     *
     * @param moduleKey         the module's config key, e.g. {@code "scoreboard"}
     * @param requestedByConfig whether the module's own {@code modules.<key>} toggle
     *                          (or coded default) wants it on
     * @return the final decision the caller ({@code ModuleManager}) should apply
     */
    public boolean shouldEnable(String moduleKey, boolean requestedByConfig) {
        String key = moduleKey.toLowerCase(Locale.ROOT);
        OverrideMode mode = overrides.getOrDefault(key, OverrideMode.AUTO);

        if (mode == OverrideMode.ENABLED) {
            return true;
        }
        if (mode == OverrideMode.DISABLED) {
            return false;
        }

        // mode == AUTO
        if (!requestedByConfig) {
            return false;
        }

        if (autoYield) {
            String conflictingPlugin = findConflictingPlugin(key);
            if (conflictingPlugin != null) {
                recordYield(key, conflictingPlugin);
                return false;
            }
        }

        return true;
    }

    private String findConflictingPlugin(String moduleKey) {
        for (Map.Entry<String, Set<String>> entry : conflictRegistry.entrySet()) {
            if (!entry.getValue().contains(moduleKey)) {
                continue;
            }
            Plugin other = plugin.getServer().getPluginManager().getPlugin(entry.getKey());
            if (other != null && other.isEnabled()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void recordYield(String moduleKey, String pluginName) {
        if (yieldedModules.containsKey(moduleKey)) {
            // Already logged this load() cycle — never spam the console for one module.
            return;
        }
        yieldedModules.put(moduleKey, pluginName);
        // NOTE: no manual "[SwagHub]" text here — Bukkit's plugin logger already prefixes
        // console output with "[SwagHub] ", so this produces exactly the single console
        // line documented in §6.3 without doubling the tag.
        plugin.getLogger().info(prettify(moduleKey) + " yielded to " + pluginName
                + " (override: compatibility.overrides." + moduleKey + ": enabled)");
    }

    private String prettify(String moduleKey) {
        String[] parts = moduleKey.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        sb.append("module");
        return sb.toString();
    }

    public ServerRole getServerRole() {
        return serverRole;
    }

    public boolean isAutoYieldEnabled() {
        return autoYield;
    }

    /** Module key -> plugin name it yielded to, in the order each yield was first detected. */
    public Map<String, String> getYieldedModules() {
        return Collections.unmodifiableMap(yieldedModules);
    }

    /** Module key -> configured override mode, for modules that have an explicit override set. */
    public Map<String, OverrideMode> getOverrides() {
        return Collections.unmodifiableMap(overrides);
    }

    /** Plugin name -> module keys it conflicts with (shipped defaults + config extensions). */
    public Map<String, Set<String>> getConflictRegistry() {
        return Collections.unmodifiableMap(conflictRegistry);
    }

    public enum OverrideMode {
        AUTO, ENABLED, DISABLED;

        public static OverrideMode fromConfig(String value) {
            if (value == null || value.isBlank()) {
                return AUTO;
            }
            try {
                return OverrideMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return AUTO;
            }
        }
    }
}
