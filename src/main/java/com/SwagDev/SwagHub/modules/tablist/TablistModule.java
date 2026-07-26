package com.SwagDev.SwagHub.modules.tablist;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.placeholder.SwagHubPlaceholders;
import com.SwagDev.SwagHub.util.HubWorldUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * §5.5 Tablist: an animated/static header + footer, per-world (or {@code default}
 * fallback), refreshed on a single repeating task at {@code update-interval-ticks},
 * sent via {@code Player#sendPlayerListHeaderAndFooter(Component, Component)}.
 *
 * <p><b>Hub-world gating and per-world content selection follow the exact same
 * decision as {@link com.SwagDev.SwagHub.modules.scoreboard.ScoreboardModule}</b> —
 * see that class's javadoc and DECISIONS.md Step 5. No per-player toggle exists for
 * the tablist (unlike the scoreboard's {@code /ah scoreboard}) — nothing in §5.5 asks
 * for one.</p>
 *
 * <p><b>"No per-tick work when idle" (§4):</b> a resend only happens when the
 * resolved header/footer text actually changed since the last render for that player
 * (cached per player) — even though header/footer resends don't visually flicker the
 * way naive scoreboard rebuilding could, this keeps the same "only touch it on change"
 * discipline for consistency and to honor §4 literally.</p>
 */
public class TablistModule extends Module implements Listener {

    private final SwagHubPlaceholders placeholders;
    private final Map<UUID, String[]> lastSent = new HashMap<>(); // [0]=header plain text, [1]=footer plain text

    private Map<String, TablistWorldConfig> worldConfigs = Collections.emptyMap();
    private long updateIntervalTicks = 20L;
    private long tickCounter = 0L;
    private BukkitTask task;

    public TablistModule(SwagHub plugin) {
        super(plugin, "tablist");
        this.placeholders = new SwagHubPlaceholders(plugin);
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        scheduleTask();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    @Override
    protected void onDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
        for (UUID uuid : new ArrayList<>(lastSent.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            }
        }
        lastSent.clear();
        worldConfigs = Collections.emptyMap();
    }

    @Override
    protected void onReload() {
        loadConfig();
        if (task != null) {
            task.cancel();
        }
        scheduleTask();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    // ─── Loading ───

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "tablist.yml");
        if (!file.exists()) {
            plugin.saveResource("tablist.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        updateIntervalTicks = Math.max(1L, yaml.getLong("update-interval-ticks", 20L));
        worldConfigs = Collections.unmodifiableMap(
                TablistConfigParser.parseWorlds(yaml.getConfigurationSection("worlds"), plugin.getLogger()::warning));
    }

    private void scheduleTask() {
        long interval = Math.max(1L, updateIntervalTicks);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    private void tick() {
        tickCounter += updateIntervalTicks;
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    // ─── Per-player render ───

    public void refreshPlayer(Player player) {
        TablistWorldConfig config = resolveConfigFor(player.getWorld());
        if (!HubWorldUtil.isHubWorld(plugin, player.getWorld()) || config == null) {
            String[] previous = lastSent.remove(player.getUniqueId());
            if (previous != null) {
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            }
            return;
        }

        String header = placeholders.apply(config.getHeader().currentFrame(tickCounter), player);
        String footer = placeholders.apply(config.getFooter().currentFrame(tickCounter), player);

        String[] previous = lastSent.get(player.getUniqueId());
        if (previous != null && header.equals(previous[0]) && footer.equals(previous[1])) {
            return; // unchanged since the last render — never resend for no reason (§4)
        }
        player.sendPlayerListHeaderAndFooter(plugin.getMessageUtil().parse(header), plugin.getMessageUtil().parse(footer));
        lastSent.put(player.getUniqueId(), new String[]{header, footer});
    }

    private TablistWorldConfig resolveConfigFor(World world) {
        TablistWorldConfig config = worldConfigs.get(world.getName());
        return config != null ? config : worldConfigs.get("default");
    }

    // ─── Listeners (immediate effect, don't wait for the next tick) ───

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastSent.remove(event.getPlayer().getUniqueId());
    }
}
