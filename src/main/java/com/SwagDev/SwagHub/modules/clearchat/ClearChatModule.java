package com.SwagDev.SwagHub.modules.clearchat;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.module.Module;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

/**
 * §5.10 {@code /clearchat} — a SEPARATE module from {@link
 * com.SwagDev.SwagHub.modules.chat.ChatControlsModule} on purpose: its config key
 * {@code clearchat} is already compat-reserved (yields to EssentialsX per §6.3's
 * shipped registry), which lockchat/cooldown/command-blocker deliberately are NOT —
 * merging them would force clearchat's independent yield behavior onto features that
 * must never yield.
 *
 * <p><b>Default-by-role (resolved ambiguity):</b> §6.2 explicitly names "chat lock/
 * cooldown" in the hub-behavior default-OFF-on-{@code game} list but is silent on
 * {@code /clearchat} specifically. Made {@code clearchat} default hub-only too — the
 * same choice {@code ChatControlsModule} makes — so the whole chat feature bundle
 * behaves coherently by server role even though it's split across two modules for
 * yield-independence reasons; a plain game server presumably already has its own
 * chat-clearing tool if it wants one (EssentialsX's own {@code /clearchat}, which
 * this module's own auto-yield already defers to when EssentialsX is present).</p>
 *
 * <p><b>Clears for everyone online, not just the sender (resolved ambiguity):</b>
 * matches how {@code /clearchat} behaves in essentially every hub plugin this design
 * is modeled on — a sender-only clear would be a fairly unusual, niche mode. A config
 * toggle ({@code clearchat.clear-for-everyone}) is provided for the sender-only
 * alternative rather than hard-coding the more common choice.</p>
 */
public class ClearChatModule extends Module implements CommandExecutor {

    private static final String PERMISSION = "swaghub.command.clearchat";

    private int lineCount;
    private boolean clearForEveryone;

    public ClearChatModule(SwagHub plugin) {
        super(plugin, "clearchat");
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        readSettings();
        PluginCommand command = plugin.getCommand("clearchat");
        if (command != null) {
            command.setExecutor(this);
        } else {
            plugin.getLogger().warning("Could not find the 'clearchat' command — check plugin.yml.");
        }
    }

    @Override
    protected void onDisable() {
        PluginCommand command = plugin.getCommand("clearchat");
        if (command != null) {
            command.setExecutor((sender, cmd, label, args) -> {
                plugin.getMessageUtil().send(sender, "module-disabled");
                return true;
            });
        }
    }

    @Override
    protected void onReload() {
        readSettings();
    }

    private void readSettings() {
        lineCount = Math.max(1, plugin.getConfig().getInt("clearchat.lines", 100));
        clearForEveryone = plugin.getConfig().getBoolean("clearchat.clear-for-everyone", true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }

        if (clearForEveryone) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                clear(player);
            }
            Bukkit.getConsoleSender().sendMessage(plugin.getMessageUtil().parse(
                    "<gray>Chat was cleared by </gray><white>" + sender.getName() + "</white><gray>.</gray>"));
        } else if (sender instanceof Player player) {
            clear(player);
        } else {
            clear(sender);
        }

        return true;
    }

    private void clear(CommandSender target) {
        for (int i = 0; i < lineCount; i++) {
            target.sendMessage(Component.empty());
        }
    }
}
