package com.SwagDev.SwagHub.modules.webeditor;

import com.SwagDev.SwagHub.SwagHub;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * Single entry point registered with SwagAPI's {@code IWebService} (one handler per
 * plugin, per {@link com.SwagDev.SwagAPI.api.IWebService#registerModule}). Mirrors
 * SwagCore's {@code DashboardHttpHandler} structurally: applies CORS headers, answers
 * {@code OPTIONS} preflight requests directly, and dispatches {@code /api/...} to
 * {@link SwagHubWebApiHandler} and everything else to {@link SwagHubWebStaticHandler}.
 *
 * <p>Authentication is handled entirely by SwagAPI's session gate before this handler
 * ever runs (§7.2) — this class has no login/password logic of its own.</p>
 */
public class SwagHubWebHandler implements HttpHandler {

    private final SwagHubWebApiHandler apiHandler;
    private final SwagHubWebStaticHandler staticHandler;

    public SwagHubWebHandler(SwagHub plugin) {
        this.apiHandler = new SwagHubWebApiHandler(plugin);
        this.staticHandler = new SwagHubWebStaticHandler();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/api/")) {
            apiHandler.handle(exchange);
        } else {
            staticHandler.handle(exchange);
        }
    }
}
