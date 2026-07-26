package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import org.bukkit.Bukkit;

/**
 * {@code [console] <command>} — runs a command as the console, e.g. {@code [console] give %player% diamond 1}.
 * {@code %key%} placeholders (including {@code %player%}, when a player triggered the action) are substituted first.
 */
public class ConsoleAction implements ActionType {

    private final SwagHub plugin;

    public ConsoleAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "console";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        String command = context.applyPlaceholders(action.getArgument());
        if (command.isBlank()) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
