package com.SwagDev.SwagHub.modules.antiwdl;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

import java.util.Locale;
import java.util.Set;

/**
 * §5.1 Anti-WorldDownloader, deferred from §11 build step 2 into this step — see
 * DECISIONS.md Step 2's "Deferred to a later build step" note. Detects the
 * WorldDownloader mod registering its plugin-messaging channel and either kicks or
 * warns the player, per a config toggle — never both, never a silent no-op.
 *
 * <p><b>Event/channel verification (resolved ambiguity — live-verified against this
 * project's actually-resolved {@code paper-api-1.21.1-R0.1-SNAPSHOT.jar} via
 * {@code javap}, per §0's "jar wins" rule):</b> the real, current event is
 * {@link org.bukkit.event.player.PlayerRegisterChannelEvent} — a subclass of
 * {@code org.bukkit.event.player.PlayerChannelEvent} exposing {@code getChannel()} —
 * NOT {@code io.papermc.paper.event.player.PlayerChannelEvent} (no such Paper-specific
 * event class exists in this resolved jar; the plain Bukkit event under
 * {@code org.bukkit.event.player} is the correct, current one). Matches against the
 * modern namespaced channel {@code wdl:init} and the legacy (pre-1.13) channel names
 * {@code WDL|INIT}/{@code WDL|CONTROL}, case-insensitively.</p>
 */
public class AntiWdlModule extends Module implements Listener {

    private static final Set<String> WDL_CHANNELS = Set.of("wdl:init", "wdl|init", "wdl|control");

    private boolean kickMode;

    public AntiWdlModule(SwagHub plugin) {
        super(plugin, "anti-wdl");
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
        kickMode = "kick".equalsIgnoreCase(plugin.getConfig().getString("anti-wdl.action", "kick"));
    }

    @EventHandler
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        if (!WDL_CHANNELS.contains(event.getChannel().toLowerCase(Locale.ROOT))) {
            return;
        }

        Player player = event.getPlayer();
        if (kickMode) {
            player.kick(plugin.getMessageUtil().getMessage("wdl-kicked"));
        } else {
            plugin.getMessageUtil().send(player, "wdl-warned");
        }
    }
}
