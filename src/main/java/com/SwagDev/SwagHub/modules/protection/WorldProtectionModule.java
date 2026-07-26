package com.SwagDev.SwagHub.modules.protection;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.util.HubWorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.TimeSkipEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * §5.1 world protections: block break/place, hunger, fall damage, all damage, PvP
 * (with a config-defined PvP-zone exception), weather lock, time lock, mob
 * spawning, item drop/pickup, leaf decay, fire spread, block burn, TNT.
 *
 * <p>Every check below is gated through {@link HubWorldUtil#isHubWorld} — the ONE
 * shared world-whitelist utility §4 requires all modules to use; nothing here
 * re-implements its own world check.</p>
 *
 * <p><b>Module-boundary decision (DECISIONS.md Step 2 §1):</b> defaults enabled
 * only when {@code server-role: hub} — §6.2 explicitly lists "world protections"
 * in the hub-behavior set that defaults OFF on {@code server-role: game}.</p>
 */
public class WorldProtectionModule extends Module implements Listener {

    private boolean denyBlockBreak;
    private boolean denyBlockPlace;
    private boolean disableHunger;
    private boolean disableFallDamage;
    private boolean disableAllDamage;
    private boolean disablePvp;
    private List<PvpZone> pvpZones = new ArrayList<>();
    private boolean lockWeather;
    private boolean clearWeather;
    private boolean lockTime;
    private long fixedTime;
    private boolean denyMobSpawning;
    private boolean denyItemDrop;
    private boolean denyItemPickup;
    private boolean denyLeafDecay;
    private boolean denyFireSpread;
    private boolean denyBlockBurn;
    private boolean denyTnt;

    public WorldProtectionModule(SwagHub plugin) {
        super(plugin, "world-protection");
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        readSettings();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        applyWeatherAndTimeLock();
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
    }

    @Override
    protected void onReload() {
        readSettings();
        applyWeatherAndTimeLock();
    }

    private void readSettings() {
        var cfg = plugin.getConfig();
        denyBlockBreak = cfg.getBoolean("world-protection.deny-block-break", true);
        denyBlockPlace = cfg.getBoolean("world-protection.deny-block-place", true);
        disableHunger = cfg.getBoolean("world-protection.disable-hunger", true);
        disableFallDamage = cfg.getBoolean("world-protection.disable-fall-damage", true);
        disableAllDamage = cfg.getBoolean("world-protection.disable-all-damage", false);
        disablePvp = cfg.getBoolean("world-protection.disable-pvp", true);
        lockWeather = cfg.getBoolean("world-protection.lock-weather", true);
        clearWeather = cfg.getBoolean("world-protection.clear-weather", true);
        lockTime = cfg.getBoolean("world-protection.lock-time", true);
        fixedTime = cfg.getLong("world-protection.fixed-time", 6000L);
        denyMobSpawning = cfg.getBoolean("world-protection.deny-mob-spawning", true);
        denyItemDrop = cfg.getBoolean("world-protection.deny-item-drop", true);
        denyItemPickup = cfg.getBoolean("world-protection.deny-item-pickup", true);
        denyLeafDecay = cfg.getBoolean("world-protection.deny-leaf-decay", true);
        denyFireSpread = cfg.getBoolean("world-protection.deny-fire-spread", true);
        denyBlockBurn = cfg.getBoolean("world-protection.deny-block-burn", true);
        denyTnt = cfg.getBoolean("world-protection.deny-tnt", true);

        pvpZones = new ArrayList<>();
        List<Map<?, ?>> rawZones = cfg.getMapList("world-protection.pvp-zones");
        for (Map<?, ?> zoneMap : rawZones) {
            try {
                String name = String.valueOf(zoneMap.get("name"));
                String world = String.valueOf(zoneMap.get("world"));
                Map<?, ?> c1 = (Map<?, ?>) zoneMap.get("corner1");
                Map<?, ?> c2 = (Map<?, ?>) zoneMap.get("corner2");
                double x1 = toDouble(c1.get("x"));
                double y1 = toDouble(c1.get("y"));
                double z1 = toDouble(c1.get("z"));
                double x2 = toDouble(c2.get("x"));
                double y2 = toDouble(c2.get("y"));
                double z2 = toDouble(c2.get("z"));
                pvpZones.add(new PvpZone(name, world, x1, y1, z1, x2, y2, z2));
            } catch (Exception ex) {
                // Never abort the whole module for one bad config entry (§10.4) —
                // log a clear, specific warning and skip just this zone.
                plugin.getLogger().warning("Skipping malformed entry in world-protection.pvp-zones "
                        + "(expected name/world/corner1{x,y,z}/corner2{x,y,z}): " + zoneMap);
            }
        }
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("Not a number: " + value);
    }

    /**
     * Forces every currently-loaded hub world to the configured weather/time, once, on
     * enable/reload.
     *
     * <p><b>Patch 1, Fix 2 (see DECISIONS.md's Patch 1 section for the full
     * write-up):</b> weather-lock and time-lock are applied to each world in complete
     * isolation from every other world AND from each other — a world that rejects one
     * (or both) of these calls (observed live: {@code IllegalArgumentException:
     * "Cannot set time in world without world clock"} for a void-type world, which
     * used to throw straight out of this method and abort the ENTIRE module's {@code
     * onEnable()}/{@code onReload()}, silently disabling every hub-world protection
     * everywhere, in every world, not just the offending one) now only skips that ONE
     * feature for that ONE world, logging exactly one warning naming both. No public
     * Bukkit/Paper API was found (checked against the {@code paper-api
     * 1.21.1-R0.1-SNAPSHOT} jar this project compiles against) that lets a caller ask
     * "does this world support {@code setTime}/weather changes" ahead of time — this
     * failure mode is an internal implementation detail of certain generated/void
     * worlds, not something the public {@code World} interface exposes a capability
     * flag for. Per-world/per-feature {@code try}/{@code catch} isolation is therefore
     * the WHOLE mechanism here, not merely a backstop layered behind some other
     * pre-check — see {@link #applyWeatherLock(World)}/{@link #applyTimeLock(World)}.</p>
     */
    private void applyWeatherAndTimeLock() {
        for (World world : Bukkit.getWorlds()) {
            if (!HubWorldUtil.isHubWorld(plugin, world)) {
                continue;
            }
            // Deliberately two independent calls, each with its own try/catch — a
            // world that rejects the weather lock must not skip the time lock (or
            // vice versa), and neither may ever prevent the loop from reaching the
            // NEXT world.
            applyWeatherLock(world);
            applyTimeLock(world);
        }
    }

    /** Isolated weather-lock application for one world — see {@link #applyWeatherAndTimeLock()}'s javadoc. */
    private void applyWeatherLock(World world) {
        if (!lockWeather) {
            return;
        }
        try {
            world.setStorm(!clearWeather);
            world.setThundering(false);
        } catch (Exception ex) {
            plugin.getLogger().warning("Weather lock skipped for world '" + world.getName()
                    + "': " + describeFailure(ex) + " — every other protection in this and every "
                    + "other hub world is unaffected.");
        }
    }

    /**
     * Isolated time-lock application for one world — see {@link
     * #applyWeatherAndTimeLock()}'s javadoc. The exact phrase "this world has no
     * day/night clock" is intentional: it is the console line this Patch 1 fix exists
     * to produce for the observed live bug (a void-type hub world's {@code
     * IllegalArgumentException} on {@code World#setTime}).
     */
    private void applyTimeLock(World world) {
        if (!lockTime) {
            return;
        }
        try {
            world.setTime(fixedTime);
        } catch (Exception ex) {
            plugin.getLogger().warning("Time lock skipped for world '" + world.getName()
                    + "': this world has no day/night clock (" + describeFailure(ex) + ") — every "
                    + "other protection in this and every other hub world is unaffected.");
        }
    }

    private String describeFailure(Exception ex) {
        String message = ex.getMessage();
        return message != null && !message.isBlank() ? message : ex.getClass().getSimpleName();
    }

    private boolean inHubWorld(World world) {
        return HubWorldUtil.isHubWorld(plugin, world);
    }

    private boolean isInAnyPvpZone(Location location) {
        for (PvpZone zone : pvpZones) {
            if (zone.contains(location)) {
                return true;
            }
        }
        return false;
    }

    // ─── Block protections ───

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!denyBlockBreak || !inHubWorld(event.getBlock().getWorld())) {
            return;
        }
        if (event.getPlayer().hasPermission("swaghub.bypass.build")) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!denyBlockPlace || !inHubWorld(event.getBlock().getWorld())) {
            return;
        }
        if (event.getPlayer().hasPermission("swaghub.bypass.build")) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (denyLeafDecay && inHubWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (denyFireSpread
                && event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD
                && inHubWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (denyBlockBurn && inHubWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        if (denyTnt && inHubWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    // ─── Player/entity protections ───

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !inHubWorld(player.getWorld())) {
            return;
        }
        if (disableAllDamage) {
            event.setCancelled(true);
            return;
        }
        if (disableFallDamage && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // disableAllDamage is already handled by onEntityDamage() above (which runs
        // at NORMAL priority, before this HIGH-priority handler, and this handler
        // is ignoreCancelled=true) — the extra guard here is defensive in case
        // handler priorities are ever changed.
        if (!disablePvp || disableAllDamage) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player)) {
            return;
        }
        if (!inHubWorld(victim.getWorld())) {
            return;
        }
        if (victim.hasPermission("swaghub.bypass.pvp")) {
            return;
        }
        if (isInAnyPvpZone(victim.getLocation())) {
            return; // config-defined PvP zone exception
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!disableHunger || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!inHubWorld(player.getWorld())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (denyItemDrop && inHubWorld(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (denyItemPickup && event.getEntity() instanceof Player player && inHubWorld(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (denyMobSpawning
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM
                && inHubWorld(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    // ─── World-wide locks ───

    @EventHandler(ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (lockWeather && inHubWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (lockWeather && inHubWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        if (lockTime && inHubWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }
}
