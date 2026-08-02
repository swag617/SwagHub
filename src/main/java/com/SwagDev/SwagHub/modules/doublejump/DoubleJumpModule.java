package com.SwagDev.SwagHub.modules.doublejump;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.api.event.PlayerDoubleJumpEvent;
import com.SwagDev.SwagHub.bedrock.BedrockService;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.util.HubWorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * §5.7 Double jump: permission-gated ({@code swaghub.doublejump}, default {@code op}),
 * a one-shot velocity boost (never persistent creative-style flight) with a particle +
 * sound, triggered by the same client double-tap-space input vanilla flight uses.
 * Config-defined {@link DoubleJumpRegion}s (the exact {@code PvpZone}-shaped two-corner
 * cuboid pattern) disable the launch effect in specific areas.
 *
 * <p><b>Settings location (resolved ambiguity):</b> lives in its own {@code
 * double-jump:} section in config.yml, NOT its own file. Unlike launchpads/items/menus
 * (a variable-length LIST of many admin-authored entries, which is what earns a
 * dedicated file elsewhere in this codebase), double-jump's own settings are a bounded
 * handful of scalars (power/height/particle/sound) plus one small region list — the
 * exact same shape as {@code world-protection}'s 13 toggles + {@code pvp-zones} list,
 * which already lives directly in config.yml. Chat-controls (Step 6, see
 * {@code ChatControlsModule}) makes the identical choice for the identical reason —
 * both stay consistent with each other and with the world-protection precedent.</p>
 *
 * <p><b>The flight stand-down contract (§6.5 hazard 3 — shared, not code-shared, with
 * {@code FlyModule}):</b> {@link #grantedByUs} tracks ONLY the players THIS module
 * granted {@code allowFlight} to. The invariant that prevents double-jump and
 * {@code /fly} from fighting over the same underlying boolean is enforced
 * independently and identically by both modules, with no shared mutable state needed:
 * <b>only ever grant {@code allowFlight} when it currently reads {@code false}</b>
 * (never override a grant some other system — gamemode, {@code /fly}, another plugin —
 * already made), and <b>only ever revoke it for a player present in this module's OWN
 * tracked set</b>. Whichever module's grant-check runs first for a given player
 * "claims" the flag; the other correctly observes {@code allowFlight} is already
 * {@code true}, does not track that player, and therefore never later revokes
 * something it didn't grant. See DECISIONS.md Step 6 for the full reasoning.</p>
 *
 * <p><b>Region-gating is checked at launch time, not continuously (resolved
 * ambiguity):</b> {@link DoubleJumpRegion}s only gate the LAUNCH EFFECT itself,
 * evaluated the moment {@link PlayerToggleFlightEvent} fires — not the underlying
 * {@code allowFlight} grant, which is only re-evaluated on join/world-change/
 * gamemode-change/respawn. Continuously polling every online player's location against
 * every region on a per-tick/per-move basis would violate §4's "no per-tick work when
 * idle" performance rule; checking at the one instant that actually matters (the
 * player attempting to double-jump) achieves the same "no double jump inside this
 * region" behavior with zero movement-listener overhead. The toggle-flight event is
 * still cancelled either way inside a region (persistent flight never begins there
 * either), the boost/particle/sound are simply skipped.</p>
 */
public class DoubleJumpModule extends Module implements Listener {

    private static final String PERMISSION = "swaghub.doublejump";

    private final Set<java.util.UUID> grantedByUs = ConcurrentHashMap.newKeySet();

    private double power;
    private double height;
    private Particle particle;
    private Sound sound;
    private List<DoubleJumpRegion> regions = new ArrayList<>();
    private boolean bedrockEnabled;

