package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * {@code [effect] PotionEffectTypeName} or
 * {@code [effect] PotionEffectTypeName;durationTicks;amplifier} — applies a potion
 * effect to the acting player (§5.9). SwagHub's own invented argument syntax, same
 * "significant name argument first, then optional numeric tuning with silent
 * fallbacks" shape as {@code [sound]}/{@code [particle]}. {@code durationTicks}
 * defaults to {@code 200} (10s); {@code amplifier} defaults to {@code 0} (level I,
 * matching vanilla's own 0-indexed amplifier convention).
 *
 * <p><b>Two live API corrections, both live-verified via {@code javap -v} against
 * this project's actually-resolved paper-api jar</b> (the same "jar wins" rule as
 * DECISIONS.md Step 4/5's {@code Enchantment}/{@code ChatColor} corrections):
 * {@code PotionEffectType.getByName(String)}/{@code getByKey(NamespacedKey)} are both
 * {@code @Deprecated(forRemoval = true)}. The first attempt at a fix — the
 * fuzzy-matching {@link Registry#EFFECT}{@code .match(String)} default method — turned
 * out to ALSO be {@code @Deprecated(forRemoval = true)} on this exact jar (not visible
 * via a plain {@code javap -p} signature listing, only caught once {@code mvn compile}
 * itself flagged it — {@code javap -v}'s bytecode-level annotation dump was needed to
 * confirm it before switching away). The genuinely modern, non-deprecated path — and
 * exactly what {@code match(String)}'s own deprecated implementation does internally,
 * confirmed by decompiling its bytecode — is building the key by hand:
 * {@code NamespacedKey.fromString(name)} (lower-cased, whitespace collapsed to
 * underscores) then the abstract, never-deprecated {@link Registry#get(NamespacedKey)}.
 * Used here instead.</p>
 */
public class EffectAction implements ActionType {

    private static final int DEFAULT_DURATION_TICKS = 200;
    private static final int DEFAULT_AMPLIFIER = 0;

    private final SwagHub plugin;

    public EffectAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "effect";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        String raw = context.applyPlaceholders(action.getArgument()).trim();
        if (raw.isBlank()) {
            plugin.getLogger().warning("An '[effect]' action ran with no potion effect type argument — skipping.");
            return;
        }

        String[] parts = raw.split(";");
        String typeName = parts[0].trim();

        String normalized = typeName.toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        NamespacedKey key = NamespacedKey.fromString(normalized);
        PotionEffectType type = key != null ? Registry.EFFECT.get(key) : null;
        if (type == null) {
            plugin.getLogger().warning("An '[effect] " + raw + "' action has an invalid potion effect type '"
                    + typeName + "' — skipping.");
            return;
        }

        int durationTicks = parseInt(parts, 1, DEFAULT_DURATION_TICKS);
        int amplifier = parseInt(parts, 2, DEFAULT_AMPLIFIER);

        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier));
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
}
