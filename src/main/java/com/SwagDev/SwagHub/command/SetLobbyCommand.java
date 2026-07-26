package com.SwagDev.SwagHub.command;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.modules.spawn.SpawnModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /setlobby} — stores the sender's current position as the lobby/spawn
 * location via {@link SpawnModule#getStore()}, which writes it to disk immediately
 * (§4's setlobby-restart fix). Console cannot run this — there's no location to
 * capture from a non-player sender.
 *
 * <p>Registered/unregistered by {@link SpawnModule#onEnable()}/{@code onDisable()}
 * — never touches {@code plugin.getCommand(...)} itself, so it stays a fully
 * reversible piece of the module's lifecycle rather than a standalone command.</p>
 */
public class SetLobbyCommand implements CommandExecutor {

    private final SwagHub plugin;
    private final SpawnModule module;

    public SetLobbyCommand(SwagHub plugin, SpawnModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("swaghub.command.setlobby")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessageUtil().send(sender, "player-only-command");
            return true;
        }

        module.getStore().set(player.getLocation());
        plugin.getMessageUtil().send(player, "setlobby-set");
        return true;
    }
}
