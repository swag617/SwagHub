package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import com.SwagDev.SwagHub.modules.proxy.ProxyService;
import org.bukkit.entity.Player;

/**
 * {@code [server] <name>} — proxy-connects the acting player to the named backend
 * server via {@link ProxyService#connect(Player, String)} (§3.3/§5.9). {@code %key%}
 * placeholders are substituted first, so a menu/announcement could parameterize the
 * target server (e.g. {@code [server] %target_server%}) once a later build step needs it.
 *
 * <p>Usable immediately from anywhere the action system already runs (currently only
 * join-settings' {@code first-join.actions} from step 2) even though the menus/items/
 * announcements that will also use it don't exist until later build steps — this is
 * the ActionRegistry extension point working exactly as designed.</p>
 *
 * <p><b>§3.3's "selectors show a helpful admin warning when a [server] action is used"
 * requirement:</b> there's no selector-menu module yet, so this action type itself is
 * the one thing that logs the warning when {@code proxy.enabled}/{@code modules.proxy}
 * is off — a single clear console line, never a silent no-op, per this build step's
 * explicit instructions.</p>
 */
public class ServerAction implements ActionType {

    private final SwagHub plugin;

    public ServerAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "server";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        String serverName = context.applyPlaceholders(action.getArgument()).trim();
        if (serverName.isBlank()) {
            plugin.getLogger().warning("A '[server]' action ran with no server name argument — skipping.");
            return;
        }

        ProxyService service = plugin.getProxyService();
        if (service == null || !service.isEnabled()) {
            plugin.getLogger().warning("A '[server] " + serverName + "' action tried to send " + player.getName()
                    + " to a backend server, but the proxy module is disabled (see 'modules.proxy'/'proxy:' in"
                    + " config.yml) — enable it to use server-connect actions.");
            return;
        }

        service.connect(player, serverName);
    }
}