    public DoubleJumpModule(SwagHub plugin) {
        super(plugin, "double-jump");
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        readSettings();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            reconcile(player);
        }
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            revokeIfOurs(player);
        }
        grantedByUs.clear();
    }

    @Override
    protected void onReload() {
        readSettings();
        for (Player player : Bukkit.getOnlinePlayers()) {
            reconcile(player);
        }
    }

    private void readSettings() {
        var cfg = plugin.getConfig();
        power = cfg.getDouble("double-jump.power", 1.4);
        height = cfg.getDouble("double-jump.height", 1.2);
        particle = parseParticle(cfg.getString("double-jump.particle", "CLOUD"));
        sound = parseSound(cfg.getString("double-jump.sound", "ENTITY_BAT_TAKEOFF"));
        // Patch 1, Fix 1 (§8.2): per-platform toggle — default true (unchanged current
        // behavior) so this only matters once an admin has actually observed unreliable
        // double-jump input from a Bedrock client and flips it off.
        bedrockEnabled = cfg.getBoolean("double-jump.bedrock", true);

        List<DoubleJumpRegion> parsed = new ArrayList<>();
        List<Map<?, ?>> rawRegions = cfg.getMapList("double-jump.regions");
        for (Map<?, ?> regionMap : rawRegions) {
            try {
                String name = String.valueOf(regionMap.get("name"));
                String world = String.valueOf(regionMap.get("world"));
                Map<?, ?> c1 = (Map<?, ?>) regionMap.get("corner1");
                Map<?, ?> c2 = (Map<?, ?>) regionMap.get("corner2");
                double x1 = toDouble(c1.get("x"));
                double y1 = toDouble(c1.get("y"));
                double z1 = toDouble(c1.get("z"));
                double x2 = toDouble(c2.get("x"));
                double y2 = toDouble(c2.get("y"));
                double z2 = toDouble(c2.get("z"));
                parsed.add(new DoubleJumpRegion(name, world, x1, y1, z1, x2, y2, z2));
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping malformed entry in double-jump.regions "
                        + "(expected name/world/corner1{x,y,z}/corner2{x,y,z}): " + regionMap);
            }
        }
        regions = parsed;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("Not a number: " + value);
    }

    private Particle parseParticle(String raw) {
        try {
            return Particle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            plugin.getLogger().warning("double-jump.particle '" + raw + "' is not a valid Particle — falling back to CLOUD.");
            return Particle.CLOUD;
        }
    }

    private Sound parseSound(String raw) {
        try {
            return Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            plugin.getLogger().warning("double-jump.sound '" + raw + "' is not a valid Sound — falling back to ENTITY_BAT_TAKEOFF.");
            return Sound.ENTITY_BAT_TAKEOFF;
        }
    }

    private boolean isInDisabledRegion(Location location) {
        for (DoubleJumpRegion region : regions) {
            if (region.contains(location)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Re-evaluates whether {@code player} should currently hold an
     * allowFlight-grant-for-double-jump, and grants/revokes accordingly — the ONE
     * place both the grant and revoke sides of the stand-down contract live, so they
     * can never drift out of sync with each other.
     */
    private void reconcile(Player player) {
        boolean qualifies = isEnabled()
                && player.hasPermission(PERMISSION)
                && HubWorldUtil.isHubWorld(plugin, player.getWorld())
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                && (bedrockEnabled || !isBedrockPlayer(player));

        if (qualifies) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                grantedByUs.add(player.getUniqueId());
            }
            // else: allowFlight is already true but WE didn't just set it — something
            // else (gamemode, /fly, another plugin) already owns it; stand down and
            // never add this player to our tracked set.
        } else if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            // These gamemodes inherently need/grant flight themselves (same exemption
            // FlyModule's own toggle() already makes) — just stop tracking any grant we
            // previously held, without touching allowFlight. Revoking here would strip
            // flight capability the gamemode itself just turned on, a moment after it did
            // so: e.g. a player with our earlier double-jump grant active switches to
            // spectator — its inherent noclip stays (that's the gamemode, not allowFlight)
            // but revokeIfOurs() below would rip actual flight back off right after the
            // gamemode granted it, leaving them able to fall through blocks but not fly.
            grantedByUs.remove(player.getUniqueId());
        } else {
            revokeIfOurs(player);
        }
    }

    /**
     * Public accessor for {@code %swaghub_doublejump_enabled%} (§5.11, build step 8):
     * whether {@code player} currently holds an {@code allowFlight} grant THIS module
     * gave them (never true for a grant some other system — gamemode, {@code /fly},
     * another plugin — owns; see the flight stand-down contract in this class's own
     * javadoc).
     */
    public boolean hasGrantedFlight(Player player) {
        return player != null && grantedByUs.contains(player.getUniqueId());
    }

    /**
     * Patch 1, Fix 1 (§8.2) — null-safe {@link BedrockService} check, consulted only
     * when {@link #bedrockEnabled} is {@code false} (the common case never touches
     * this at all). {@link SwagHub#getBedrockService()} is only {@code null} before
     * {@link SwagHub#finishStartup(long)} has run, which never happens for a real
     * {@link #reconcile(Player)} call (see that method's own javadoc).
     */
    private boolean isBedrockPlayer(Player player) {
        BedrockService service = plugin.getBedrockService();
        return service != null && service.isBedrockPlayer(player.getUniqueId());
    }

    private void revokeIfOurs(Player player) {
        if (grantedByUs.remove(player.getUniqueId()) && player.getAllowFlight()) {
            player.setAllowFlight(false);
        }
    }

    // ─── Listeners ───

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!grantedByUs.contains(player.getUniqueId())) {
            return; // not a flight grant we own — stand down completely, touch nothing
        }
        if (!event.isFlying()) {
            return; // becoming not-flying; nothing for us to intercept
        }

        event.setCancelled(true); // always prevent persistent flight from beginning

        if (isInDisabledRegion(player.getLocation())) {
            return; // inside a disabled region — flight stays cancelled, but no boost either
        }

        // §5.11 developer API: fired right before the boost/particle/sound actually
        // apply — see PlayerDoubleJumpEvent's own javadoc for why the persistent-
        // flight cancellation above is NEVER conditional on this event's outcome.
        PlayerDoubleJumpEvent doubleJumpEvent = new PlayerDoubleJumpEvent(player);
        Bukkit.getPluginManager().callEvent(doubleJumpEvent);
        if (doubleJumpEvent.isCancelled()) {
            return; // boost skipped — persistent flight stays cancelled regardless (see above)
        }

        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() > 0.0001) {
            direction.normalize();
        }
        Vector velocity = direction.multiply(power);
        velocity.setY(height);
        player.setVelocity(velocity);

        player.getWorld().spawnParticle(particle, player.getLocation(), 20, 0.3, 0.1, 0.3, 0.02);
        player.getWorld().playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reconcile(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                reconcile(event.getPlayer());
            }
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        reconcile(event.getPlayer());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        // The new gamemode isn't applied yet at event-fire time — reconcile next tick
        // against the player's (by-then-updated) real gamemode rather than trusting
        // event.getNewGameMode() plus assumptions about ordering with other plugins.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                reconcile(event.getPlayer());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // No setAllowFlight call needed for an offline player — just stop tracking them.
        grantedByUs.remove(event.getPlayer().getUniqueId());
    }
}
