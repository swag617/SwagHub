package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * {@code [sound] SOUND_NAME} or {@code [sound] SOUND_NAME;volume;pitch} — plays a
 * sound to the acting player at their own location (§5.6/§5.9). {@code volume}/
 * {@code pitch} default to {@code 1.0} each when omitted or non-numeric — silently,
 * mirroring {@code ActionParser}'s own private {@code parseLong}/{@code parseDouble}
 * helpers, which never warn on a malformed numeric argument either (only the sound
 * NAME itself is significant enough to warn about, per this action's own explicit
 * instructions).
 *
 * <p>Resolves {@code SOUND_NAME} via {@code Sound.valueOf(...)} (uppercased) — the
 * same {@link IllegalArgumentException}-catching pattern {@code MenuModule}'s
 * {@code open-sound} parsing already uses.</p>
 */
public class SoundAction implements ActionType {

    private final SwagHub plugin;

    public SoundAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "sound";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        String raw = context.applyPlaceholders(action.getArgument()).trim();
        if (raw.isBlank()) {
            plugin.getLogger().warning("A '[sound]' action ran with no sound name argument — skipping.");
            return;
        }

        String[] parts = raw.split(";");
        String soundName = parts[0].trim();
        float volume = parseFloat(parts, 1, 1.0f);
        float pitch = parseFloat(parts, 2, 1.0f);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("A '[sound] " + raw + "' action has an invalid sound name '"
                    + soundName + "' — skipping.");
        }
    }

    private float parseFloat(String[] parts, int index, float fallback) {
        if (parts.length <= index) {
            return fallback;
        }
        try {
            return Float.parseFloat(parts[index].trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
