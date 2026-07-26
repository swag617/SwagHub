package com.SwagDev.SwagHub.modules.launchpad;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.util.HubWorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * §5.7 Launchpads: config-defined pressure-plate-or-block locations that launch a
 * player in their look direction + upward with a particle + sound (§3's utility
 * module — {@link #isEnabledByDefault()} is always {@code true} regardless of
 * {@code server-role}, exactly like {@code proxy}/{@code menus}).
 *
 * <p><b>Own file, {@code launchpads.yml} (resolved ambiguity):</b> unlike
 * double-jump/chat-controls (a bounded handful of scalar settings), launchpads are a
 * variable-length, admin-authored LIST of named locations — the exact same shape as
 * {@code items.yml}/{@code scoreboard.yml}'s {@code worlds:} map, both of which
 * already earn their own file in this codebase. Follows {@code items.yml}'s id-keyed
 * map format ({@code launchpads.<id>: {...}}) rather than a raw YAML list, for the
 * same "each entry is independently named/referenceable" reason join items are.</p>
 *
 * <p><b>Trigger event (resolved ambiguity — "verify which real event fires"):</b>
 * {@link PlayerInteractEvent} with {@link Action#PHYSICAL} is Bukkit's documented,
 * long-standing event for "a player's client reports stepping on a pressure plate /
 * tripwire" — NOT {@code PlayerMoveEvent}, which fires many times per second for
 * every online player regardless of whether they're anywhere near a launchpad and
 * would require continuous per-tick location-vs-launchpad-list comparisons, directly
 * against §4's "no per-tick work when idle" performance rule. A stock pressure plate
 * (any material ending in {@code _PRESSURE_PLATE}) is the intended trigger block —
 * placing one at a launchpad's configured coordinates is required for the physical
 * interaction to fire at all; this module does not place/replace blocks itself.</p>
 *
 * <p>No wand/selection command — matches {@code PvpZone}'s "simple two-corner cuboid,
 * config-only" precedent extended to "simple config-only point location" here; §11
 * step 7 is where wand tooling is introduced (for portals), and nothing in §5.7's
 * launchpad bullet asks for one.</p>
 */
public class LaunchpadModule extends Module implements Listener {

    private Map<String, LaunchpadConfig> byLocationKey = Collections.emptyMap();

    public LaunchpadModule(SwagHub plugin) {
        super(plugin, "launchpads");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        loadLaunchpads();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        byLocationKey = Collections.emptyMap();
    }

    @Override
    protected void onReload() {
        loadLaunchpads();
    }

    private void loadLaunchpads() {
        File file = new File(plugin.getDataFolder(), "launchpads.yml");
        if (!file.exists()) {
            plugin.saveResource("launchpads.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        Map<String, LaunchpadConfig> parsed = new LinkedHashMap<>();
        ConfigurationSection launchpadsSection = yaml.getConfigurationSection("launchpads");
        if (launchpadsSection != null) {
            for (String id : launchpadsSection.getKeys(false)) {
                ConfigurationSection section = launchpadsSection.getConfigurationSection(id);
                LaunchpadConfigParser.parse(section, id, plugin.getLogger()::warning)
                        .ifPresent(config -> parsed.put(locationKey(config.getWorldName(), config.getX(), config.getY(), config.getZ()), config));
            }
        }
        byLocationKey = Collections.unmodifiableMap(parsed);
    }

    private String locationKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhysical(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!HubWorldUtil.isHubWorld(plugin, player.getWorld())) {
            return;
        }

        LaunchpadConfig config = byLocationKey.get(
                locationKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
        if (config == null) {
            return;
        }

        launch(player, config);
    }

    private void launch(Player player, LaunchpadConfig config) {
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() > 0.0001) {
            direction.normalize();
        }
        Vector velocity = direction.multiply(config.getPower());
        velocity.setY(config.getHeight());
        player.setVelocity(velocity);

        if (config.getParticleName() != null) {
            try {
                Particle particle = Particle.valueOf(config.getParticleName().trim().toUpperCase(Locale.ROOT));
                player.getWorld().spawnParticle(particle, player.getLocation(), 20, 0.4, 0.1, 0.4, 0.02);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Launchpad '" + config.getId() + "' has an invalid particle '"
                        + config.getParticleName() + "' — skipping the particle effect.");
            }
        }
        if (config.getSoundName() != null) {
            try {
                Sound sound = Sound.valueOf(config.getSoundName().trim().toUpperCase(Locale.ROOT));
                player.getWorld().playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Launchpad '" + config.getId() + "' has an invalid sound '"
                        + config.getSoundName() + "' — skipping the sound effect.");
            }
        }
    }

    /** Exposed for diagnostics — never mutated by callers. */
    public Map<String, LaunchpadConfig> getLaunchpads() {
        return byLocationKey;
    }
}
