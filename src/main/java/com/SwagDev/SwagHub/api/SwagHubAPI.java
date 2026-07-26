package com.SwagDev.SwagHub.api;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.modules.menu.MenuConfig;

/**
 * §5.11's developer API facade: "expose {@code SwagHubAPI} for registering custom
 * action types and menus programmatically." A plain facade class (not an
 * interface+impl split — this codebase doesn't use that pattern for any other
 * facade, see {@code MessageUtil}/{@code ConfigManager} etc.), constructor-injected
 * with the plugin instance like every other manager here (§4's "no static abuse"
 * rule).
 *
 * <p>Obtain via {@link SwagHub#getAPI()}. Constructed once, eagerly, in {@code
 * SwagHub#onEnable()} after {@code actionRegistry}/{@code menuModule} both exist.</p>
 *
 * <p><b>Delegation only — no duplicated validation:</b> both {@link
 * #registerActionType(ActionType)} and {@link #registerMenu(MenuConfig)} forward
 * straight to the class that already owns the real behavior ({@code ActionRegistry}/
 * {@code MenuModule}) rather than re-implementing any of their guard logic here.</p>
 */
public final class SwagHubAPI {

    private final SwagHub plugin;

    public SwagHubAPI(SwagHub plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a custom {@link ActionType}, making {@code [<tag>]} usable anywhere
     * SwagHub's action system is used (items, menus, announcements, portals, ...).
     * Delegates to {@link com.SwagDev.SwagHub.action.ActionRegistry#register
     * (ActionType)} — registering an already-registered tag logs a warning there and
     * overwrites with the newest registration; this method does not duplicate that
     * check.
     */
    public void registerActionType(ActionType type) {
        plugin.getActionRegistry().register(type);
    }

    /**
     * Unregisters a previously-registered action type by tag — for a third-party
     * plugin to call from ITS OWN {@code onDisable()}, so no dangling {@link
     * ActionType} reference (holding a reference to the now-disabled plugin) is left
     * registered in {@link com.SwagDev.SwagHub.action.ActionRegistry}. Delegates to
     * {@code ActionRegistry#unregister(String)}.
     */
    public void unregisterActionType(String tag) {
        plugin.getActionRegistry().unregister(tag);
    }

    /**
     * Registers a menu programmatically — usable from {@code /ah open}, the
     * {@code [open-menu]} action, and tab-completion exactly like a
     * {@code selector-menus/*.yml}-defined menu. Delegates to {@link
     * com.SwagDev.SwagHub.modules.menu.MenuModule#registerMenu(MenuConfig)} — see
     * that method's javadoc for the file-wins-on-collision rule and the
     * survives-{@code /ah reload} design. Returns {@code false} if the registration
     * was rejected (null/blank id, or an id collision with a file-based menu).
     */
    public boolean registerMenu(MenuConfig menu) {
        return plugin.getMenuModule().registerMenu(menu);
    }

    /**
     * Unregisters a programmatically-registered menu by id — for a third-party
     * plugin to call from ITS OWN {@code onDisable()}. Never touches a file-based
     * menu of the same id. Delegates to {@link
     * com.SwagDev.SwagHub.modules.menu.MenuModule#unregisterMenu(String)}.
     */
    public boolean unregisterMenu(String id) {
        return plugin.getMenuModule().unregisterMenu(id);
    }
}
