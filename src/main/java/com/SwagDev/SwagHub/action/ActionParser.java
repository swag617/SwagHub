package com.SwagDev.SwagHub.action;

import com.SwagDev.SwagHub.SwagHub;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a raw config string list into {@link ParsedAction}s and executes them in
 * order against an {@link ActionRegistry} (§5.9 — the glue every configurable
 * "do something" in SwagHub goes through).
 *
 * <p>Handles three structural tags itself, since they control sequencing/guarding
 * rather than doing a single action:</p>
 * <ul>
 *   <li>{@code [delay] <ticks>} — pauses the rest of the sequence via the Bukkit
 *       scheduler before continuing (never blocks the calling thread);</li>
 *   <li>{@code [permission-check] <permission>} — if the acting player lacks the
 *       permission, every remaining action in the sequence is skipped;</li>
 *   <li>{@code [chance] <percent>} — rolls a 0-100 chance; on failure, every
 *       remaining action in the sequence is skipped.</li>
 * </ul>
 * Every other tag is looked up in the {@link ActionRegistry}. An unregistered tag
 * (e.g. one documented for a later build step, like {@code [server]}) logs one
 * warning and is skipped — it never aborts the rest of the sequence, matching the
 * "never abort the whole module for one bad entry" error-handling rule.
 */
public class ActionParser {

    private static final Pattern TAG_PATTERN = Pattern.compile("^\\[(?<tag>[a-zA-Z0-9-]+)]\\s*(?<arg>.*)$", Pattern.DOTALL);

    private final SwagHub plugin;
    private final ActionRegistry registry;

    public ActionParser(SwagHub plugin, ActionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    /** Parses raw config lines into {@link ParsedAction}s. Blank lines are skipped; malformed lines log a warning and are skipped. */
    public List<ParsedAction> parse(List<String> rawLines) {
        List<ParsedAction> parsed = new ArrayList<>();
        if (rawLines == null) {
            return parsed;
        }
        for (String line : rawLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Matcher matcher = TAG_PATTERN.matcher(line.trim());
            if (!matcher.matches()) {
                plugin.getLogger().warning("Could not parse action line (expected a leading \"[tag]\"): \"" + line + "\"");
                continue;
            }
            String tag = matcher.group("tag").toLowerCase(Locale.ROOT);
            String argument = matcher.group("arg").trim();
            parsed.add(new ParsedAction(tag, argument));
        }
        return parsed;
    }

    /** Parses and immediately executes {@code rawLines} against {@code context}. */
    public void execute(List<String> rawLines, ActionContext context) {
        executeParsed(parse(rawLines), context, 0);
    }

    /** Executes an already-parsed action list against {@code context}, starting from the first action. */
    public void executeParsed(List<ParsedAction> actions, ActionContext context) {
        executeParsed(actions, context, 0);
    }

    private void executeParsed(List<ParsedAction> actions, ActionContext context, int index) {
        if (context.isSkippingRemaining()) {
            return;
        }
        if (index >= actions.size()) {
            return;
        }

        ParsedAction action = actions.get(index);
        switch (action.getTag()) {
            case "delay" -> {
                long ticks = parseLong(action.getArgument(), 0L);
                if (ticks <= 0) {
                    executeParsed(actions, context, index + 1);
                    return;
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> executeParsed(actions, context, index + 1), ticks);
                return; // continuation is scheduled — do not fall through to index + 1 synchronously
            }
            case "permission-check" -> {
                if (!passesPermissionCheck(context.getPlayer(), action.getArgument())) {
                    context.skipRemainingActions();
                    return;
                }
            }
            case "chance" -> {
                if (!passesChance(action.getArgument())) {
                    context.skipRemainingActions();
                    return;
                }
            }
            default -> dispatch(action, context);
        }

        executeParsed(actions, context, index + 1);
    }

    private boolean passesPermissionCheck(Player player, String permission) {
        if (player == null || permission == null || permission.isBlank()) {
            return false;
        }
        return player.hasPermission(permission);
    }

    private boolean passesChance(String rawPercent) {
        double chance = parseDouble(rawPercent, 100.0);
        if (chance >= 100.0) {
            return true;
        }
        if (chance <= 0.0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble(100.0) < chance;
    }

    private void dispatch(ParsedAction action, ActionContext context) {
        Optional<ActionType> type = registry.get(action.getTag());
        if (type.isEmpty()) {
            plugin.getLogger().warning("Unknown or not-yet-implemented action type '[" + action.getTag()
                    + "]' — skipping. (If this is from the design doc's action list, it may belong to a later build step.)");
            return;
        }
        try {
            type.get().execute(action, context);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Action type '[" + action.getTag() + "]' threw an exception while executing \"" + action.getArgument() + "\"", ex);
        }
    }

    private long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }
}
