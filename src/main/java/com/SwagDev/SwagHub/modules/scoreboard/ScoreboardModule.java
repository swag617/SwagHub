package com.SwagDev.SwagHub.modules.scoreboard;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.data.SwagHubPlayerData;
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
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * §5.4 Scoreboard: a per-player sidebar with an animated/static title and per-world
 * (or {@code default}-fallback) lines, refreshed on a single repeating task at
 * {@code update-interval-ticks}.
 *
 * <p><b>Hub-world gating decision (applies identically to {@link
 * com.SwagDev.SwagHub.modules.tablist.TablistModule}/{@link
 * com.SwagDev.SwagHub.modules.announcements.AnnouncementsModule} — see DECISIONS.md
 * Step 5):</b> the scoreboard is shown ONLY while a player is standing in a world
 * listed in {@code hub-worlds} (via {@link HubWorldUtil#isHubWorld}), mirroring
 * {@code WorldProtectionModule}/{@code JoinSettingsModule}'s existing precedent — this
 * is a hub-identity feature, not something that should follow a player onto an
 * unrelated game world SwagHub happens to coexist on. Per-world CONTENT selection
 * (which title/lines a given hub world shows) is a SEPARATE, independent concern
 * handled entirely by {@code scoreboard.yml}'s {@code worlds:} map + {@code default}
 * fallback — a server can have multiple listed hub worlds with different scoreboard
 * content, or zero (which shows nothing at all, since {@code hub-worlds: []} means
 * "every world is a hub world" per {@link HubWorldUtil}'s own documented default).</p>
 *
 * <p><b>Anti-flicker technique</b> (§5.4's explicit "team-based line technique"
 * requirement): each viewing player gets exactly ONE {@link Scoreboard} + ONE
 * {@link Objective} + up to {@link ScoreboardConfigParser#MAX_LINES} {@link Team}s,
 * created ONCE in {@link #showScoreboard(Player)} — never recreated per tick. Each
 * team owns one permanently-unique, invisible "entry" string — a raw legacy
 * section-sign + hex-digit pair ({@code "§" + "0".."f"}), the same underlying
 * bytes {@code org.bukkit.ChatColor}'s color constants produce, but built directly
 * as a plain string rather than via that enum: {@code ChatColor} is marked
 * {@code @Deprecated} on this project's actually-resolved paper-api jar (live-verified
 * via {@code mvn clean compile -Dmaven.compiler.showDeprecation=true} — the "jar
 * wins" rule, see DECISIONS.md Step 5), so the raw-string form avoids depending on a
 * deprecated Bukkit API for something this simple. Every render tick
 * ({@link #renderForPlayer}) only calls {@code team.prefix(...)}/
 * {@code objective.displayName(...)} when the resolved text actually changed since the
 * last render (per-player-per-line cache), and only re-touches {@code Score#setScore}
 * when the number of ACTIVE lines changes (not every tick) — this is what "no flicker"
 * concretely means here: avoiding wasteful, naive full teardown/rebuild every tick.</p>
 */
public class ScoreboardModule extends Module implements Listener {

    private static final String OBJECTIVE_NAME = "swaghub_sb";
    private static final String TEAM_PREFIX = "swaghub_l";
    private static final int MAX_LINES = ScoreboardConfigParser.MAX_LINES;

    private final SwagHubPlaceholders placeholders;
    private final Map<UUID, PlayerScoreboardSession> sessions = new HashMap<>();

    private Map<String, ScoreboardWorldConfig> worldConfigs = Collections.emptyMap();
    private long updateIntervalTicks = 20L;
    private long tickCounter = 0L;
    private BukkitTask task;

    public ScoreboardModule(SwagHub plugin) {
        super(plugin, "scoreboard");
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
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            hideScoreboard(uuid);
        }
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
        File file = new File(plugin.getDataFolder(), "scoreboard.yml");
        if (!file.exists()) {
            plugin.saveResource("scoreboard.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        updateIntervalTicks = Math.max(1L, yaml.getLong("update-interval-ticks", 20L));
        worldConfigs = Collections.unmodifiableMap(
                ScoreboardConfigParser.parseWorlds(yaml.getConfigurationSection("worlds"), plugin.getLogger()::warning));
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

    // ─── Per-player show/hide/render ───

    /**
     * Re-evaluates whether {@code player} should currently be shown a scoreboard, and
     * either shows/hides/renders accordingly. Called from the repeating task for every
     * online player, and directly (for immediate effect, not waiting for the next tick)
     * on join, world change, and the {@code /ah scoreboard} toggle.
     */
    public void refreshPlayer(Player player) {
        ScoreboardWorldConfig config = resolveConfigFor(player.getWorld());
        boolean shouldShow = HubWorldUtil.isHubWorld(plugin, player.getWorld())
                && config != null
                && isScoreboardEnabledFor(player);

        PlayerScoreboardSession session = sessions.get(player.getUniqueId());
        if (!shouldShow) {
            if (session != null) {
                hideScoreboard(player.getUniqueId());
            }
            return;
        }
        if (session == null) {
            session = showScoreboard(player);
        }
        renderForPlayer(player, session, config);
    }

    private PlayerScoreboardSession showScoreboard(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.empty());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<Team> teams = new ArrayList<>(MAX_LINES);
        String[] entries = new String[MAX_LINES];
        for (int i = 0; i < MAX_LINES; i++) {
            Team team = scoreboard.registerNewTeam(TEAM_PREFIX + i);
            // A raw legacy "section sign + hex digit" pair — invisible, and guaranteed
            // unique for i in [0, 15] (16 hex digits available, MAX_LINES is 15). Built
            // directly rather than via org.bukkit.ChatColor, which is @Deprecated on this
            // project's resolved paper-api jar (see class javadoc / DECISIONS.md Step 5).
            String entry = "§" + Integer.toHexString(i);
            team.addEntry(entry);
            teams.add(team);
            entries[i] = entry;
        }

        PlayerScoreboardSession session = new PlayerScoreboardSession(scoreboard, objective, teams, entries);
        sessions.put(player.getUniqueId(), session);
        player.setScoreboard(scoreboard);
        return session;
    }

    private void hideScoreboard(UUID uuid) {
        PlayerScoreboardSession session = sessions.remove(uuid);
        if (session == null) {
            return;
        }
        for (Team team : session.teams) {
            team.unregister();
        }
        session.objective.unregister();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void renderForPlayer(Player player, PlayerScoreboardSession session, ScoreboardWorldConfig config) {
        String resolvedTitle = placeholders.apply(config.getTitle().currentFrame(tickCounter), player);
        if (!resolvedTitle.equals(session.lastTitle)) {
            session.objective.displayName(plugin.getMessageUtil().parse(resolvedTitle));
            session.lastTitle = resolvedTitle;
        }

        List<String> lines = config.getLines();
        int count = Math.min(lines.size(), MAX_LINES);

        if (count != session.activeLineCount) {
            for (int i = 0; i < MAX_LINES; i++) {
                Score score = session.objective.getScore(session.entries[i]);
                if (i < count) {
                    score.setScore(MAX_LINES - 1 - i);
                } else if (score.isScoreSet()) {
                    score.resetScore();
                    session.lastLine[i] = null;
                }
            }
            session.activeLineCount = count;
        }

        for (int i = 0; i < count; i++) {
            String resolved = placeholders.apply(lines.get(i), player);
            if (!resolved.equals(session.lastLine[i])) {
                session.teams.get(i).prefix(plugin.getMessageUtil().parse(resolved));
                session.lastLine[i] = resolved;
            }
        }
    }

    private ScoreboardWorldConfig resolveConfigFor(World world) {
        ScoreboardWorldConfig config = worldConfigs.get(world.getName());
        return config != null ? config : worldConfigs.get("default");
    }

    private boolean isScoreboardEnabledFor(Player player) {
        SwagHubPlayerData.Data data = plugin.getPlayerDataService()
                .getModuleData(player.getUniqueId(), "swaghub", SwagHubPlayerData.Data.class);
        return data == null || data.scoreboardEnabled;
    }

    // ─── /ah scoreboard toggle ───

    /** Flips {@code player}'s stored scoreboard toggle, applies the change immediately, and returns the new state. */
    public boolean toggleForPlayer(Player player) {
        SwagHubPlayerData.Data data = plugin.getPlayerDataService()
                .getModuleData(player.getUniqueId(), "swaghub", SwagHubPlayerData.Data.class);
        if (data == null) {
            data = new SwagHubPlayerData.Data();
        }
        data.scoreboardEnabled = !data.scoreboardEnabled;
        plugin.getPlayerDataService().setModuleData(player.getUniqueId(), "swaghub", data);
        refreshPlayer(player);
        return data.scoreboardEnabled;
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
        hideScoreboard(event.getPlayer().getUniqueId());
    }

    /** Per-player scoreboard/objective/team state — created once, mutated in place, never rebuilt per tick. */
    private static final class PlayerScoreboardSession {
        final Scoreboard scoreboard;
        final Objective objective;
        final List<Team> teams;
        final String[] entries;
        final String[] lastLine = new String[MAX_LINES];
        String lastTitle;
        int activeLineCount = -1; // sentinel: forces the first score-assignment pass

        PlayerScoreboardSession(Scoreboard scoreboard, Objective objective, List<Team> teams, String[] entries) {
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.teams = teams;
            this.entries = entries;
        }
    }
}
