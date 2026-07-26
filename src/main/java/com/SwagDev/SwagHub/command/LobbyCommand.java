package com.SwagDev.SwagHub.command;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.modules.spawn.SpawnModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /lobby} — teleports the sender to the stored lobby location, honoring the
 * configured teleport delay + move-cancel (see
 * {@link SpawnModule#beginLobbyTeleport(Player)}). Deliberately a distinct command
 * from {@code /spawn} per §6.5 point 5 ("do not alias /lobby to /spawn") — {@code
 * /spawn} is not implemented in this build step at all.
 *
 * <p>Registered/unregistered by {@link SpawnModule#onEnable()}/{@code onDisable()}.</p>
 */
public class LobbyCommand implements CommandExecutor {

    private final SwagHub plugin;
    private final SpawnModule module;

    public LobbyCommand(SwagHub plugin, SpawnModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("swaghub.command.lobby")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessageUtil().send(sender, "player-only-command");
            return true;
        }

        module.beginLobbyTeleport(player);
        return true;
    }
}
