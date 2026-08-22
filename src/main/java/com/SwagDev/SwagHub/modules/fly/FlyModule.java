package com.SwagDev.SwagHub.modules.fly;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.data.SwagHubPlayerData;
import com.SwagDev.SwagHub.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * §5.10 {@code /fly [player]} — toggles {@code allowFlight}+{@code flying}.
 * {@code swaghub.command.fly} (self, default {@code op}), {@code
 * swaghub.command.fly.others} (target another player, default {@code op}). Module
 * config key {@code fly} is already compat-reserved (yields to SwagCore/EssentialsX
 * per §6.3's shipped registry) — top-level command, matching {@code /setlobby}/
 * {@code /lobby}'s standalone-command precedent rather than an {@code /ah}
 * subcommand, per §5.10's own command list keeping it separate.
 *
 * <p><b>The flight stand-down contract — see {@code DoubleJumpModule}'s javadoc for
 * the full shared-invariant reasoning (no shared mutable state, just an identical
 * discipline both modules independently follow):</b> {@code /fly} only ever GRANTS
 * {@code allowFlight} when it currently reads {@code false} — if it's already
 * {@code true} (granted by double-jump, creative/spectator gamemode, or another
 * plugin) and this module didn't grant it, {@code /fly} refuses to also claim
 * ownership (sends {@code fly-already-enabled-elsewhere} instead of toggling) so it
 * can never later call {@code setAllowFlight(false)} on a grant it didn't make. It
 * only ever REVOKES for a player present in {@link #grantedByUs}. Explicitly refuses
 * to act at all while the target is in {@link GameMode#CREATIVE}/
 * {@link GameMode#SPECTATOR} — those gamemodes inherently grant flight, and
 * {@code /fly off} forcibly revoking {@code allowFlight} out from under a creative
 * player would leave them unable to fly despite still being in creative mode.</p>
 *
 * <p><b>No world-exit auto-disable (resolved ambiguity):</b> unlike double-jump
 * (whose grant is automatically driven by hub-world entry/exit), {@code /fly} is an
 * explicit admin/player-invoked toggle with no world-scoping semantics anywhere in
 * §5.10's text — flight stays on until explicitly toggled off again, matching how
 * EssentialsX's own {@code /fly} behaves (and when EssentialsX is present, this whole
 * module yields to it anyway per the compat registry).</p>
 *
 * <p><b>Patch 2 addition — {@code /flyspeed <1-10> [player]}:</b> lives in this
 * module rather than a new one, since {@code FlyModule} already owns flight for the
 * hub. {@code swaghub.command.flyspeed} (self, default {@code op}), {@code
 * swaghub.command.flyspeed.others} (target another player, default {@code op}) —
 * same self/others argument-parsing shape as {@code /fly} itself, just with the
 * required speed argument occupying the slot {@code /fly}'s optional player argument
 * does not need. The 1-10 integer maps onto Bukkit's {@code setFlySpeed} float range
 * via {@code speed / 10.0f} (1 = vanilla's own default {@code 0.1}, 10 = Bukkit's max
 * {@code 1.0}). The resulting speed is ALSO written into {@link
 * SwagHubPlayerData.Data#flySpeed} (the same field {@code PlayerStateModule} restores
 * on join — see that module's javadoc) so a chosen fly speed survives a relog exactly
 * like a chosen gamemode does, without needing {@code swaghub.bypass.joingamemode} —
 * fly speed persistence has no safety implication, unlike gamemode/flight restoration.</p>
 */
public class FlyModule extends Module implements Listener, CommandExecutor {

    private static final String PERMISSION_SELF = "swaghub.command.fly";
    private static final String PERMISSION_OTHERS = "swaghub.command.fly.others";
    private static final String PERMISSION_FLYSPEED_SELF = "swaghub.command.flyspeed";
    private static final String PERMISSION_FLYSPEED_OTHERS = "swaghub.command.flyspeed.others";

    private final Set<UUID> grantedByUs = ConcurrentHashMap.newKeySet();
    private CommandExecutor disabledFallback;

    public FlyModule(SwagHub plugin) {
        super(plugin, "fly");
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PluginCommand command = plugin.getCommand("fly");
        if (command != null) {
            command.setExecutor(this);
        } else {
            plugin.getLogger().warning("Could not find the 'fly' command — check plugin.yml.");
        }
        PluginCommand flySpeedCommand = plugin.getCommand("flyspeed");
        if (flySpeedCommand != null) {
            flySpeedCommand.setExecutor(this);
        } else {
            plugin.getLogger().warning("Could not find the 'flyspeed' command — check plugin.yml.");
        }
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        for (UUID uuid : grantedByUs) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
        grantedByUs.clear();

        disabledFallback = (sender, command, label, args) -> {
            plugin.getMessageUtil().send(sender, "module-disabled");
            return true;
        };
        PluginCommand command = plugin.getCommand("fly");
        if (command != null) {
            command.setExecutor(disabledFallback);
        }
        PluginCommand flySpeedCommand = plugin.getCommand("flyspeed");
        if (flySpeedCommand != null) {
            flySpeedCommand.setExecutor(disabledFallback);
        }
    }

    @Override
    protected void onReload() {
        // No settings of its own beyond the permission checks — nothing to re-read.
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("flyspeed")) {
            return onFlySpeedCommand(sender, args);
        }

        Player target;
        if (args.length == 0) {
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
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                plugin.getMessageUtil().send(sender, "player-not-found", Map.of("player", args[0]));
                return true;
            }
        }

        toggle(sender, target);
        return true;
    }

    /**
     * Public accessor for {@code %swaghub_fly_enabled%} (§5.11, build step 8):
     * whether {@code player} currently holds a flight grant THIS module gave them —
     * mirrors {@code DoubleJumpModule#hasGrantedFlight}, see that method's javadoc
     * for the shared stand-down-contract reasoning.
     */
    public boolean hasGrantedFlight(Player player) {
        return player != null && grantedByUs.contains(player.getUniqueId());
    }

    private void toggle(CommandSender invoker, Player target) {
        if (target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) {
            plugin.getMessageUtil().send(invoker, "fly-already-enabled-elsewhere");
            return;
        }

        boolean isOn = grantedByUs.contains(target.getUniqueId());
        if (isOn) {
            grantedByUs.remove(target.getUniqueId());
            target.setFlying(false);
            target.setAllowFlight(false);
            plugin.getMessageUtil().send(target, "fly-disabled");
        } else {
            if (target.getAllowFlight()) {
                // Already flight-enabled by something we don't own (double-jump, another
                // plugin) — refuse to also claim ownership; see class javadoc.
                plugin.getMessageUtil().send(invoker, "fly-already-enabled-elsewhere");
                return;
            }
            target.setAllowFlight(true);
            target.setFlying(true);
            grantedByUs.add(target.getUniqueId());
            plugin.getMessageUtil().send(target, "fly-enabled");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        grantedByUs.remove(event.getPlayer().getUniqueId());
    }

    /**
     * {@code /flyspeed <1-10> [player]} — see class javadoc for the self/others
     * argument-parsing shape and the {@code speed / 10.0f} mapping. Same "no args
     * needs PERMISSION_SELF + must be a Player" / "target arg present needs
     * PERMISSION_OTHERS + Bukkit.getPlayerExact lookup, player-not-found message"
     * shape as {@link #onCommand}'s own {@code /fly} handling, adapted for the extra
     * required speed argument: {@code args.length == 1} is self (arg 0 is the
     * speed), {@code args.length == 2} is others (arg 0 is still the speed, arg 1 is
     * the target) — the speed argument always occupies slot 0 regardless of whether
     * a target follows it.
     */
    private boolean onFlySpeedCommand(CommandSender sender, String[] args) {
        if (args.length != 1 && args.length != 2) {
            plugin.getMessageUtil().send(sender, "flyspeed-usage");
            return true;
        }

        Player target;
        if (args.length == 1) {
            if (!sender.hasPermission(PERMISSION_FLYSPEED_SELF)) {
                plugin.getMessageUtil().send(sender, "no-permission");
                return true;
            }
            if (!(sender instanceof Player self)) {
                plugin.getMessageUtil().send(sender, "player-only-command");
                return true;
            }
            target = self;
        } else {
            if (!sender.hasPermission(PERMISSION_FLYSPEED_OTHERS)) {
                plugin.getMessageUtil().send(sender, "no-permission");
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.getMessageUtil().send(sender, "player-not-found", Map.of("player", args[1]));
                return true;
            }
        }

        int level;
        try {
            level = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            plugin.getMessageUtil().send(sender, "flyspeed-usage");
            return true;
        }
        if (level < 1 || level > 10) {
            plugin.getMessageUtil().send(sender, "flyspeed-usage");
            return true;
        }

        float flySpeed = level / 10.0f;
        target.setFlySpeed(flySpeed);
        persistFlySpeed(target, flySpeed);

        plugin.getMessageUtil().send(target, "flyspeed-changed", Map.of("speed", String.valueOf(level)));
        if (!target.equals(sender)) {
            plugin.getMessageUtil().send(sender, "flyspeed-changed-other",
                    Map.of("speed", String.valueOf(level), "player", target.getName()));
        }
        return true;
    }

    /**
     * Writes {@code flySpeed} into {@link SwagHubPlayerData.Data#flySpeed} — the same
     * field {@code PlayerStateModule} restores on join (see class javadoc's Patch 2
     * note) — via the same {@code getModuleData}/{@code setModuleData} accessor
     * pattern {@code VanishModule} uses for its own persisted field.
     */
    private void persistFlySpeed(Player target, float flySpeed) {
        SwagHubPlayerData.Data data = plugin.getPlayerDataService()
                .getModuleData(target.getUniqueId(), "swaghub", SwagHubPlayerData.Data.class);
        if (data == null) {
            data = new SwagHubPlayerData.Data();
        }
        data.flySpeed = flySpeed;
        plugin.getPlayerDataService().setModuleData(target.getUniqueId(), "swaghub", data);
    }
}
