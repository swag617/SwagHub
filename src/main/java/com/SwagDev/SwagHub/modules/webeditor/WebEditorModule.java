package com.SwagDev.SwagHub.modules.webeditor;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.module.Module;

import java.util.Locale;

/**
 * §11 build step 9 / §7: registers SwagHub's web-based hub-options editor with
 * SwagAPI's shared {@code IWebService} at {@code /swagapi/swaghub/}.
 *
 * <p><b>SwagHub never runs its own HTTP server, opens its own port, or implements any
 * authentication of its own</b> (§7 — non-negotiable). Every request into {@link
 * SwagHubWebHandler} is already gated by SwagAPI's session-cookie login before it ever
 * reaches this plugin's code; see {@link SwagHubWebApiHandler} for the additional
 * {@code swaghub.dashboard.*} permission check this module's own API layer performs on
 * top of that (§7.2).</p>
 *
 * <p><b>Utility module — always enabled regardless of {@code server-role}</b> (§7.1),
 * matching {@code proxy}/{@code menus}/{@code launchpads}/{@code holograms}/{@code
 * portals}' precedent. Config key {@code "webeditor"}.</p>
 *
 * <p><b>Soft-unavailable, never fatal</b> (§7.1): if {@link SwagHub#getWebService()} is
 * {@code null} or {@link com.SwagDev.SwagAPI.api.IWebService#isRunning()} is {@code
 * false} (SwagAPI is a hard dependency per §6.4, so the service handle itself always
 * exists — but its underlying web server can still be administratively disabled in
 * SwagAPI's own config), this module logs exactly one info line and returns without
 * registering anything — never an error, never a fallback server. Per {@link
 * Module#enable()}'s contract, {@link #onEnable()} returning normally (not throwing)
 * still marks this module {@link #isEnabled()} {@code true} even in that soft-skip
 * case, mirroring how other modules with an optional runtime dependency (e.g. {@code
 * ProxyModule} continuing to report enabled even with zero players online to poll)
 * report their own enabled state as "the module's own lifecycle succeeded," not "every
 * optional integration is currently active."</p>
 */
public class WebEditorModule extends Module {

    private boolean registered = false;

    public WebEditorModule(SwagHub plugin) {
        super(plugin, "webeditor");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        var webService = plugin.getWebService();
        if (webService == null || !webService.isRunning()) {
            plugin.getLogger().info("SwagAPI's web server is not running — the SwagHub web editor is unavailable.");
            return;
        }

        webService.registerModule(plugin, new SwagHubWebHandler(plugin));
        registered = true;
        String moduleName = plugin.getName().toLowerCase(Locale.ROOT);
        plugin.getLogger().info("SwagHub web editor registered at " + webService.getPluginUrl(moduleName));
    }

    @Override
    protected void onDisable() {
        if (!registered) {
            return;
        }
        var webService = plugin.getWebService();
        if (webService != null) {
            // Silently does nothing if there's nothing registered / the web server is
            // down — safe to call unconditionally here (IWebService#unregisterModule's
            // own javadoc).
            webService.unregisterModule(plugin);
        }
        registered = false;
    }

    @Override
    protected void onReload() {
        // No-op: the web editor module itself has nothing of its own to reload — the
        // config.yml/scoreboard.yml/tablist.yml/announcements.yml state it reads and
        // writes is reloaded by ConfigManager/each owning module's own reload(), not by
        // this module. (If SwagAPI's web server later comes online after having been
        // down at startup, re-registering would require re-running onEnable()'s logic;
        // that's out of scope for a v1 reload — a full server restart or plugin reload
        // picks it up via onDisable()+onEnable() instead of a live onReload() re-check.)
    }

    /** Whether the web editor is actually mounted on SwagAPI's shared web server right now. */
    public boolean isRegistered() {
        return registered;
    }
}
