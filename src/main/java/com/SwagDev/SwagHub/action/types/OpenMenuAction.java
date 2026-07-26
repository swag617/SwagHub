package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import com.SwagDev.SwagHub.modules.menu.MenuModule;
import org.bukkit.entity.Player;

/**
 * {@code [open-menu] <menu-id>} — opens a SwagHub selector menu for the acting
 * player (§5.3/§5.9). This is what lets menus be openable "from items, other menus,
 * and commands" (§5.3): a join item's actions, another menu's slot actions, and
 * {@code /ah open} all funnel through the exact same {@link MenuModule#openMenu}
 * entry point — permission checks and the "menu not found" message live there once,
 * not duplicated per caller.
 *
 * <p>Mirrors {@code ServerAction}'s disabled-module handling exactly: if the menu
 * module is disabled, this logs one clear admin warning (never a silent no-op, never
 * a player-facing stack trace) instead of attempting to open anything.</p>
 */
public class OpenMenuAction implements ActionType {

    private final SwagHub plugin;

    public OpenMenuAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "open-menu";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        String menuId = context.applyPlaceholders(action.getArgument()).trim();
        if (menuId.isBlank()) {
            plugin.getLogger().warning("An '[open-menu]' action ran with no menu id argument — skipping.");
            return;
        }

        MenuModule module = plugin.getMenuModule();
        if (module == null || !module.isEnabled()) {
            plugin.getLogger().warning("An '[open-menu] " + menuId + "' action tried to open a menu for "
                    + player.getName() + ", but the menu module is disabled (see 'modules.menus' in config.yml)"
                    + " — enable it to use [open-menu] actions.");
            return;
        }

        module.openMenu(player, menuId);
    }
}
