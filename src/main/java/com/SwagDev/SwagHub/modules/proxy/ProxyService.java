package com.SwagDev.SwagHub.modules.proxy;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * §3.2's clean proxy-facing contract. {@link BungeeChannelProxyService} is the (only)
 * implementation, riding the {@code bungeecord:main} plugin messaging channel — but
 * feature code (menus, portals, the action system, later placeholders) should always
 * depend on this interface, never the concrete class, so a future push-based
 * implementation (§3.4's "optional Velocity companion" idea) can drop in later without
 * touching any consumer.
 *
 * <p>Every method here is safe to call at any time, including while the proxy module
 * is disabled ({@link #isEnabled()} is {@code false}) or while zero players are online
 * — neither condition ever throws. Disabled: connect/connectOther silently no-op (the
 * {@code [server]} action type is responsible for the one-line admin warning in that
 * case — see {@code ServerAction}); the request-style and cached-style getter methods
 * keep returning the last-known cache (or {@code 0}/empty if nothing has ever been
 * cached). Zero players online: nothing can be sent (plugin messages only travel
 * through a connected player's channel), so request methods behave exactly like
 * "disabled" for that one call — this is expected, not an error, per §3.2.</p>
 */
public interface ProxyService {

    /** Whether the owning {@code ProxyModule} is currently enabled. */
    boolean isEnabled();

    /**
     * {@code Connect} — sends {@code player} to {@code serverName}. If the connect
     * appears to silently fail (the player is still on this server after
     * {@code proxy.connect-timeout-ticks}), the configured {@code server-offline}
     * message is sent automatically (§3.3) — callers never need to handle that
     * themselves.
     */
    void connect(Player player, String serverName);

    /**
     * {@code ConnectOther} — sends the (possibly offline-to-this-server, online-
     * elsewhere-on-the-network) player named {@code targetPlayerName} to {@code
     * serverName}, relayed through any currently-connected player's channel. Not
     * consumed anywhere yet in this build step — reserved for the admin command layer
     * added in a later step, per §3.2.
     */
    void connectOther(String targetPlayerName, String serverName);

    /**
     * {@code GetServers} — asynchronously refreshes {@link #getCachedServers()} and,
     * once the proxy responds, invokes {@code callback} with the fresh list (hopped
     * onto the main thread). If the callback is {@code null}, this just refreshes the
     * cache. If no players are online to relay the request through, {@code callback}
     * (when non-null) is invoked immediately with the current cache instead of hanging
     * forever waiting for a response that can never arrive.
     */
    void requestServerList(Consumer<List<String>> callback);

    /**
     * {@code PlayerCount} — asynchronously refreshes the cached count for {@code
     * server} (pass {@code "ALL"} for the network-wide total) and, once the proxy
     * responds, invokes {@code callback} with the fresh count (hopped onto the main
     * thread). Same zero-players-online / null-callback behavior as {@link
     * #requestServerList(Consumer)}.
     */
    void requestPlayerCount(String server, Consumer<Integer> callback);

    /**
     * {@code PlayerList} — asynchronously refreshes the cached player-name list for
     * {@code server} and, once the proxy responds, invokes {@code callback} with it
     * (hopped onto the main thread). Not consumed anywhere yet in this build step —
     * reserved for a future menu/tooltip that lists who's on a server, per §3.2's
     * "PlayerCount / PlayerList" pairing.
     */
    void requestPlayerList(String server, Consumer<List<String>> callback);

    /**
     * Last-known player count for {@code server} (case-insensitive; pass {@code
     * "ALL"} for the network-wide total), or {@code 0} if never cached. Never blocks
     * and never triggers a fresh query — this is the pure cache-read getter later
     * build steps (menus, holograms, scoreboard, the §8 PlaceholderAPI expansion) call.
     */
    int getCachedCount(String server);

    /** Last-known network-wide total player count, or {@code 0} if never cached. */
    int getCachedTotalCount();

    /** Server name (lower-cased) -&gt; last-known player count. Never includes the "ALL" total. */
    Map<String, Integer> getCachedCounts();

    /** Last-known {@code GetServers} response, or an empty list if never cached. */
    List<String> getCachedServers();
}
