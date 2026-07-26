package com.SwagDev.SwagHub.modules.playerhider;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.data.SwagHubPlayerData;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.util.PlayerVisibilityCoordinator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * §5.7 Player hider: a three-state per-player visibility cycle
 * ({@link PlayerHiderState#ALL_VISIBLE} -&gt; {@link PlayerHiderState#HIDE_OTHERS} -&gt;
 * {@link PlayerHiderState#RANKS_ONLY} -&gt; back), persisted via
 * {@code SwagHubPlayerData.Data#playerHiderState} so it survives relog. Cycled by the
 * new {@code [cycle-player-hider]} action type (see
 * {@code com.SwagDev.SwagHub.action.types.CyclePlayerHiderAction}) — an admin drops
 * that action into an {@code items.yml} entry's {@code actions:} list exactly like any
 * other action (§5.9); this module has no bespoke item-giving logic of its own.
 *
 * <p><b>{@code RANKS_ONLY} semantics:</b> a viewer in this state has every player
 * WITHOUT {@code swaghub.playerhider.alwaysvisible} (default {@code op}) hidden from
 * them; players who DO hold that permission stay visible.</p>
 *
 * <p><b>Join/leave re-application (this is the genuinely tricky part — a per-(viewer,
 * target) pairwise relationship, not a per-player one):</b> {@link #onJoin} does two
 * things every time: (a) re-applies the JOINER's own restored preference against
 * every other player already online ({@link #applyViewerPreference}), and (b)
 * separately applies every OTHER already-online viewer's current preference against
 * the joiner ({@link #applyPairFor(Player, Player)} for each). Both directions are
 * necessary — (a) alone would leave every existing viewer's hider state stale against
 * the new player, and (b) alone would leave the joiner not enforcing their own
 * restored preference against anyone.</p>
 *
 * <p><b>Known limitation (documented, not fixed in this build step, per its own
 * explicit scoping):</b> if a player's {@code swaghub.playerhider.alwaysvisible}
 * permission changes at runtime (e.g. a permissions plugin demotes/promotes them
 * mid-session), no viewer's {@code RANKS_ONLY} state is re-evaluated against that
 * player until the next event that already triggers a re-application for one of them
 * (join/quit/cycle) — this module does not poll for permission changes.</p>
 *
 * <p><b>Shared visibility API — see {@link PlayerVisibilityCoordinator}'s own javadoc</b>
 * for why this module and {@code VanishModule} cannot each independently call
 * {@code Player#hidePlayer}/{@code #showPlayer} directly without risking one silently
 * undoing the other's still-wanted hide for the same (viewer, target) pair.</p>
 */
public class PlayerHiderModule extends Module implements Listener {

    private static final String ALWAYS_VISIBLE_PERMISSION = "swaghub.playerhider.alwaysvisible";
    private static final String REASON = "player-hider";

    private final Map<UUID, Long> lastCycleMillis = new HashMap<>();

    private long cooldownMillis;

    public PlayerHiderModule(SwagHub plugin) {
        super(plugin, "player-hider");
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        readSettings();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            applyViewerPreference(viewer);
        }
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        lastCycleMillis.clear();
    }

    @Override
    protected void onReload() {
        readSettings();
    }

    private void readSettings() {
        cooldownMillis = Math.max(0L, plugin.getConfig().getLong("player-hider.cooldown-seconds", 3L)) * 1000L;
    }

    // ─── State access ───

    /**
     * Public accessor for {@code %swaghub_playerhider_state%} (§5.11, build step 8) —
     * also used internally by every method below exactly as before (now public
     * doesn't change any existing call site's behavior).
     */
    public PlayerHiderState getState(Player player) {
        SwagHubPlayerData.Data data = plugin.getPlayerDataService()
                .getModuleData(player.getUniqueId(), "swaghub", SwagHubPlayerData.Data.class);
        return data == null ? PlayerHiderState.ALL_VISIBLE : PlayerHiderState.fromName(data.playerHiderState);
    }

    private void setState(Player player, PlayerHiderState state) {
        SwagHubPlayerData.Data data = plugin.getPlayerDataService()
                .getModuleData(player.getUniqueId(), "swaghub", SwagHubPlayerData.Data.class);
        if (data == null) {
            data = new SwagHubPlayerData.Data();
        }
        data.playerHiderState = state.name();
        plugin.getPlayerDataService().setModuleData(player.getUniqueId(), "swaghub", data);
    }

    /**
     * Advances {@code player}'s own state by one step, subject to the configured
     * cooldown — sends {@code player-hider-cooldown} (never silently ignores) on a
     * rejected attempt, per this module's own explicit requirement.
     */
    public void cycle(Player player) {
        long now = System.currentTimeMillis();
        long last = lastCycleMillis.getOrDefault(player.getUniqueId(), 0L);
        long remainingMs = (last + cooldownMillis) - now;
        if (remainingMs > 0) {
            plugin.getMessageUtil().send(player, "player-hider-cooldown",
                    Map.of("seconds", String.valueOf((remainingMs + 999) / 1000)));
            return;
        }

        lastCycleMillis.put(player.getUniqueId(), now);
        PlayerHiderState next = getState(player).next();
        setState(player, next);
        applyViewerPreference(player);
        plugin.getMessageUtil().send(player, "player-hider-cycled", Map.of("state", prettify(next)));
    }

    private String prettify(PlayerHiderState state) {
        return switch (state) {
            case ALL_VISIBLE -> "All Visible";
            case HIDE_OTHERS -> "Hide Others";
            case RANKS_ONLY -> "Ranks Only";
        };
    }

    // ─── Applying a viewer's preference against everyone else online ───

    private void applyViewerPreference(Player viewer) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer)) {
                continue;
            }
            applyPairFor(viewer, target);
        }
    }

    private void applyPairFor(Player viewer, Player target) {
        PlayerHiderState state = getState(viewer);
        boolean shouldHide = switch (state) {
            case ALL_VISIBLE -> false;
            case HIDE_OTHERS -> true;
            case RANKS_ONLY -> !target.hasPermission(ALWAYS_VISIBLE_PERMISSION);
        };
        plugin.getPlayerVisibilityCoordinator().setHidden(plugin, viewer, target, REASON, shouldHide);
    }

    // ─── Listeners ───

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        // (a) The joiner's own restored preference, applied against everyone already online.
        applyViewerPreference(joiner);
        // (b) Every other already-online viewer's current preference, applied against the joiner.
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(joiner)) {
                continue;
            }
            applyPairFor(viewer, joiner);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastCycleMillis.remove(uuid);
        plugin.getPlayerVisibilityCoordinator().clearViewer(uuid);
        plugin.getPlayerVisibilityCoordinator().clearTargetEverywhere(uuid);
    }
}
