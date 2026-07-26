package com.SwagDev.SwagHub.modules.announcements;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ParsedAction;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.util.HubWorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * §5.6 Announcements: per-(hub-)world rotating broadcasts — sequential or random —
 * each entry a raw action list reusing the exact existing {@code ActionParser}/
 * {@code ActionRegistry} machinery (no parallel action-string-list executor).
 *
 * <p><b>Hub-world gating and per-world content selection follow the exact same
 * decision as {@code ScoreboardModule}/{@code TablistModule}</b> — see
 * {@link com.SwagDev.SwagHub.modules.scoreboard.ScoreboardModule}'s javadoc and
 * DECISIONS.md Step 5.</p>
 *
 * <p><b>Per-world independent timers on one shared task</b> (§4's "no per-tick work
 * when idle"): rather than one {@link BukkitTask} per world, a single task runs every
 * {@code check-interval-ticks} and, for each currently-loaded hub world with entries
 * configured, accumulates an elapsed-ticks counter; an announcement fires for that
 * world only once its OWN {@code interval-ticks} (or the top-level
 * {@code default-interval-ticks} fallback) has actually elapsed — mirrors
 * {@code ProxyModule}'s single-task-per-module precedent instead of spawning N tasks.</p>
 *
 * <p>Broadcasts to EVERY player currently in that world (not a single "triggering"
 * player) — one {@link ActionContext} is built and the entry's already-{@link
 * com.SwagDev.SwagHub.action.ActionParser#parse parsed} action list is executed once
 * per online player in the world, exactly matching how {@code [message]}/
 * {@code [actionbar]}/{@code [title]}/{@code [sound]} are inherently per-player sends
 * anyway.</p>
 */
public class AnnouncementsModule extends Module {

    private Map<String, AnnouncementWorldConfig> worldConfigs = Collections.emptyMap();
    private long checkIntervalTicks = 20L;
    private long defaultIntervalTicks = 600L;
    private BukkitTask task;

    private final Map<String, Long> elapsedTicksByWorld = new HashMap<>();
    private final Map<String, Integer> sequentialIndexByWorld = new HashMap<>();

    public AnnouncementsModule(SwagHub plugin) {
        super(plugin, "announcements");
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        loadConfig();
        scheduleTask();
    }

    @Override
    protected void onDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        elapsedTicksByWorld.clear();
        sequentialIndexByWorld.clear();
        worldConfigs = Collections.emptyMap();
    }

    @Override
    protected void onReload() {
        loadConfig();
        if (task != null) {
            task.cancel();
        }
        scheduleTask();
        // Reset per-world timing/rotation state rather than trying to carry it across a
        // reload — a shrunk entries list could otherwise leave a stale sequential index
        // pointing past the end of the new list.
        elapsedTicksByWorld.clear();
        sequentialIndexByWorld.clear();
    }

    // ─── Loading ───

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "announcements.yml");
        if (!file.exists()) {
            plugin.saveResource("announcements.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        checkIntervalTicks = Math.max(1L, yaml.getLong("check-interval-ticks", 20L));
        defaultIntervalTicks = Math.max(1L, yaml.getLong("default-interval-ticks", 600L));
        worldConfigs = Collections.unmodifiableMap(
                AnnouncementConfigParser.parseWorlds(yaml.getConfigurationSection("worlds"), plugin.getLogger()::warning));
    }

    private void scheduleTask() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, checkIntervalTicks, checkIntervalTicks);
    }

    private void tick() {
        for (World world : Bukkit.getWorlds()) {
            if (!HubWorldUtil.isHubWorld(plugin, world)) {
                continue;
            }
            AnnouncementWorldConfig config = resolveConfigFor(world);
            if (config == null || config.getEntries().isEmpty()) {
                continue;
            }

            long interval = config.getIntervalTicksOverride() != null ? config.getIntervalTicksOverride() : defaultIntervalTicks;
            interval = Math.max(checkIntervalTicks, interval);

            long elapsed = elapsedTicksByWorld.merge(world.getName(), checkIntervalTicks, Long::sum);
            if (elapsed < interval) {
                continue;
            }
            elapsedTicksByWorld.put(world.getName(), 0L);
            announceTo(world, config);
        }
    }

    private void announceTo(World world, AnnouncementWorldConfig config) {
        List<AnnouncementEntry> entries = config.getEntries();
        AnnouncementEntry entry;
        if (config.getRotation() == Rotation.RANDOM) {
            entry = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
        } else {
            int index = sequentialIndexByWorld.getOrDefault(world.getName(), 0) % entries.size();
            entry = entries.get(index);
            sequentialIndexByWorld.put(world.getName(), (index + 1) % entries.size());
        }

        List<ParsedAction> parsed = plugin.getActionParser().parse(entry.getActions());
        for (Player player : world.getPlayers()) {
            ActionContext context = new ActionContext(plugin, player);
            plugin.getActionParser().executeParsed(parsed, context);
        }
    }

    private AnnouncementWorldConfig resolveConfigFor(World world) {
        AnnouncementWorldConfig config = worldConfigs.get(world.getName());
        return config != null ? config : worldConfigs.get("default");
    }
}
