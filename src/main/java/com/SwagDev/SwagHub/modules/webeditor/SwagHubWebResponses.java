package com.SwagDev.SwagHub.modules.webeditor;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Shared HTTP response helpers used by the web editor's handlers. Mirrors SwagCore's
 * {@code WebDashboardServer}, plus {@link #sendEmpty(HttpExchange, int)} for the
 * no-body-detail {@code 401}/{@code 403} responses §7.2 explicitly requires.
 */
public final class SwagHubWebResponses {

    private SwagHubWebResponses() {
    }

    public static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendPlain(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * A response with no body at all — used for {@code 401} (unauthenticated) and
     * {@code 403} (authenticated but lacking the required {@code swaghub.dashboard.*}
     * permission), both of which §7.2 requires to carry "no body detail."
     */
    public static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
}
