package com.SwagDev.SwagHub.action;

import com.SwagDev.SwagHub.SwagHub;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Everything an {@link ActionType} (or the guard logic in {@link ActionParser}) needs
 * to execute one action: who triggered it, and what placeholder tokens are available.
 *
 * <p>One context is created per triggering event (item click, menu click, join,
 * announcement tick, ...) and threaded through every action in that sequence, so a
 * {@code [permission-check]} or {@code [chance]} guard can stop the rest of the same
 * sequence via {@link #skipRemainingActions()}.</p>
 */
public final class ActionContext {

    private final SwagHub plugin;
    private final Player player;
    private final Map<String, String> placeholders;
    private boolean skipRemaining = false;

    public ActionContext(SwagHub plugin, Player player, Map<String, String> placeholders) {
        this.plugin = plugin;
        this.player = player;

        Map<String, String> merged = new HashMap<>();
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        // %player% is always available when a player triggered the action, unless the
        // caller already supplied its own value for that key.
        if (player != null) {
            merged.putIfAbsent("player", player.getName());
        }
        this.placeholders = Collections.unmodifiableMap(merged);
    }

    public ActionContext(SwagHub plugin, Player player) {
        this(plugin, player, Collections.emptyMap());
    }

    public SwagHub getPlugin() {
        return plugin;
    }

    /** May be {@code null} for actions triggered without an acting player (e.g. a console-only announcement). */
    public Player getPlayer() {
        return player;
    }

    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    /** Called by a failed {@code [permission-check]}/{@code [chance]} guard to halt the rest of the sequence. */
    public void skipRemainingActions() {
        this.skipRemaining = true;
    }

    public boolean isSkippingRemaining() {
        return skipRemaining;
    }

    /** Replaces every {@code %key%} token in {@code input} with its value from this context's placeholders. */
    public String applyPlaceholders(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }
}
