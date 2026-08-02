package com.SwagDev.SwagHub.modules.networkstats;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.module.Module;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads player stats from other servers on the network (e.g. SMP, Factions) via each one's
 * SwagCore {@code /swagnet/swagcore/player/<uuid>} endpoint, rather than connecting directly
 * to their databases — keeps each game-type's data physically isolated (SMP's economy never
 * touches Factions') while still letting the hub show a player their stats across servers.
 *
 * <p>Every fetch has a short timeout and is cached briefly — a server that's slow, offline,
 * or unconfigured just yields {@link Optional#empty()} rather than blocking or erroring.</p>
 */
public class NetworkStatsModule extends Module {

    private static final long CACHE_TTL_MS = 60_000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final long RETURN_POLL_INTERVAL_TICKS = 100L; // 5s
    private static final long RETURN_STALE_MS = 30 * 60_000L; // 30 minutes

    public record PlayerSummary(double balance, String rank, long playtimeMinutes, int homes) {}

    private record CacheEntry(PlayerSummary summary, long fetchedAt) {
        boolean isFresh() { return System.currentTimeMillis() - fetchedAt < CACHE_TTL_MS; }
    }

    private record QueuedPlayer(UUID uuid, String name, long queuedAt) {}

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** Origin server (lower-cased) -&gt; uuid -&gt; queued evacuee awaiting auto-return once that server is healthy again. */
    private final Map<String, Map<UUID, QueuedPlayer>> pendingReturn = new ConcurrentHashMap<>();

    private Map<String, String> knownServers = Map.of();
    private String sharedSecret = "";
    private boolean apiRegistered;
    private BukkitTask returnPollTask;

    public NetworkStatsModule(SwagHub plugin) {
        super(plugin, "networkstats");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        readSettings();

        var webService = plugin.getWebService();
        if (webService != null) {
            webService.registerServiceModule(plugin, new NetworkServiceApiHandler(plugin, this));
            apiRegistered = true;
        }

        returnPollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::pollPendingReturns, RETURN_POLL_INTERVAL_TICKS, RETURN_POLL_INTERVAL_TICKS);
    }

    @Override
    protected void onDisable() {
        cache.clear();
        pendingReturn.clear();
        if (returnPollTask != null) {
            returnPollTask.cancel();
            returnPollTask = null;
        }
        if (apiRegistered) {
            var webService = plugin.getWebService();
            if (webService != null) webService.unregisterServiceModule(plugin);
            apiRegistered = false;
        }
    }

    @Override
    protected void onReload() {
        readSettings();
        cache.clear();
    }

    /** Called by {@link NetworkServiceApiHandler} when a game server reports players it just evacuated to the hub. */
    public void queueForReturn(String fromServer, UUID uuid, String name) {
        String key = fromServer.toLowerCase(Locale.ROOT);
        pendingReturn.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .put(uuid, new QueuedPlayer(uuid, name, System.currentTimeMillis()));
    }

    /**
     * Runs off the main thread (registered via {@code runTaskTimerAsynchronously}) since it
     * does blocking HTTP reachability checks. For each origin server with a non-empty queue,
     * a plain GET against its already-configured {@code network.known-servers} URL confirms
     * the JVM is back up — any non-5xx response counts (even a 401/404 means SwagAPI's web
     * service answered), no dedicated "/ping" route needed. Once healthy, every queued player
     * is sent back via {@code ConnectOther}, hopped onto the main thread since that touches
     * Bukkit's plugin-messaging API. Stale entries (older than 30 minutes) are dropped
     * regardless of health, so a server that never comes back doesn't poll forever.
     */
    private void pollPendingReturns() {
        if (pendingReturn.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (String server : List.copyOf(pendingReturn.keySet())) {
            Map<UUID, QueuedPlayer> queue = pendingReturn.get(server);
            if (queue == null) continue;

            queue.values().removeIf(q -> now - q.queuedAt() > RETURN_STALE_MS);
            if (queue.isEmpty()) {
                pendingReturn.remove(server, queue);
                continue;
            }

            if (!isServerReachable(server)) continue;

            List<QueuedPlayer> toReturn = new ArrayList<>(queue.values());
            pendingReturn.remove(server);

            Bukkit.getScheduler().runTask(plugin, () -> {
                var proxyService = plugin.getProxyService();
                if (proxyService == null) return;
                for (QueuedPlayer q : toReturn) {
                    proxyService.connectOther(q.name(), server);
                }
                plugin.getLogger().info("'" + server + "' is back up — sent " + toReturn.size()
                        + " evacuated player(s) back.");
            });
        }
    }

    private boolean isServerReachable(String server) {
        String baseUrl = knownServers.get(server);
        if (baseUrl == null || sharedSecret.isBlank()) return false;

        String url = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-SwagNetwork-Key", sharedSecret)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private void readSettings() {
        sharedSecret = plugin.getConfig().getString("network.shared-secret", "");

        Map<String, String> servers = new LinkedHashMap<>();
        var section = plugin.getConfig().getConfigurationSection("network.known-servers");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String url = section.getString(id);
                if (url != null && !url.isBlank()) servers.put(id.toLowerCase(Locale.ROOT), url);
            }
        }
        knownServers = servers;
    }

    public Set<String> getKnownServerIds() {
        return knownServers.keySet();
    }

    /**
     * Fetches (or returns a cached) player summary from {@code serverId} (a key from
     * {@code network.known-servers} in config.yml). Empty if the server id is unknown, the
     * shared secret isn't configured, the request fails, times out, or the response can't
     * be parsed — never throws.
     */
    public CompletableFuture<Optional<PlayerSummary>> fetch(String serverId, UUID uuid) {
        String key = serverId.toLowerCase(Locale.ROOT);
        String baseUrl = knownServers.get(key);
        if (baseUrl == null || sharedSecret.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String cacheKey = key + ":" + uuid;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.isFresh()) {
            return CompletableFuture.completedFuture(Optional.of(cached.summary()));
        }

        String url = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "player/" + uuid;
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-SwagNetwork-Key", sharedSecret)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseResponse(cacheKey, response))
                .exceptionally(ex -> Optional.empty());
    }

    private Optional<PlayerSummary> parseResponse(String cacheKey, HttpResponse<String> response) {
        if (response.statusCode() != 200) return Optional.empty();
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            PlayerSummary summary = new PlayerSummary(
                    json.get("balance").getAsDouble(),
                    json.get("rank").getAsString(),
                    json.get("playtimeMinutes").getAsLong(),
                    json.get("homes").getAsInt());
            cache.put(cacheKey, new CacheEntry(summary, System.currentTimeMillis()));
            return Optional.of(summary);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
