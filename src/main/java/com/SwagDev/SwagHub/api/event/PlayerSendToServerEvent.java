package com.SwagDev.SwagHub.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * §5.11's developer API: fired right before SwagHub sends {@code player} to
 * {@code targetServer} via the proxy {@code Connect} sub-channel (§3.2). Cancelling
 * this event prevents the {@code Connect} plugin message from ever being sent (and
 * therefore prevents {@code BungeeChannelProxyService}'s §3.3 fallback-timeout check
 * from ever being scheduled either — nothing happens at all).
 *
 * <p><b>Scoping (resolved ambiguity, DECISIONS.md Step 8):</b> only fired from
 * {@code BungeeChannelProxyService#connect(Player, String)} — the direct,
 * player-initiated path used by the server selector, portals, the {@code [server]}
 * action type, and any third-party plugin calling {@code ProxyService#connect}
 * directly. Deliberately NOT fired from {@code #connectOther(String, String)}: that
 * path acts on a player who may not even be present on this server (an
 * admin/cross-server relay action targeting a player by name), so there is no
 * guaranteed-local {@link Player} object to attach to this event in the first place,
 * and firing it against a best-effort {@code Bukkit.getPlayerExact(...)} lookup would
 * silently do nothing when that lookup misses — a confusing half-implemented
 * contract. A third-party plugin that needs to gate {@code ConnectOther} should do so
 * before calling {@code ProxyService#connectOther} itself.</p>
 *
 * <p>Third-party plugins: register a listener on this event to veto or observe
 * players being sent to another backend server (e.g. to enforce a queue, log
 * transfers, or block transfers during maintenance).</p>
 */
public class PlayerSendToServerEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String targetServer;
    private boolean cancelled;

    public PlayerSendToServerEvent(Player player, String targetServer) {
        this.player = player;
        this.targetServer = targetServer;
    }

    /** The player about to be sent to {@link #getTargetServer()}. */
    public Player getPlayer() {
        return player;
    }

    /** The proxy-configured backend server name the player is about to be sent to. */
    public String getTargetServer() {
        return targetServer;
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
