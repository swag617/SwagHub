package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import com.SwagDev.SwagHub.modules.playerhider.PlayerHiderModule;
import org.bukkit.entity.Player;

/**
 * {@code [cycle-player-hider]} — advances the acting player's player-hider state by
 * one step (§5.7/§5.9). Takes no argument. This is how an {@code items.yml} entry
 * triggers the cycle — no bespoke item-giving mechanism, just a normal action on a
 * normal configured item, exactly like {@code [open-menu]} does for menus.
 *
 * <p>Mirrors {@code OpenMenuAction}'s disabled-module handling: if the player-hider
 * module is disabled, this logs one clear admin warning (never a silent no-op).</p>
 */
public class CyclePlayerHiderAction implements ActionType {

    private final SwagHub plugin;

    public CyclePlayerHiderAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "cycle-player-hider";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }

        PlayerHiderModule module = plugin.getPlayerHiderModule();
        if (module == null || !module.isEnabled()) {
            plugin.getLogger().warning("A '[cycle-player-hider]' action tried to cycle " + player.getName()
                    + "'s player-hider state, but the player-hider module is disabled (see 'modules.player-hider'"
                    + " in config.yml) — enable it to use this action.");
            return;
        }

        module.cycle(player);
    }
}
