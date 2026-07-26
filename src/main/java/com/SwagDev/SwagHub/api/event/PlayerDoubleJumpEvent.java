package com.SwagDev.SwagHub.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * §5.11's developer API: fired by {@code DoubleJumpModule} right before the §5.7
 * velocity boost (+ particle/sound) is actually applied to {@code player} — after the
 * disabled-region check has already passed, so this only fires for a boost that would
 * genuinely happen. Cancelling this event skips ONLY the boost/particle/sound.
 *
 * <p><b>The underlying {@link org.bukkit.event.player.PlayerToggleFlightEvent} is
 * cancelled either way, regardless of this event's outcome</b> — see
 * {@code DoubleJumpModule}'s own class javadoc: "always prevent persistent flight
 * from beginning" is non-negotiable, since double jump is a one-shot velocity boost,
 * never real flight. This event exists purely to let third-party plugins veto or
 * observe the boost effect itself (e.g. to implement a double-jump cooldown, a
 * region-based override of their own, or custom effects instead of SwagHub's).</p>
 */
public class PlayerDoubleJumpEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private boolean cancelled;

    public PlayerDoubleJumpEvent(Player player) {
        this.player = player;
    }

    /** The player about to receive the double-jump boost. */
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
