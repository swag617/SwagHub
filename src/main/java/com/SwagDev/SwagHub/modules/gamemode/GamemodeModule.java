package com.SwagDev.SwagHub.modules.gamemode;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * §5.10 {@code /gamemode} (+ aliases {@code gmc}/{@code gms}/{@code gma}/
 * {@code gmsp}) — module config key {@code gamemode} is already compat-reserved
 * (yields to SwagCore/EssentialsX). {@code swaghub.command.gamemode} (self, default
 * {@code op}), {@code swaghub.command.gamemode.others} (target another player,
 * default {@code op}).
 *
 * <p>Bukkit routes every alias to this SAME {@link #onCommand} — the {@code label}
 * parameter is the exact string the sender actually typed (the command name or
 * whichever alias), so the target mode is inferred from it when invoked via an
 * alias; invoked as the literal {@code /gamemode}, an explicit mode argument is
 * required. Supports full mode names and the short forms {@code s}/{@code c}/
 * {@code a}/{@code sp} (case-insensitive), for parity with vanilla's own
 * {@code /gamemode} short-form support.</p>
 */
public class GamemodeModule extends Module implements CommandExecutor {

    private static final String PERMISSION_SELF = "swaghub.command.gamemode";
    private static final String PERMISSION_OTHERS = "swaghub.command.gamemode.others";

    public GamemodeModule(SwagHub plugin) {
        super(plugin, "gamemode");
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        PluginCommand command = plugin.getCommand("gamemode");
        if (command != null) {
            command.setExecutor(this);
        } else {
            plugin.getLogger().warning("Could not find the 'gamemode' command — check plugin.yml.");
        }
    }

    @Override
    protected void onDisable() {
        PluginCommand command = plugin.getCommand("gamemode");
        if (command != null) {
            command.setExecutor((sender, cmd, label, args) -> {
                plugin.getMessageUtil().send(sender, "module-disabled");
                return true;
            });
        }
    }

    @Override
    protected void onReload() {
        // No settings of its own — nothing to re-read.
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String lowerLabel = label.toLowerCase(Locale.ROOT);

        GameMode mode;
        String[] remainingArgsForTarget;

        if (lowerLabel.equals("gamemode")) {
            if (args.length < 1) {
                sender.sendMessage(plugin.getMessageUtil().parse(
                        "<red>Usage: </red><yellow>/gamemode <survival|creative|adventure|spectator> [player]</yellow>"));
                return true;
            }
            GameMode parsed = parseMode(args[0]);
            if (parsed == null) {
                sender.sendMessage(plugin.getMessageUtil().parse(
                        "<red>Unknown game mode '</red><yellow>" + args[0] + "</yellow><red>'.</red>"));
                return true;
            }
            mode = parsed;
            remainingArgsForTarget = args.length > 1 ? new String[] {args[1]} : new String[0];
        } else {
            mode = aliasToMode(lowerLabel);
            if (mode == null) {
                return true; // unreachable given plugin.yml's alias list, but defensive
            }
            remainingArgsForTarget = args.length > 0 ? new String[] {args[0]} : new String[0];
        }

        Player target;
        if (remainingArgsForTarget.length == 0) {
            if (!sender.hasPermission(PERMISSION_SELF)) {
                plugin.getMessageUtil().send(sender, "no-permission");
                return true;
            }
            if (!(sender instanceof Player self)) {
                plugin.getMessageUtil().send(sender, "player-only-command");
                return true;
            }
            target = self;
        } else {
            if (!sender.hasPermission(PERMISSION_OTHERS)) {
                plugin.getMessageUtil().send(sender, "no-permission");
                return true;
            }
            target = Bukkit.getPlayerExact(remainingArgsForTarget[0]);
            if (target == null) {
                plugin.getMessageUtil().send(sender, "player-not-found", Map.of("player", remainingArgsForTarget[0]));
                return true;
            }
        }

        target.setGameMode(mode);
        plugin.getMessageUtil().send(target, "gamemode-changed", Map.of("gamemode", prettify(mode)));
        if (!target.equals(sender)) {
            plugin.getMessageUtil().send(sender, "gamemode-changed-other",
                    Map.of("gamemode", prettify(mode), "player", target.getName()));
        }
        return true;
    }

    private GameMode aliasToMode(String label) {
        return switch (label) {
            case "gmc" -> GameMode.CREATIVE;
            case "gms" -> GameMode.SURVIVAL;
            case "gma" -> GameMode.ADVENTURE;
            case "gmsp" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private GameMode parseMode(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "survival", "s" -> GameMode.SURVIVAL;
            case "creative", "c" -> GameMode.CREATIVE;
            case "adventure", "a" -> GameMode.ADVENTURE;
            case "spectator", "sp" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private String prettify(GameMode mode) {
        String name = mode.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
