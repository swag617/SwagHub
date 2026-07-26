package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * {@code [teleport] world;x;y;z} or {@code [teleport] world;x;y;z;yaw;pitch} —
 * teleports the acting player to an absolute location (§5.7/§5.9). {@code yaw}/
 * {@code pitch} are always both-or-neither: omit both to keep the player's own
 * current look direction; a genuinely malformed/incomplete pair (present but
 * non-numeric, or only one of the two given) logs one warning and falls back to the
 * player's current look direction too, rather than aborting the teleport entirely —
 * matches {@code TitleAction}'s "the location/position half of the argument is what
 * actually matters; a broken timing/orientation tail degrades gracefully" philosophy.
 *
 * <p>A fully missing/malformed {@code world;x;y;z} (world doesn't resolve to a
 * currently-loaded world, or x/y/z aren't numeric) is genuinely malformed — logs one
 * warning naming the argument and skips the whole action, since there's no sane
 * partial fallback for "where" the way there is for "which way you're facing".</p>
 */
public class TeleportAction implements ActionType {

    private final SwagHub plugin;

    public TeleportAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "teleport";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        String raw = context.applyPlaceholders(action.getArgument()).trim();
        if (raw.isBlank()) {
            plugin.getLogger().warning("A '[teleport]' action ran with no argument (expected 'world;x;y;z[;yaw;pitch]') — skipping.");
            return;
        }

        String[] parts = raw.split(";", -1);
        if (parts.length < 4) {
            plugin.getLogger().warning("A '[teleport] " + raw + "' action is missing required fields"
                    + " (expected 'world;x;y;z[;yaw;pitch]') — skipping.");
            return;
        }

        String worldName = parts[0].trim();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("A '[teleport] " + raw + "' action references world '" + worldName
                    + "', which is not currently loaded — skipping.");
            return;
        }

        double x, y, z;
        try {
            x = Double.parseDouble(parts[1].trim());
            y = Double.parseDouble(parts[2].trim());
            z = Double.parseDouble(parts[3].trim());
        } catch (NumberFormatException ex) {
            plugin.getLogger().warning("A '[teleport] " + raw + "' action has non-numeric x/y/z coordinates — skipping.");
            return;
        }

        float yaw = player.getLocation().getYaw();
        float pitch = player.getLocation().getPitch();
        if (parts.length >= 6) {
            try {
                yaw = Float.parseFloat(parts[4].trim());
                pitch = Float.parseFloat(parts[5].trim());
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("A '[teleport] " + raw + "' action has a non-numeric yaw/pitch"
                        + " — keeping your current look direction instead.");
            }
        } else if (parts.length == 5) {
            plugin.getLogger().warning("A '[teleport] " + raw + "' action has an incomplete yaw/pitch pair"
                    + " (both or neither are required) — keeping your current look direction instead.");
        }
        // parts.length == 4: yaw/pitch intentionally omitted — silently keep current look direction.

        player.teleport(new Location(world, x, y, z, yaw, pitch));
    }
}
