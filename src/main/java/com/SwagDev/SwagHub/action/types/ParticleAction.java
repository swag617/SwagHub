package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * {@code [particle] ParticleName} or
 * {@code [particle] ParticleName;count;offsetX;offsetY;offsetZ} — spawns a particle
 * effect at the acting player's current location (§5.9). This is SwagHub's own
 * invented argument syntax — §5.9 lists {@code [particle]} as a required tag but does
 * not specify its exact format, so this mirrors {@code [sound]}'s established
 * "significant name argument first, then a semicolon-separated tail of optional
 * numeric tuning values that silently fall back to sane defaults on a parse failure"
 * convention as closely as possible for a 4-number tail instead of a 2-number one.
 *
 * <p>{@code count} defaults to {@code 10}; {@code offsetX}/{@code offsetY}/
 * {@code offsetZ} (the per-axis random spread Bukkit's {@code spawnParticle} already
 * takes) each default to {@code 0.5}. A non-numeric count/offset silently falls back
 * to its own default (never warns — mirrors {@code SoundAction}'s volume/pitch
 * handling); only an unresolvable {@code ParticleName} itself logs a warning, since
 * that is the one argument with no sane fallback.</p>
 */
public class ParticleAction implements ActionType {

    private static final int DEFAULT_COUNT = 10;
    private static final double DEFAULT_OFFSET = 0.5;

    private final SwagHub plugin;

    public ParticleAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "particle";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        String raw = context.applyPlaceholders(action.getArgument()).trim();
        if (raw.isBlank()) {
            plugin.getLogger().warning("A '[particle]' action ran with no particle name argument — skipping.");
            return;
        }

        String[] parts = raw.split(";");
        String particleName = parts[0].trim();

        Particle particle;
        try {
            particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("A '[particle] " + raw + "' action has an invalid particle name '"
                    + particleName + "' — skipping.");
            return;
        }

        int count = parseInt(parts, 1, DEFAULT_COUNT);
        double offsetX = parseDouble(parts, 2, DEFAULT_OFFSET);
        double offsetY = parseDouble(parts, 3, DEFAULT_OFFSET);
        double offsetZ = parseDouble(parts, 4, DEFAULT_OFFSET);

        player.getWorld().spawnParticle(particle, player.getLocation(), count, offsetX, offsetY, offsetZ);
    }

    private int parseInt(String[] parts, int index, int fallback) {
        if (parts.length <= index) {
            return fallback;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double parseDouble(String[] parts, int index, double fallback) {
        if (parts.length <= index) {
            return fallback;
        }
        try {
            return Double.parseDouble(parts[index].trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
