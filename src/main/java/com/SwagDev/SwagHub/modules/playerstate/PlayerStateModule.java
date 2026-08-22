package com.SwagDev.SwagHub.modules.playerstate;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.data.SwagHubPlayerData;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.util.HubWorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

/**
 * Fixes a real reported bug: an admin flying in {@link GameMode#CREATIVE} 100 blocks
 * up logs off, and {@link com.SwagDev.SwagHub.modules.join.JoinSettingsModule}'s
 * {@code onJoin} (running at {@link EventPriority#HIGH}, unconditionally, whenever
 * {@code join-settings.set-gamemode} is true — the default) forces them straight back
 * into the configured lobby gamemode (default {@link GameMode#ADVENTURE}, with flight
 * always off) on their very next join, with zero memory of what they were actually
 * doing when they quit. The result: they fall from wherever the void-fall/spawn logic
 * leaves them and can die before anyone reacts.
 *
 * <p>This module is a general safety net, not hub-specific — {@link
 * #isEnabledByDefault()} is always {@code true} regardless of {@code server-role},
 * matching {@code HologramModule}/{@code PortalModule}'s "utility module" precedent.
 * It captures the player's TRUE live state on quit ({@link #onQuit}, reading directly
 * off the {@link Player} object — {@link Player#getGameMode()}/{@link
 * Player#isFlying()}/{@link Player#getFlySpeed()} — never off whatever command last
 * touched them, so a plain vanilla creative switch, {@code /gamemode}, {@code /fly},
 * or any other plugin's own flight grant is captured identically) and offers to
 * restore it on the next join ({@link #onJoin}).</p>
 *
 * <p><b>Why restoration is bypass-permission-gated, not automatic for everyone:</b>
 * {@link com.SwagDev.SwagHub.modules.join.JoinSettingsModule}'s forced lobby default
 * is deliberate hub behavior — a regular player should always land back in the
 * lobby's safe, configured gamemode, every single time, with no way for leftover
 * state (accidentally left in creative by some other plugin, or by an admin testing
 * something) to survive a relog and violate that guarantee. Only a player holding
 * {@code swaghub.bypass.joingamemode} (default {@code op} — trusted staff) gets their
 * last gamemode and flight state restored instead of the forced default. Fly speed
 * restoration, by contrast, is NOT gated behind this permission (see {@link #onJoin}
 * below) — it's a harmless QoL nicety that has no effect on a player who doesn't
 * currently have flight, and no safety implication for one who does.</p>
 *
 * <p><b>Event priority — see {@link
 * com.SwagDev.SwagHub.modules.vanish.VanishModule}'s javadoc for the same reasoning
 * pattern applied here:</b> {@link #onJoin} runs at {@link EventPriority#MONITOR},
 * deliberately the LAST priority tier. Bukkit always runs every {@code MONITOR}
 * listener strictly after every {@code HIGH} listener regardless of plugin
 * registration order, so this module's restoration is GUARANTEED to run after {@code
 * JoinSettingsModule}'s {@code HIGH}-priority forced reset without needing any
 * cross-module registration-order dependency in {@link SwagHub#registerFeatureModules()}
 * — the exact ordering hazard called out in that method's own javadoc for other
 * module pairs is a non-issue here specifically because of this priority gap.</p>
 */
public class PlayerStateModule extends Module implements Listener {

    private static final String PERMISSION_BYPASS_JOIN_GAMEMODE = "swaghub.bypass.joingamemode";

    public PlayerStateModule(SwagHub plugin) {
        super(plugin, "player-state");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
    }

    @Override
    protected void onReload() {
        // No settings of its own — nothing to re-read.
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        SwagHubPlayerData.Data data = plugin.getPlayerDataService()
                .getModuleData(player.getUniqueId(), "swaghub", SwagHubPlayerData.Data.class);
        if (data == null) {
            data = new SwagHubPlayerData.Data();
        }
        // Read the TRUE live state directly off the Player object — see class javadoc
        // for why this must never be sourced from whatever command last touched them.
        data.lastGameMode = player.getGameMode().name();
        data.wasFlying = player.isFlying();
        data.flySpeed = player.getFlySpeed();
        plugin.getPlayerDataService().setModuleData(player.getUniqueId(), "swaghub", data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SwagHubPlayerData.Data data = plugin.getPlayerDataService()
                .getModuleData(player.getUniqueId(), "swaghub", SwagHubPlayerData.Data.class);
        if (data == null) {
            return;
        }

        // Fly speed: always restored, unconditionally, regardless of permission or
        // world — see class javadoc for why this one field is deliberately NOT gated
        // behind swaghub.bypass.joingamemode. Inert until the player actually has
        // flight, so this is never a safety-relevant bypass.
        player.setFlySpeed(data.flySpeed);

        // Gamemode + flight: only restored in a hub world, for a player holding the
        // bypass permission, and only if something was actually saved.
        if (!HubWorldUtil.isHubWorld(plugin, player.getWorld())) {
            return;
        }
        if (!player.hasPermission(PERMISSION_BYPASS_JOIN_GAMEMODE)) {
            return;
        }
        if (data.lastGameMode == null || data.lastGameMode.isBlank()) {
            return;
        }

        GameMode restored = parseGameMode(data.lastGameMode);
        if (restored == null) {
            return;
        }

        player.setGameMode(restored);
        if (data.wasFlying) {
            // setAllowFlight MUST precede setFlying, or Bukkit silently ignores the
            // setFlying(true) call — see JoinSettingsModule/GamemodeModule's own
            // handling of gamemode changes for why this order matters here too.
            player.setAllowFlight(true);
            player.setFlying(true);
        }
    }

    /**
     * Defensive parse of a stored {@link GameMode#name()} string — mirrors {@code
     * JoinSettingsModule#parseGameMode}'s own defensive pattern (log a warning and
     * skip restoring, rather than throwing, on a corrupt/renamed value).
     */
    private GameMode parseGameMode(String raw) {
        try {
            return GameMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Stored last-game-mode value '" + raw
                    + "' is not a valid GameMode — skipping join-state restoration for this player.");
            return null;
        }
    }
}
