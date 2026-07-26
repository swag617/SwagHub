package com.SwagDev.SwagHub.modules.spawn;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.command.LobbyCommand;
import com.SwagDev.SwagHub.command.SetLobbyCommand;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.util.HubWorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * §5.1 spawn/lobby: {@code /setlobby}, {@code /lobby} (delay + move-cancel),
 * spawn-on-join, spawn-on-void-fall, spawn-on-respawn, plus the {@link SpawnStore}
 * persistence layer (§4/§11 build step 2's setlobby-restart fix).
 *
 * <p><b>Module-boundary decision (see DECISIONS.md Step 2 §1):</b> this module
 * always defaults enabled regardless of {@code server-role} ({@link #isEnabledByDefault()}
 * is not overridden — it keeps {@link Module}'s {@code true} default). {@code /setlobby}
 * /{@code /lobby} and the void-fall safety net are utility behavior never named in
 * §6.2's "hub-behavior modules default OFF on {@code game} role" list, and §6.3
 * explicitly requires void-fall teleport to "stay enabled even with SwagCore
 * present." Only the internal spawn-on-join sub-toggle's own config default could
 * reasonably vary by role — it does not in this build, see DECISIONS.md for why.</p>
 */
public class SpawnModule extends Module implements Listener {

    private final SpawnStore store;
    private final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();
    private final Map<UUID, Location> pendingOrigin = new HashMap<>();

    private CommandExecutor disabledFallback;
    private SetLobbyCommand setLobbyCommand;
    private LobbyCommand lobbyCommand;

    private long teleportDelayTicks;
    private boolean cancelOnMove;
    private boolean spawnOnJoin;
    private boolean spawnOnVoidFall;
    private boolean spawnOnRespawn;

    public SpawnModule(SwagHub plugin) {
        super(plugin, "spawn");
        this.store = new SpawnStore(plugin);
    }

    @Override
    protected void onEnable() {
        store.load();
        readSettings();

        Bukkit.getPluginManager().registerEvents(this, plugin);

        disabledFallback = (sender, command, label, args) -> {
            plugin.getMessageUtil().send(sender, "module-disabled");
            return true;
        };
        setLobbyCommand = new SetLobbyCommand(plugin, this);
        lobbyCommand = new LobbyCommand(plugin, this);

        PluginCommand setLobby = plugin.getCommand("setlobby");
        if (setLobby != null) {
            setLobby.setExecutor(setLobbyCommand);
        } else {
            plugin.getLogger().warning("Could not find the 'setlobby' command — check plugin.yml.");
        }

        PluginCommand lobby = plugin.getCommand("lobby");
        if (lobby != null) {
            lobby.setExecutor(lobbyCommand);
        } else {
            plugin.getLogger().warning("Could not find the 'lobby' command — check plugin.yml.");
        }
    }

    @Override
    protected void onDisable() {
        // Cancel every in-flight /lobby countdown before this module goes dark — a
        // module must never leave a scheduled task pointing at a command/listener
        // that's about to stop being registered.
        for (BukkitTask task : pendingTeleports.values()) {
            task.cancel();
        }
        pendingTeleports.clear();
        pendingOrigin.clear();

        if (disabledFallback != null) {
            PluginCommand setLobby = plugin.getCommand("setlobby");
            if (setLobby != null) {
                setLobby.setExecutor(disabledFallback);
            }
            PluginCommand lobby = plugin.getCommand("lobby");
            if (lobby != null) {
                lobby.setExecutor(disabledFallback);
            }
        }

        HandlerList.unregisterAll(this);

        // Defensive second write on shutdown/disable, per §4's exact wording
        // ("immediately on change AND on shutdown") — set() already wrote
        // synchronously the moment /setlobby ran; this is belt-and-suspenders
        // against any in-memory state that hasn't yet hit disk for any reason.
        store.flush();
    }

    @Override
    protected void onReload() {
        readSettings();
    }

    private void readSettings() {
        teleportDelayTicks = Math.max(0, plugin.getConfig().getLong("spawn.lobby-teleport-delay-ticks", 60));
        cancelOnMove = plugin.getConfig().getBoolean("spawn.cancel-on-move", true);
        spawnOnJoin = plugin.getConfig().getBoolean("spawn.spawn-on-join", true);
        spawnOnVoidFall = plugin.getConfig().getBoolean("spawn.spawn-on-void-fall", true);
        spawnOnRespawn = plugin.getConfig().getBoolean("spawn.spawn-on-respawn", true);
    }

    // ─── Consumed by SetLobbyCommand / LobbyCommand ───

    public SpawnStore getStore() {
        return store;
    }

    /**
     * Starts (or restarts) a {@code /lobby} teleport countdown for {@code player},
     * or teleports immediately if the configured delay is 0 or the player holds
     * {@code swaghub.bypass.lobbydelay}. Safe to call again while already pending —
     * the previous countdown is cancelled and replaced rather than stacking tasks.
     */
    public void beginLobbyTeleport(Player player) {
        Optional<Location> destination = store.getBukkitLocation();
        if (destination.isEmpty()) {
            plugin.getMessageUtil().send(player, "lobby-not-set");
            return;
        }

        cancelPending(player.getUniqueId());

        if (teleportDelayTicks <= 0 || player.hasPermission("swaghub.bypass.lobbydelay")) {
            player.teleport(destination.get());
            plugin.getMessageUtil().send(player, "lobby-teleporting-now");
            return;
        }

        pendingOrigin.put(player.getUniqueId(), player.getLocation());
        plugin.getMessageUtil().send(player, "lobby-teleporting-in",
                Map.of("seconds", formatSeconds(teleportDelayTicks)));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTeleports.remove(player.getUniqueId());
            pendingOrigin.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            Optional<Location> loc = store.getBukkitLocation();
            if (loc.isEmpty()) {
                plugin.getMessageUtil().send(player, "lobby-not-set");
                return;
            }
            player.teleport(loc.get());
        }, teleportDelayTicks);

        pendingTeleports.put(player.getUniqueId(), task);
    }

    private String formatSeconds(long ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    private void cancelPending(UUID uuid) {
        BukkitTask existing = pendingTeleports.remove(uuid);
        if (existing != null) {
            existing.cancel();
        }
        pendingOrigin.remove(uuid);
    }

    // ─── Listeners ───

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        if (!spawnOnJoin) {
            return;
        }
        // No HubWorldUtil gate here on purpose — the whole point of spawn-on-join is
        // to force the player INTO a hub world regardless of which world they
        // logged into; see DECISIONS.md Step 2 for the reasoning.
        store.getBukkitLocation().ifPresent(loc -> event.getPlayer().teleport(loc));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!spawnOnRespawn) {
            return;
        }
        store.getBukkitLocation().ifPresent(event::setRespawnLocation);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (!spawnOnVoidFall || event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!HubWorldUtil.isHubWorld(plugin, player.getWorld())) {
            return;
        }
        Optional<Location> destination = store.getBukkitLocation();
        if (destination.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        player.setFallDistance(0f);
        player.teleport(destination.get());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!cancelOnMove || pendingTeleports.isEmpty()) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        Location origin = pendingOrigin.get(uuid);
        if (origin == null || event.getTo() == null) {
            return;
        }
        // Ignore look-only changes (mouse movement without actually moving) — only a
        // real block-position change should cancel the countdown.
        if (origin.getBlockX() == event.getTo().getBlockX()
                && origin.getBlockY() == event.getTo().getBlockY()
                && origin.getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        cancelPending(uuid);
        plugin.getMessageUtil().send(event.getPlayer(), "lobby-teleport-cancelled");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // A pending /lobby countdown makes no sense once the player has died —
        // clean it up rather than leaving a stale task around.
        cancelPending(event.getEntity().getUniqueId());
    }
}
