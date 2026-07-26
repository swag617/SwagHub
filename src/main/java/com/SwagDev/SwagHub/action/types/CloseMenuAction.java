package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import org.bukkit.entity.Player;

/**
 * {@code [close-menu]} — closes the acting player's currently-open inventory (§5.9).
 * Takes no argument. A plain {@code player.closeInventory()} is deliberately used
 * rather than checking the open inventory's holder first: closing whatever the
 * player currently has open (a SwagHub menu or anything else) is the expected,
 * unsurprising behavior of a "close whatever's open" action, and
 * {@code closeInventory()} is already a no-op if nothing is open.
 */
public class CloseMenuAction implements ActionType {

    private final SwagHub plugin;

    public CloseMenuAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "close-menu";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        player.closeInventory();
    }
}
