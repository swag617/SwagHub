package com.SwagDev.SwagHub.modules.vanish;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.data.SwagHubPlayerData;
import com.SwagDev.SwagHub.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

/**
 * §5.10 {@code /vanish [player]} — module config key {@code vanish} is already
 * compat-reserved (yields to SwagCore/EssentialsX per §6.3). {@code
 * swaghub.command.vanish} (self, default {@code op}), {@code
 * swaghub.command.vanish.others} (target another player, default {@code op}), {@code
 * swaghub.vanish.see} (default {@code op}) — a PASSIVE visibility permission, not a
 * command permission, per §5.10's own phrasing, hence NOT under the
 * {@code swaghub.command.*} namespace.
 *
 * <p><b>{@code hidePlayer} alone satisfies "invisibility" (resolved ambiguity) — no
 * redundant {@link org.bukkit.potion.PotionEffectType#INVISIBILITY} layered on
 * top.</b> {@code Player#hidePlayer}/{@code #showPlayer} already fully removes the
 * target from every non-permitted viewer's tab list AND client-side entity
 * rendering — a real invisibility potion effect on top would add nothing visually
 * (the target isn't even sent to those clients at all) while creating a SEPARATE
 * state to keep synchronized (removed on drinking milk, visible as a status effect
 * icon to the vanished player themself, etc.) for zero behavioral gain. This is
 * exactly what every real vanish plugin (EssentialsX included) actually relies on.</p>
 *
 * <p><b>Persisted vanish flag</b> ({@code SwagHubPlayerData.Data#vanished}) so a
 * vanished staff member's next relog doesn't un-vanish them or announce their
 * join/quit. {@link #onJoin}/{@link #onQuit} both run at
 * {@link EventPriority#MONITOR} — deliberately the LAST priority tier, so blanking
 * the join/quit {@link net.kyori.adventure.text.Component} message is guaranteed to
 * win even if some other handler already set text on the same event (nothing in this
 * codebase currently does, but the ordering is chosen defensively rather than
 * assumed — see DECISIONS.md Step 6). Modifying an event at {@code MONITOR} is
 * usually discouraged, but "blank this message, unconditionally, after everyone
 * else" is exactly the one thing {@code MONITOR} is uniquely positioned to
 * guarantee.</p>
 *
 * <p><b>Shared visibility API — see {@link com.SwagDev.SwagHub.util.PlayerVisibilityCoordinator}'s
 * javadoc</b> for why this module and {@code PlayerHiderModule} cannot each
 * independently call {@code Player#hidePlayer}/{@code #showPlayer} directly.</p>
 */
public class VanishModule extends Module implements Listener, CommandExecutor {

    private static final String PERMISSION_SELF = "swaghub.command.vanish";
    private static final String PERMISSION_OTHERS = "swaghub.command.vanish.others";
    private static final String PERMISSION_SEE = "swaghub.vanish.see";
    private static final String REASON = "vanish";

    public VanishModule(SwagHub plugin) {
        super(plugin, "vanish");
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PluginCommand command = plugin.getCommand("vanish");
        if (command != null) {
            command.setExecutor(this);
        } else {
            plugin.getLogger().warning("Could not find the 'vanish' command — check plugin.yml.");
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isVanished(player)) {
                applyVanishState(player);
            }
        }
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        // Un-hide every currently-vanished player from everyone before this module goes
        // dark, so nobody stays invisible with no way to toggle it back off.
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (isVanished(target)) {
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (!viewer.equals(target)) {
                        plugin.getPlayerVisibilityCoordinator().setHidden(plugin, viewer, target, REASON, false);
                    }
                }
            }
        }
        PluginCommand command = plugin.getCommand("vanish");
        if (command != null) {
            command.setExecutor((sender, cmd, label, args) -> {
                plugin.getMessageUtil().send(sender, "module-disabled");
                return true;
            });
        }
    }

    @Override
    protected void onReload() {
        // No settings of its own — nothing to re-read.
    }

    /**
     * Public accessor for {@code %swaghub_vanished%} (§5.11, build step 8 — see
     * {@link com.SwagDev.SwagHub.placeholder.SwagHubPlaceholders}). Exposes the same
     * state every in-module caller already reads; no behavior change for those
     * callers, which keep calling this method (now public) exactly as before.
     */
    public boolean isVanished(Player player) {
        SwagHubPlayerData.Data data = plugin.getPlayerDataService()
                .getModuleData(player.getUniqueId(), "swaghub", SwagHubPlayerData.Data.class);
        return data != null && data.vanished;
    }

    private void setVanished(Player player, boolean vanished) {
        SwagHubPlayerData.Data data = plugin.getPlayerDataService()
                .getModuleData(player.getUniqueId(), "swaghub", SwagHubPlayerData.Data.class);
        if (data == null) {
            data = new SwagHubPlayerData.Data();
        }
        data.vanished = vanished;
        plugin.getPlayerDataService().setModuleData(player.getUniqueId(), "swaghub", data);
    }

    /** Applies {@code target}'s current vanish state against every other online player. */
    private void applyVanishState(Player target) {
        boolean vanished = isVanished(target);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }
            boolean shouldHide = vanished && !viewer.hasPermission(PERMISSION_SEE);
            plugin.getPlayerVisibilityCoordinator().setHidden(plugin, viewer, target, REASON, shouldHide);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        if (args.length == 0) {
            if (!sender.hasPermission(PERMISSION_SELF)) {
                plugin.getMessageUtil().send(sender, "no-permission");
                return true;
            }
            if (!(sender instanceof Player self)) {
                plugin.getMessageUtil().send(sender, "player-only-command");
                return true;
            }
            target = self;
        } else {
            if (!sender.hasPermission(PERMISSION_OTHERS)) {
                plugin.getMessageUtil().send(sender, "no-permission");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                plugin.getMessageUtil().send(sender, "player-not-found", Map.of("player", args[0]));
                return true;
            }
        }

        boolean nowVanished = !isVanished(target);
        setVanished(target, nowVanished);
        applyVanishState(target);
        plugin.getMessageUtil().send(target, nowVanished ? "vanish-enabled" : "vanish-disabled");
        if (!target.equals(sender)) {
            plugin.getMessageUtil().send(sender, nowVanished ? "vanish-enabled-other" : "vanish-disabled-other",
                    Map.of("player", target.getName()));
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        if (isVanished(joiner)) {
            event.joinMessage(null);
        }
        // The joiner's own restored state, applied against everyone already online.
        applyVanishState(joiner);
        // Every already-vanished online player, applied against the new joiner.
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(joiner)) {
                continue;
            }
            if (isVanished(other)) {
                boolean shouldHide = !joiner.hasPermission(PERMISSION_SEE);
                plugin.getPlayerVisibilityCoordinator().setHidden(plugin, joiner, other, REASON, shouldHide);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isVanished(player)) {
            event.quitMessage(null);
        }
        plugin.getPlayerVisibilityCoordinator().clearViewer(player.getUniqueId());
        plugin.getPlayerVisibilityCoordinator().clearTargetEverywhere(player.getUniqueId());
    }
}
