package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import org.bukkit.entity.Player;

/**
 * {@code [message] <MiniMessage text>} — sends a chat message to the acting player.
 * {@code %key%} placeholders from the {@link ActionContext} are substituted before parsing.
 */
public class MessageAction implements ActionType {

    private final SwagHub plugin;

    public MessageAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "message";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        String text = context.applyPlaceholders(action.getArgument());
        player.sendMessage(plugin.getMessageUtil().parse(text));
    }
}
