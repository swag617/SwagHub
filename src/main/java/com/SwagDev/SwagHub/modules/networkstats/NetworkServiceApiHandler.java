package com.SwagDev.SwagHub.modules.networkstats;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.modules.webeditor.SwagHubWebResponses;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Server-to-server API for SwagHub — registered via SwagAPI's {@code registerServiceModule}
 * (shared-secret auth, not the human dashboard's session cookie), mounted at
 * {@code /swagnet/swaghub/}. Currently exposes one route: {@code POST /evacuated} — a game
 * server reports the players it just sent to the hub ahead of a restart, so the hub can send
 * them back once that server is healthy again (see {@link NetworkStatsModule#queueForReturn}).
 */
public class NetworkServiceApiHandler implements HttpHandler {

    private final SwagHub plugin;
    private final NetworkStatsModule networkStatsModule;
    private final Gson gson = new Gson();

    public NetworkServiceApiHandler(SwagHub plugin, NetworkStatsModule networkStatsModule) {
        this.plugin = plugin;
        this.networkStatsModule = networkStatsModule;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/evacuated") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleEvacuated(exchange);
            } else {
                SwagHubWebResponses.sendJson(exchange, 404, "{\"error\":\"Unknown endpoint\"}");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NetworkServiceApiHandler error", e);
            try { SwagHubWebResponses.sendJson(exchange, 500, "{\"error\":\"Internal server error\"}"); }
            catch (IOException ignored) {}
        }
    }

    private void handleEvacuated(HttpExchange exchange) throws IOException {
        JsonObject body = readBody(exchange);
        if (body == null || !body.has("fromServer") || !body.has("players")) {
            SwagHubWebResponses.sendJson(exchange, 400, "{\"error\":\"Expected {fromServer, players[]}\"}");
            return;
        }

        String fromServer = body.get("fromServer").getAsString();
        int queued = 0;
        for (JsonElement el : body.getAsJsonArray("players")) {
            try {
                JsonObject p = el.getAsJsonObject();
                UUID uuid = UUID.fromString(p.get("uuid").getAsString());
                String name = p.get("name").getAsString();
                networkStatsModule.queueForReturn(fromServer, uuid, name);
                queued++;
            } catch (Exception ignored) {
                // one malformed entry never aborts the rest of the batch
            }
        }

        plugin.getLogger().info("Received evacuation report from '" + fromServer + "' — queued "
                + queued + " player(s) for auto-return.");

        JsonObject response = new JsonObject();
        response.addProperty("queued", queued);
        SwagHubWebResponses.sendJson(exchange, 200, response.toString());
    }

    private JsonObject readBody(HttpExchange exchange) {
        try (var reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            JsonElement el = gson.fromJson(reader, JsonElement.class);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
