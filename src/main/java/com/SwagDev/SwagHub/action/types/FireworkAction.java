package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code [firework] colors;type;flicker;trail} — spawns and immediately detonates a
 * firework at the acting player's current location (§5.9). SwagHub's own invented
 * argument syntax — §5.9 lists {@code [firework]} as a required tag but does not
 * specify its exact format:
 * <ul>
 *   <li>{@code colors} — comma-separated. Each entry is either one of
 *       {@code org.bukkit.Color}'s named constants (e.g. {@code RED}, {@code AQUA} —
 *       matched case-insensitively) or a {@code #RRGGBB} hex triplet. A single
 *       malformed color entry logs one warning naming it and is skipped — never
 *       aborts the whole action, matching §10.4. Defaults to a single {@code WHITE}
 *       color if the whole field is blank/all entries are malformed.</li>
 *   <li>{@code type} — one of {@code BALL}/{@code BALL_LARGE}/{@code STAR}/
 *       {@code BURST}/{@code CREEPER} (case-insensitive), matching
 *       {@link FireworkEffect.Type}'s exact constant names. Defaults to
 *       {@code BALL} on a missing/invalid value (silently — the visual shape is a
 *       minor cosmetic default, not worth a console warning on every terse usage
 *       that only specifies colors).</li>
 *   <li>{@code flicker}/{@code trail} — booleans (any value {@code Boolean.parseBoolean}
 *       accepts; anything else is treated as {@code false}). Both default to
 *       {@code false} when omitted.</li>
 * </ul>
 *
 * <p>Uses {@code FireworkEffect.builder()}/{@code FireworkMeta#addEffect(...)}/
 * {@code Firework#detonate()} — live-verified against this project's actually-resolved
 * paper-api jar via {@code javap} before writing this class (matches every prior build
 * step's "jar wins" verification discipline).</p>
 */
public class FireworkAction implements ActionType {

    private static final FireworkEffect.Type DEFAULT_TYPE = FireworkEffect.Type.BALL;

    private final SwagHub plugin;

    public FireworkAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "firework";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        String raw = context.applyPlaceholders(action.getArgument()).trim();
        String[] parts = raw.split(";", -1);

        String colorsField = parts.length > 0 ? parts[0].trim() : "";
        List<Color> colors = parseColors(colorsField, raw);

        FireworkEffect.Type type = DEFAULT_TYPE;
        if (parts.length > 1 && !parts[1].isBlank()) {
            try {
                type = FireworkEffect.Type.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                // Cosmetic-only default — silently fall back, mirroring [sound]'s numeric-tail handling.
                type = DEFAULT_TYPE;
            }
        }

        boolean flicker = parts.length > 2 && Boolean.parseBoolean(parts[2].trim());
        boolean trail = parts.length > 3 && Boolean.parseBoolean(parts[3].trim());

        FireworkEffect effect = FireworkEffect.builder()
                .with(type)
                .withColor(colors)
                .flicker(flicker)
                .trail(trail)
                .build();

        Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(effect);
        firework.setFireworkMeta(meta);
        firework.detonate();
    }

    private List<Color> parseColors(String colorsField, String rawAction) {
        List<Color> colors = new ArrayList<>();
        if (!colorsField.isBlank()) {
            for (String token : colorsField.split(",")) {
                String name = token.trim();
                if (name.isEmpty()) {
                    continue;
                }
                Color color = parseOneColor(name);
                if (color == null) {
                    plugin.getLogger().warning("A '[firework] " + rawAction + "' action has an invalid color '"
                            + name + "' — skipping just that color.");
                    continue;
                }
                colors.add(color);
            }
        }
        if (colors.isEmpty()) {
            colors.add(Color.WHITE);
        }
        return colors;
    }

    private Color parseOneColor(String name) {
        if (name.startsWith("#")) {
            try {
                int rgb = Integer.parseInt(name.substring(1), 16);
                return Color.fromRGB(rgb);
            } catch (Exception ex) {
                return null;
            }
        }
        try {
            var field = Color.class.getField(name.toUpperCase(Locale.ROOT));
            Object value = field.get(null);
            return value instanceof Color color ? color : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
