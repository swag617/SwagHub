package com.SwagDev.SwagHub.modules.proxy;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * §11 build step 3 / §3 of the design doc: registers the {@code bungeecord:main}
 * ({@code "BungeeCord"} legacy channel name) outgoing channel, registers {@link
 * BungeeChannelProxyService} as the incoming {@link org.bukkit.plugin.messaging.PluginMessageListener},
 * and owns the repeating {@code PlayerCount}/{@code GetServers} poll task.
 *
 * <p><b>Utility module — defaults ON regardless of {@code server-role}</b> (§6.2
 * explicitly lists "proxy service" among the modules that stay enabled on both
 * {@code hub} and {@code game} roles, unlike {@code WorldProtectionModule}/{@code
 * JoinSettingsModule} from step 2). See {@link #isEnabledByDefault()}.</p>
 *
 * <p><b>Enable/disable toggle — see DECISIONS.md Step 3, ambiguity 1:</b> the design
 * doc's §3.3 names this toggle {@code proxy.enabled}. This module folds that into the
 * same {@code modules.<key>} mechanism every other module already uses (i.e. {@code
 * modules.proxy: true/false}, defaulting to {@link #isEnabledByDefault()} when absent)
 * rather than introducing a second, inconsistent per-module enable convention —
 * {@code config.yml}'s {@code proxy:} section documents this mapping explicitly.</p>
 */
public class ProxyModule extends Module {

    private static final String CHANNEL = "BungeeCord";

    private BungeeChannelProxyService service;
    private BukkitTask pollTask;

    private long pollIntervalTicks;
    private List<String> pollServers;

    public ProxyModule(SwagHub plugin) {
        super(plugin, "proxy");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        service = new BungeeChannelProxyService(plugin);
        readSettings();

        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, service);
        service.setEnabled(true);

        schedulePollTask();
    }

    @Override
    protected void onDisable() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        if (service != null) {
            service.setEnabled(false);
        }
        // Order matters: stop being a listener/sender before the last poll cycle's
        // in-flight callbacks could possibly fire — cancel() above already guarantees
        // no NEW poll cycle starts, and unregistering here guarantees no further
        // incoming message is delivered to a module that's shutting down.
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, service);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        // The service instance itself is intentionally kept (not nulled) so
        // SwagHub#getProxyService() never returns null once the module has enabled at
        // least once — isEnabled() already reports false, which is what every caller
        // (ServerAction, /ah proxy servers) actually checks.
    }

    @Override
    protected void onReload() {
        long oldIntervalTicks = pollIntervalTicks;
        readSettings();
        if (pollIntervalTicks != oldIntervalTicks && pollTask != null) {
            pollTask.cancel();
            schedulePollTask();
        }
    }

    private void schedulePollTask() {
        long intervalTicks = Math.max(20L, pollIntervalTicks);
        pollTask = Bukkit.getScheduler().runTaskTimer(plugin, this::poll, intervalTicks, intervalTicks);
    }

    private void readSettings() {
        long pollIntervalSeconds = Math.max(1L, plugin.getConfig().getLong("proxy.poll-interval-seconds", 10));
        pollIntervalTicks = pollIntervalSeconds * 20L;
        pollServers = plugin.getConfig().getStringList("proxy.servers");

        long connectTimeoutTicks = Math.max(0L, plugin.getConfig().getLong("proxy.connect-timeout-ticks", 40));
        if (service != null) {
            service.setConnectTimeoutTicks(connectTimeoutTicks);
        }
    }

    /**
     * One poll cycle: refresh the server list and every configured server's player
     * count (plus the network-wide total). Per §3.2's explicit protocol note, plugin
     * messages can only be sent through a currently-connected player's channel — if
     * nobody is online, the whole cycle is skipped silently (not logged as an error,
     * not retried early); the last-cached values keep being served via the getters on
     * {@link ProxyService} in the meantime.
     */
    private void poll() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        service.requestServerList(null);
        service.requestPlayerCount("ALL", null);
        for (String server : pollServers) {
            if (server != null && !server.isBlank()) {
                service.requestPlayerCount(server, null);
            }
        }
    }

    /** The public-facing {@link ProxyService} — consumed by {@code ServerAction}, {@code /ah proxy servers}, and later build steps (menus, holograms, scoreboard, PlaceholderAPI). */
    public ProxyService getService() {
        return service;
    }
}
