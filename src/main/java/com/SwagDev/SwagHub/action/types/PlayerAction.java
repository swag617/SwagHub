package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * {@code [player] <command>} — runs a command as the acting player (no leading slash),
 * e.g. {@code [player] spawn}. {@code %key%} placeholders are substituted first.
 */
public class PlayerAction implements ActionType {

    private final SwagHub plugin;

    public PlayerAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "player";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        String command = context.applyPlaceholders(action.getArgument());
        if (command.isBlank()) {
            return;
        }
        Bukkit.dispatchCommand(player, command);
    }
}
