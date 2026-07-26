package com.SwagDev.SwagHub.modules.proxy;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.api.event.PlayerSendToServerEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * §3.1/§3.2 implementation: all proxy communication rides the {@code bungeecord:main}
 * plugin messaging channel (registered under its legacy name {@code "BungeeCord"}).
 * One implementation serves both BungeeCord and Velocity — Velocity answers this
 * channel natively via its built-in BungeeCord-compatibility layer — so no proxy-side
 * plugin is ever required (§3.4).
 *
 * <p><b>Lifecycle ownership:</b> registering the outgoing channel, registering this
 * class as the {@link PluginMessageListener} for the incoming channel, and the
 * repeating poll task are entirely owned by {@link ProxyModule} — never by this class
 * or the main plugin class — so the whole feature is fully reversible on module
 * disable / {@code /ah reload}, per §4. {@link #setEnabled(boolean)} is package-private
 * and only ever flipped by {@code ProxyModule}.</p>
 *
 * <p><b>Wire format:</b> plain {@link java.io} streams (not Guava's {@code ByteStreams}
 * helpers, even though most public BungeeCord-messaging examples use them) — see
 * DECISIONS.md's Step 3 section for why: this codebase has never taken a Guava import
 * before, and there's no live server available this build step to verify it's actually
 * on paper-api's compile/runtime classpath, so plain {@code DataOutputStream}/{@code
 * DataInputStream} over a {@code ByteArrayOutputStream}/{@code ByteArrayInputStream}
 * removes that risk entirely at zero cost.</p>
 *
 * <p><b>Thread safety:</b> {@link #onPluginMessageReceived(String, Player, byte[])} is
 * not documented by the Bukkit API as guaranteed to run on the main thread. Every
 * pending callback fired from it is therefore hopped onto the main thread via {@link
 * Bukkit#getScheduler()}{@code .runTask(...)} before invocation, even though in
 * practice CraftBukkit/Paper currently dispatch this on the main thread — defensive,
 * not provably necessary, and free.</p>
 */
public class BungeeChannelProxyService implements ProxyService, PluginMessageListener {

    private static final String CHANNEL = "BungeeCord";
    private static final String ALL_SERVERS_KEY = "ALL";

    private final SwagHub plugin;

    private final Map<String, Integer> cachedCounts = new ConcurrentHashMap<>();
    private final Map<String, List<String>> cachedPlayerLists = new ConcurrentHashMap<>();
    private volatile List<String> cachedServers = Collections.emptyList();
    private volatile int cachedTotalCount = 0;

    private final List<Consumer<List<String>>> pendingServerListCallbacks =
            Collections.synchronizedList(new ArrayList<>());
    private final Map<String, List<Consumer<Integer>>> pendingPlayerCountCallbacks = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<List<String>>>> pendingPlayerListCallbacks = new ConcurrentHashMap<>();

    private volatile boolean enabled = false;
    private volatile long connectTimeoutTicks = 40L;

    public BungeeChannelProxyService(SwagHub plugin) {
        this.plugin = plugin;
    }

    /** Flipped by {@link ProxyModule#onEnable()}/{@code onDisable()} — never toggled from anywhere else. */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Re-read by {@link ProxyModule} on enable/{@code /ah reload} from {@code proxy.connect-timeout-ticks}. */
    void setConnectTimeoutTicks(long ticks) {
        this.connectTimeoutTicks = Math.max(0, ticks);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void connect(Player player, String serverName) {
        if (!enabled || player == null || serverName == null || serverName.isBlank()) {
            return;
        }
        String server = serverName.trim();

        // §5.11 developer API: fired ONLY on this direct, player-initiated path — see
        // PlayerSendToServerEvent's own javadoc for why connectOther() below never
        // fires it. A cancelling listener stops everything: no Connect message is
        // built or sent, and no fallback-timeout check is scheduled.
        PlayerSendToServerEvent event = new PlayerSendToServerEvent(player, server);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        byte[] message = writeMessage(out -> {
            out.writeUTF("Connect");
            out.writeUTF(server);
        });
        if (message == null) {
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, message);
        scheduleConnectFallback(player, server);
    }

    @Override
    public void connectOther(String targetPlayerName, String serverName) {
        if (!enabled || targetPlayerName == null || targetPlayerName.isBlank()
                || serverName == null || serverName.isBlank()) {
            return;
        }
        Optional<Player> conduit = anyOnlinePlayer();
        if (conduit.isEmpty()) {
            plugin.getLogger().warning("Cannot send '" + targetPlayerName + "' to server '" + serverName
                    + "' via ConnectOther — no players are currently online on this server to relay the proxy message through.");
            return;
        }
        String player = targetPlayerName.trim();
        String server = serverName.trim();
        byte[] message = writeMessage(out -> {
            out.writeUTF("ConnectOther");
            out.writeUTF(player);
            out.writeUTF(server);
        });
        if (message == null) {
            return;
        }
        conduit.get().sendPluginMessage(plugin, CHANNEL, message);
    }

    @Override
    public void requestServerList(Consumer<List<String>> callback) {
        if (!enabled) {
            if (callback != null) {
                callback.accept(getCachedServers());
            }
            return;
        }
        Optional<Player> conduit = anyOnlinePlayer();
        if (conduit.isEmpty()) {
            // No channel exists to send through right now — serve the cache instead
            // of leaving the caller waiting on a response that can never arrive.
            if (callback != null) {
                callback.accept(getCachedServers());
            }
            return;
        }
        if (callback != null) {
            pendingServerListCallbacks.add(callback);
        }
        byte[] message = writeMessage(out -> out.writeUTF("GetServers"));
        if (message == null) {
            return;
        }
        conduit.get().sendPluginMessage(plugin, CHANNEL, message);
    }

    @Override
    public void requestPlayerCount(String server, Consumer<Integer> callback) {
        if (server == null || server.isBlank()) {
            return;
        }
        String requested = server.trim();
        if (!enabled) {
            if (callback != null) {
                callback.accept(getCachedCount(requested));
            }
            return;
        }
        Optional<Player> conduit = anyOnlinePlayer();
        if (conduit.isEmpty()) {
            if (callback != null) {
                callback.accept(getCachedCount(requested));
            }
            return;
        }
        if (callback != null) {
            String key = cacheKey(requested);
            pendingPlayerCountCallbacks
                    .computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(callback);
        }
        byte[] message = writeMessage(out -> {
            out.writeUTF("PlayerCount");
            out.writeUTF(requested);
        });
        if (message == null) {
            return;
        }
        conduit.get().sendPluginMessage(plugin, CHANNEL, message);
    }

    @Override
    public void requestPlayerList(String server, Consumer<List<String>> callback) {
        if (server == null || server.isBlank()) {
            return;
        }
        String requested = server.trim();
        if (!enabled) {
            if (callback != null) {
                callback.accept(getCachedPlayerList(requested));
            }
            return;
        }
        Optional<Player> conduit = anyOnlinePlayer();
        if (conduit.isEmpty()) {
            if (callback != null) {
                callback.accept(getCachedPlayerList(requested));
            }
            return;
        }
        if (callback != null) {
            String key = requested.toLowerCase(Locale.ROOT);
            pendingPlayerListCallbacks
                    .computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(callback);
        }
        byte[] message = writeMessage(out -> {
            out.writeUTF("PlayerList");
            out.writeUTF(requested);
        });
        if (message == null) {
            return;
        }
        conduit.get().sendPluginMessage(plugin, CHANNEL, message);
    }

    @Override
    public int getCachedCount(String server) {
        if (server == null || server.isBlank()) {
            return 0;
        }
        if (server.equalsIgnoreCase(ALL_SERVERS_KEY)) {
            return cachedTotalCount;
        }
        return cachedCounts.getOrDefault(server.toLowerCase(Locale.ROOT), 0);
    }

    @Override
    public int getCachedTotalCount() {
        return cachedTotalCount;
    }

    @Override
    public Map<String, Integer> getCachedCounts() {
        return Collections.unmodifiableMap(cachedCounts);
    }

    @Override
    public List<String> getCachedServers() {
        return cachedServers;
    }

    /** Not part of {@link ProxyService} yet — no consumer needs it this build step; kept public for future use. */
    public List<String> getCachedPlayerList(String server) {
        if (server == null || server.isBlank()) {
            return Collections.emptyList();
        }
        return cachedPlayerLists.getOrDefault(server.toLowerCase(Locale.ROOT), Collections.emptyList());
    }

    private String cacheKey(String server) {
        return server.equalsIgnoreCase(ALL_SERVERS_KEY) ? ALL_SERVERS_KEY : server.toLowerCase(Locale.ROOT);
    }

    private Optional<Player> anyOnlinePlayer() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            return Optional.of(player);
        }
        return Optional.empty();
    }

    /**
     * Schedules the §3.3 fallback check: if {@code player} is still online on THIS
     * server after {@code connectTimeoutTicks}, the {@code Connect} silently failed
     * (the protocol has no explicit failure acknowledgement on this channel — a
     * player still being here is the only observable symptom), so the configured
     * {@code server-offline} message is sent. Re-resolves the player fresh by UUID
     * rather than holding the original reference, so a player who fully disconnected
     * in the meantime is handled the same safe way (no message, no error).
     */
    private void scheduleConnectFallback(Player player, String serverName) {
        long timeout = connectTimeoutTicks;
        if (timeout <= 0) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player stillHere = Bukkit.getPlayer(uuid);
            if (stillHere != null && stillHere.isOnline()) {
                plugin.getMessageUtil().send(stillHere, "server-offline", Map.of("server", serverName));
            }
        }, timeout);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) {
            return;
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
        String subChannel;
        try {
            subChannel = in.readUTF();
        } catch (IOException ex) {
            // Malformed/truncated message — never let this crash the listener or the server.
            return;
        }

        switch (subChannel) {
            case "PlayerCount" -> handlePlayerCount(in);
            case "GetServers" -> handleGetServers(in);
            case "PlayerList" -> handlePlayerList(in);
            default -> {
                // GetServer/UUID/UUIDOther/ServerIP/PlayerCountOther/... — not used by SwagHub, ignored.
            }
        }
    }

    private void handlePlayerCount(DataInputStream in) {
        String server;
        int count;
        try {
            server = in.readUTF();
            count = in.readInt();
        } catch (IOException ex) {
            plugin.getLogger().warning("Received a malformed PlayerCount response from the proxy — ignoring.");
            return;
        }
        String key = cacheKey(server);
        if (key.equals(ALL_SERVERS_KEY)) {
            cachedTotalCount = count;
        } else {
            cachedCounts.put(key, count);
        }
        firePlayerCountCallbacks(key, count);
    }

    private void handleGetServers(DataInputStream in) {
        String raw;
        try {
            raw = in.readUTF();
        } catch (IOException ex) {
            plugin.getLogger().warning("Received a malformed GetServers response from the proxy — ignoring.");
            return;
        }
        List<String> servers = splitCsv(raw);
        cachedServers = servers;
        fireServerListCallbacks(servers);
    }

    private void handlePlayerList(DataInputStream in) {
        String server;
        String raw;
        try {
            server = in.readUTF();
            raw = in.readUTF();
        } catch (IOException ex) {
            plugin.getLogger().warning("Received a malformed PlayerList response from the proxy — ignoring.");
            return;
        }
        List<String> players = splitCsv(raw);
        String key = server.toLowerCase(Locale.ROOT);
        cachedPlayerLists.put(key, players);
        firePlayerListCallbacks(key, players);
    }

    private List<String> splitCsv(String raw) {
        List<String> values = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return Collections.unmodifiableList(values);
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return Collections.unmodifiableList(values);
    }

    private void firePlayerCountCallbacks(String key, int count) {
        List<Consumer<Integer>> callbacks = pendingPlayerCountCallbacks.remove(key);
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }
        List<Consumer<Integer>> snapshot;
        synchronized (callbacks) {
            snapshot = new ArrayList<>(callbacks);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Consumer<Integer> callback : snapshot) {
                callback.accept(count);
            }
        });
    }

    private void fireServerListCallbacks(List<String> servers) {
        List<Consumer<List<String>>> snapshot;
        synchronized (pendingServerListCallbacks) {
            if (pendingServerListCallbacks.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(pendingServerListCallbacks);
            pendingServerListCallbacks.clear();
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Consumer<List<String>> callback : snapshot) {
                callback.accept(servers);
            }
        });
    }

    private void firePlayerListCallbacks(String key, List<String> players) {
        List<Consumer<List<String>>> callbacks = pendingPlayerListCallbacks.remove(key);
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }
        List<Consumer<List<String>>> snapshot;
        synchronized (callbacks) {
            snapshot = new ArrayList<>(callbacks);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Consumer<List<String>> callback : snapshot) {
                callback.accept(players);
            }
        });
    }

    /** Small helper so every outgoing message builder shares one IOException-to-null translation. */
    private byte[] writeMessage(IOWriter writer) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            writer.write(out);
        } catch (IOException ex) {
            // A ByteArrayOutputStream-backed DataOutputStream never actually throws in practice —
            // guarded anyway so a future change here can never crash a caller.
            plugin.getLogger().warning("Failed to build an outgoing proxy message — skipping this send.");
            return null;
        }
        return baos.toByteArray();
    }

    @FunctionalInterface
    private interface IOWriter {
        void write(DataOutputStream out) throws IOException;
    }
}
