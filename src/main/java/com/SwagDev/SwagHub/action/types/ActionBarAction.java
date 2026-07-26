package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import org.bukkit.entity.Player;

/**
 * {@code [actionbar] <MiniMessage text>} — sends an action bar message to the acting
 * player (§5.6/§5.9), via {@code Player#sendActionBar(Component)} (an
 * {@code Audience} default method — confirmed present on Adventure 4.17.0, this
 * project's actually-resolved {@code adventure-api} version, see DECISIONS.md Step 5).
 * {@code %key%} placeholders are substituted before MiniMessage parsing, mirroring
 * {@code MessageAction} exactly.
 */
public class ActionBarAction implements ActionType {

    private final SwagHub plugin;

    public ActionBarAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "actionbar";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        String text = context.applyPlaceholders(action.getArgument());
        player.sendActionBar(plugin.getMessageUtil().parse(text));
    }
}
