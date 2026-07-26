package com.SwagDev.SwagHub.command;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.modules.hologram.HologramModule;
import com.SwagDev.SwagHub.modules.menu.MenuModule;
import com.SwagDev.SwagHub.modules.portal.PortalModule;
import com.SwagDev.SwagHub.modules.proxy.ProxyService;
import com.SwagDev.SwagHub.modules.scoreboard.ScoreboardModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code /swaghub} (alias {@code /ah}) — root command executor.
 *
 * <p>Step 1 scope: only {@code reload} and {@code info} exist. Step 3 scope adds
 * {@code proxy servers} (§3.2/§5.10's admin diagnostic). Step 4 scope adds
 * {@code open <menu> [player]} (§5.3/§5.10). Step 5 scope adds
 * {@code scoreboard} (§5.4/§5.10 — the player-facing scoreboard toggle). Step 7 scope
 * adds {@code hologram <create|delete|addline|setline|removeline|movehere|list>}
 * (§5.8/§5.10) and {@code portal <wand|create|delete|list>} (§3.3/§5.8/§5.10) — no
 * further feature subcommands remain in §5.10's list.</p>
 */
public class SwagHubCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("reload", "info", "proxy", "open", "scoreboard", "hologram", "portal");
    private static final List<String> PROXY_SUBCOMMANDS = List.of("servers");
    private static final List<String> HOLOGRAM_SUBCOMMANDS =
            List.of("create", "delete", "addline", "setline", "removeline", "movehere", "list");
    private static final List<String> PORTAL_SUBCOMMANDS = List.of("wand", "create", "delete", "list");

    private final SwagHub plugin;

    public SwagHubCommand(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendInfo(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "info" -> sendInfo(sender);
            case "proxy" -> handleProxy(sender, args);
            case "open" -> handleOpen(sender, args);
            case "scoreboard" -> handleScoreboard(sender);
            case "hologram" -> handleHologram(sender, args);
            case "portal" -> handlePortal(sender, args);
            default -> plugin.getMessageUtil().send(sender, "unknown-subcommand");
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("swaghub.command.reload")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return;
        }

        plugin.getConfigManager().reload();
        plugin.getMessageUtil().load();
        plugin.getCompatibilityManager().load();
        plugin.getModuleManager().reloadAll();

        plugin.getMessageUtil().send(sender, "reload-complete");
    }

    private void sendInfo(CommandSender sender) {
        if (!sender.hasPermission("swaghub.command.info")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return;
        }

        sender.sendMessage(plugin.getMessageUtil().parse(
                "<gradient:#7b2ff7:#f107a3>SwagHub</gradient> <gray>v" + plugin.getPluginMeta().getVersion() + "</gray>"));
        sender.sendMessage(plugin.getMessageUtil().parse(
                "<gray>Server role:</gray> <white>" + plugin.getCompatibilityManager().getServerRole() + "</white>"));
        sender.sendMessage(plugin.getMessageUtil().parse(
                "<gray>Modules registered:</gray> <white>" + plugin.getModuleManager().getModules().size() + "</white>"));

        Map<String, String> yielded = plugin.getCompatibilityManager().getYieldedModules();
        if (yielded.isEmpty()) {
            sender.sendMessage(plugin.getMessageUtil().parse("<gray>Yielded modules:</gray> <white>none</white>"));
        } else {
            sender.sendMessage(plugin.getMessageUtil().parse("<gray>Yielded modules:</gray>"));
            for (Map.Entry<String, String> entry : yielded.entrySet()) {
                sender.sendMessage(plugin.getMessageUtil().parse(
                        "<gray> - " + entry.getKey() + " -> </gray><yellow>" + entry.getValue() + "</yellow>"));
            }
        }

        sendUpdateNoticeIfAvailable(sender);
    }

    /**
     * §6.4's {@code IUpdateService} paragraph — build step 8's one deliberate
     * addition on top of "no code needed" (see DECISIONS.md Step 8): a single extra
     * line in {@code /ah info} when an update is actually pending, and nothing at all
     * otherwise (no "you're up to date" spam, matching this command's existing
     * terse style). {@code AdminJoinUpdateListener} (in SwagAPI) already handles the
     * proactive on-join notification for every plugin, SwagHub included, with zero
     * code on this side — this is purely an on-demand supplement for {@code /ah info}.
     */
    private void sendUpdateNoticeIfAvailable(CommandSender sender) {
        if (plugin.getUpdateService() == null || !plugin.getUpdateService().isEnabled()) {
            return;
        }
        plugin.getUpdateService().getUpdateInfo("SwagHub").ifPresent(info -> {
            if (info.isUpdateAvailable()) {
                sender.sendMessage(plugin.getMessageUtil().parse(
                        "<gray>Update available:</gray> <yellow>" + info.latestVersion()
                                + "</yellow> <gray>(current " + info.currentVersion() + ")</gray>"));
            }
        });
    }

    /**
     * {@code /ah proxy servers} — §3.2/§5.10's admin diagnostic: fires a {@code
     * GetServers} query and prints the proxy-reported server list (with each
     * server's last-known/just-refreshed player count) to {@code sender}. The query
     * is inherently asynchronous (the proxy's response arrives a moment later on the
     * incoming plugin channel, there is no way to block the main thread waiting for
     * it) — this sends an immediate "querying" acknowledgement, then a second
     * message once {@link ProxyService#requestServerList} invokes the callback.
     */
    private void handleProxy(CommandSender sender, String[] args) {
        if (!sender.hasPermission("swaghub.command.proxy")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("servers")) {
            sender.sendMessage(plugin.getMessageUtil().parse("<red>Usage: </red><yellow>/ah proxy servers</yellow>"));
            return;
        }

        ProxyService service = plugin.getProxyService();
        if (service == null || !service.isEnabled()) {
            plugin.getMessageUtil().send(sender, "proxy-module-disabled");
            return;
        }

        sender.sendMessage(plugin.getMessageUtil().parse("<gray>Querying the proxy for its server list...</gray>"));
        service.requestServerList(servers -> {
            if (servers.isEmpty()) {
                sender.sendMessage(plugin.getMessageUtil().parse(
                        "<yellow>The proxy reported no servers (or hasn't responded yet — try again in a moment).</yellow>"));
                return;
            }
            sender.sendMessage(plugin.getMessageUtil().parse("<gray>Proxy-reported servers:</gray>"));
            for (String server : servers) {
                int count = service.getCachedCount(server);
                sender.sendMessage(plugin.getMessageUtil().parse(
                        "<gray> - </gray><white>" + server + "</white> <gray>(</gray><yellow>" + count + "</yellow><gray> online)</gray>"));
            }
        });
    }

    /**
     * {@code /ah open <menu> [player]} — opens {@code <menu>} for the sender, or for
     * {@code [player]} if given (§5.3/§5.10). Targeting a different player is its own,
     * separately-permissioned admin action ({@code swaghub.command.open.others}) —
     * the menu's own {@code swaghub.menu.<id>} permission is still checked against
     * whichever player actually ends up viewing it, inside {@link MenuModule#openMenu}.
     */
    private void handleOpen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("swaghub.command.open")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageUtil().parse("<red>Usage: </red><yellow>/ah open <menu> [player]</yellow>"));
            return;
        }

        MenuModule module = plugin.getMenuModule();
        if (module == null || !module.isEnabled()) {
            plugin.getMessageUtil().send(sender, "menus-module-disabled");
            return;
        }

        String menuId = args[1];

        if (args.length >= 3) {
            if (!sender.hasPermission("swaghub.command.open.others")) {
                plugin.getMessageUtil().send(sender, "no-permission");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.getMessageUtil().send(sender, "player-not-found", Map.of("player", args[2]));
                return;
            }
            module.openMenu(target, menuId);
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessageUtil().send(sender, "player-only-command");
            return;
        }
        module.openMenu(player, menuId);
    }

    /**
     * {@code /ah scoreboard} — toggles the sender's own scoreboard visibility (§5.4/
     * §5.10), persisted via {@code SwagHubPlayerData}'s {@code scoreboardEnabled} flag
     * and applied immediately (no need to wait for the scoreboard module's own next
     * render tick). Player-only — mirrors every other player-facing toggle command in
     * this codebase ({@code /lobby}, {@code /ah open}) in requiring a real {@link Player}.
     */
    private void handleScoreboard(CommandSender sender) {
        if (!sender.hasPermission("swaghub.command.scoreboard")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessageUtil().send(sender, "player-only-command");
            return;
        }

        ScoreboardModule module = plugin.getScoreboardModule();
        if (module == null || !module.isEnabled()) {
            plugin.getMessageUtil().send(sender, "scoreboard-module-disabled");
            return;
        }

        boolean nowEnabled = module.toggleForPlayer(player);
        plugin.getMessageUtil().send(player, nowEnabled ? "scoreboard-toggled-on" : "scoreboard-toggled-off");
    }

    /**
     * {@code /ah hologram <create|delete|addline|setline|removeline|movehere|list>}
     * (§5.8/§5.10). A single {@code swaghub.command.hologram} permission node gates
     * the whole subtree, mirroring {@code /ah proxy}'s one-node-for-the-whole-diagnostic
     * precedent. {@code create}/{@code movehere} need a real location and are
     * player-only; {@code delete}/{@code addline}/{@code setline}/{@code removeline}/
     * {@code list} are console-usable.
     */
    private void handleHologram(CommandSender sender, String[] args) {
        if (!sender.hasPermission("swaghub.command.hologram")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return;
        }

        HologramModule module = plugin.getHologramModule();
        if (module == null || !module.isEnabled()) {
            plugin.getMessageUtil().send(sender, "holograms-module-disabled");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageUtil().parse(
                    "<red>Usage: </red><yellow>/ah hologram <create|delete|addline|setline|removeline|movehere|list></yellow>"));
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMessageUtil().parse("<red>Usage: </red><yellow>/ah hologram create <id></yellow>"));
                    return;
                }
                if (!(sender instanceof Player player)) {
                    plugin.getMessageUtil().send(sender, "player-only-command");
                    return;
                }
                String id = args[2];
                if (module.exists(id)) {
                    plugin.getMessageUtil().send(sender, "hologram-already-exists", Map.of("id", id));
                    return;
                }
                module.create(id, player.getLocation(), "<gray>New hologram — edit with /ah hologram setline</gray>");
                plugin.getMessageUtil().send(sender, "hologram-created", Map.of("id", id));
            }
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMessageUtil().parse("<red>Usage: </red><yellow>/ah hologram delete <id></yellow>"));
                    return;
                }
                String id = args[2];
                if (!module.delete(id)) {
                    plugin.getMessageUtil().send(sender, "hologram-not-found", Map.of("id", id));
                    return;
                }
                plugin.getMessageUtil().send(sender, "hologram-deleted", Map.of("id", id));
            }
            case "addline" -> {
                if (args.length < 4) {
                    sender.sendMessage(plugin.getMessageUtil().parse("<red>Usage: </red><yellow>/ah hologram addline <id> <text...></yellow>"));
                    return;
                }
                String id = args[2];
                String text = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                if (!module.addLine(id, text)) {
                    plugin.getMessageUtil().send(sender, "hologram-not-found", Map.of("id", id));
                    return;
                }
                plugin.getMessageUtil().send(sender, "hologram-line-added", Map.of("id", id));
            }
            case "setline" -> {
                if (args.length < 5) {
                    sender.sendMessage(plugin.getMessageUtil().parse("<red>Usage: </red><yellow>/ah hologram setline <id> <index> <text...></yellow>"));
                    return;
                }
                String id = args[2];
                int index = parseIndex(sender, args[3]);
                if (index < 0) {
                    return;
                }
                String text = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
                HologramModule.MutationResult result = module.setLine(id, index, text);
                reportMutation(sender, id, result);
            }
            case "removeline" -> {
                if (args.length < 4) {
                    sender.sendMessage(plugin.getMessageUtil().parse("<red>Usage: </red><yellow>/ah hologram removeline <id> <index></yellow>"));
                    return;
                }
                String id = args[2];
                int index = parseIndex(sender, args[3]);
                if (index < 0) {
                    return;
                }
                HologramModule.MutationResult result = module.removeLine(id, index);
                reportMutation(sender, id, result);
            }
            case "movehere" -> {
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMessageUtil().parse("<red>Usage: </red><yellow>/ah hologram movehere <id></yellow>"));
                    return;
                }
                if (!(sender instanceof Player player)) {
                    plugin.getMessageUtil().send(sender, "player-only-command");
                    return;
                }
                String id = args[2];
                if (!module.moveHere(id, player.getLocation())) {
                    plugin.getMessageUtil().send(sender, "hologram-not-found", Map.of("id", id));
                    return;
                }
                plugin.getMessageUtil().send(sender, "hologram-moved", Map.of("id", id));
            }
            case "list" -> {
                if (module.getHolograms().isEmpty()) {
                    plugin.getMessageUtil().send(sender, "hologram-list-empty");
                    return;
                }
                plugin.getMessageUtil().send(sender, "hologram-list-header");
                for (String id : module.getHolograms().keySet()) {
                    sender.sendMessage(plugin.getMessageUtil().parse("<gray> - </gray><white>" + id + "</white>"));
                }
            }
            default -> sender.sendMessage(plugin.getMessageUtil().parse(
                    "<red>Usage: </red><yellow>/ah hologram <create|delete|addline|setline|removeline|movehere|list></yellow>"));
        }
    }

    private int parseIndex(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            sender.sendMessage(plugin.getMessageUtil().parse("<red>'" + raw + "' is not a valid line index.</red>"));
            return -1;
        }
    }

    private void reportMutation(CommandSender sender, String id, HologramModule.MutationResult result) {
        switch (result) {
            case SUCCESS -> plugin.getMessageUtil().send(sender, "hologram-line-updated", Map.of("id", id));
            case NOT_FOUND -> plugin.getMessageUtil().send(sender, "hologram-not-found", Map.of("id", id));
            case INVALID_INDEX -> plugin.getMessageUtil().send(sender, "hologram-invalid-index");
            case LAST_LINE -> plugin.getMessageUtil().send(sender, "hologram-cannot-remove-last-line");
        }
    }

    /**
     * {@code /ah portal <wand|create|delete|list>} (§3.3/§5.8/§5.10). A single
     * {@code swaghub.command.portal} permission node gates the whole subtree, same as
     * {@code /ah hologram}'s single-node precedent. {@code wand}/{@code create} are
     * player-only (a wand is a held item; {@code create} reads the sender's own
     * in-memory selection); {@code delete}/{@code list} are console-usable.
     */
    private void handlePortal(CommandSender sender, String[] args) {
        if (!sender.hasPermission("swaghub.command.portal")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return;
        }

        PortalModule module = plugin.getPortalModule();
        if (module == null || !module.isEnabled()) {
            plugin.getMessageUtil().send(sender, "portals-module-disabled");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageUtil().parse("<red>Usage: </red><yellow>/ah portal <wand|create|delete|list></yellow>"));
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "wand" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessageUtil().send(sender, "player-only-command");
                    return;
                }
                ItemStack wand = module.createWandItem();
                player.getInventory().addItem(wand);
                plugin.getMessageUtil().send(player, "portal-wand-given");
            }
            case "create" -> {
                if (args.length < 4) {
                    sender.sendMessage(plugin.getMessageUtil().parse("<red>Usage: </red><yellow>/ah portal create <id> <server></yellow>"));
                    return;
                }
                if (!(sender instanceof Player player)) {
                    plugin.getMessageUtil().send(sender, "player-only-command");
                    return;
                }
                String id = args[2];
                String server = args[3];
                PortalModule.CreateResult result = module.create(player, id, server);
                switch (result) {
                    case SUCCESS -> plugin.getMessageUtil().send(player, "portal-created", Map.of("id", id, "server", server));
                    case ALREADY_EXISTS -> plugin.getMessageUtil().send(player, "portal-already-exists", Map.of("id", id));
                    case NO_SELECTION -> plugin.getMessageUtil().send(player, "portal-no-selection");
                    case DIFFERENT_WORLDS -> plugin.getMessageUtil().send(player, "portal-different-worlds");
                }
            }
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMessageUtil().parse("<red>Usage: </red><yellow>/ah portal delete <id></yellow>"));
                    return;
                }
                String id = args[2];
                if (!module.delete(id)) {
                    plugin.getMessageUtil().send(sender, "portal-not-found", Map.of("id", id));
                    return;
                }
                plugin.getMessageUtil().send(sender, "portal-deleted", Map.of("id", id));
            }
            case "list" -> {
                if (module.getPortals().isEmpty()) {
                    plugin.getMessageUtil().send(sender, "portal-list-empty");
                    return;
                }
                plugin.getMessageUtil().send(sender, "portal-list-header");
                module.getPortals().forEach((id, region) -> sender.sendMessage(plugin.getMessageUtil().parse(
                        "<gray> - </gray><white>" + id + "</white> <gray>-></gray> <yellow>" + region.getServerName() + "</yellow>")));
            }
            default -> sender.sendMessage(plugin.getMessageUtil().parse(
                    "<red>Usage: </red><yellow>/ah portal <wand|create|delete|list></yellow>"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("proxy")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return PROXY_SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            MenuModule module = plugin.getMenuModule();
            if (module == null) {
                return new ArrayList<>();
            }
            return module.getMenus().keySet().stream()
                    .filter(id -> id.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("open")) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("hologram")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return HOLOGRAM_SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("hologram")
                && List.of("delete", "addline", "setline", "removeline", "movehere").contains(args[1].toLowerCase(Locale.ROOT))) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            HologramModule module = plugin.getHologramModule();
            if (module == null) {
                return new ArrayList<>();
            }
            return module.getHolograms().keySet().stream()
                    .filter(id -> id.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("portal")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return PORTAL_SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("portal") && args[1].equalsIgnoreCase("delete")) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            PortalModule module = plugin.getPortalModule();
            if (module == null) {
                return new ArrayList<>();
            }
            return module.getPortals().keySet().stream()
                    .filter(id -> id.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Stream.<String>empty().collect(Collectors.toList());
    }
}
