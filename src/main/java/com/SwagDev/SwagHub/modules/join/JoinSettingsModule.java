package com.SwagDev.SwagHub.modules.join;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.util.HubWorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * §5.1 join settings: clear inventory, set gamemode, heal/feed, join firework, and
 * first-join extras (a separate first-join message key plus optional extra actions
 * run through the existing {@link com.SwagDev.SwagHub.action.ActionParser}/
 * {@link com.SwagDev.SwagHub.action.ActionRegistry} from build step 1 — no parallel
 * action runner is built here).
 *
 * <p>Runs at {@link EventPriority#HIGH} on {@link PlayerJoinEvent}, deliberately
 * later than {@link com.SwagDev.SwagHub.modules.spawn.SpawnModule}'s
 * {@link EventPriority#NORMAL} spawn-on-join handler, so join settings are always
 * evaluated against the player's FINAL (post-teleport) world — see DECISIONS.md
 * Step 2 for why this cross-module ordering matters.</p>
 *
 * <p><b>Module-boundary decision:</b> defaults enabled only when
 * {@code server-role: hub} — §6.2 lists "join items"/"clear-inventory" in the
 * hub-behavior set that defaults OFF on {@code server-role: game}.</p>
 */
public class JoinSettingsModule extends Module implements Listener {

    private boolean clearInventory;
    private boolean setGamemode;
    private GameMode gamemode;
    private boolean healAndFeed;
    private boolean joinFirework;
    private List<String> firstJoinActions;

    public JoinSettingsModule(SwagHub plugin) {
        super(plugin, "join-settings");
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        readSettings();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
    }

    @Override
    protected void onReload() {
        readSettings();
    }

    private void readSettings() {
        var cfg = plugin.getConfig();
        clearInventory = cfg.getBoolean("join-settings.clear-inventory", true);
        setGamemode = cfg.getBoolean("join-settings.set-gamemode", true);
        gamemode = parseGameMode(cfg.getString("join-settings.gamemode", "ADVENTURE"));
        healAndFeed = cfg.getBoolean("join-settings.heal-and-feed", true);
        joinFirework = cfg.getBoolean("join-settings.join-firework", true);
        firstJoinActions = cfg.getStringList("join-settings.first-join.actions");
    }

    private GameMode parseGameMode(String raw) {
        if (raw == null) {
            return GameMode.ADVENTURE;
        }
        try {
            return GameMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid join-settings.gamemode value '" + raw + "' — falling back to ADVENTURE.");
            return GameMode.ADVENTURE;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!HubWorldUtil.isHubWorld(plugin, player.getWorld())) {
            return;
        }

        if (clearInventory) {
            player.getInventory().clear();
        }
        if (setGamemode) {
            player.setGameMode(gamemode);
        }
        if (healAndFeed) {
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
        if (joinFirework) {
            launchJoinFirework(player);
        }

        if (!player.hasPlayedBefore()) {
            handleFirstJoin(player);
        }
    }

    private void launchJoinFirework(Player player) {
        try {
            Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.AQUA, Color.PURPLE)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .trail(true)
                    .flicker(true)
                    .build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
            // Detonate immediately rather than waiting out the fuse — a join
            // firework should feel instant, not like a delayed grenade.
            firework.detonate();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to launch join firework for " + player.getName(), ex);
        }
    }

    private void handleFirstJoin(Player player) {
        plugin.getMessageUtil().send(player, "first-join-message");
        if (firstJoinActions == null || firstJoinActions.isEmpty()) {
            return;
        }
        ActionContext context = new ActionContext(plugin, player);
        plugin.getActionParser().execute(firstJoinActions, context);
    }
}
