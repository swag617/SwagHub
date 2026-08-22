package com.SwagDev.SwagHub.modules.webeditor;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.CompatibilityManager;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.modules.announcements.Rotation;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Handles every {@code /api/...} endpoint for the SwagHub web editor (§7.4), scoped
 * deliberately narrower than SwagCore's full admin dashboard — see DECISIONS.md's
 * Step 9 section for exactly which configs get an editor this build step and why
 * holograms/portals/items/menus don't yet.
 *
 * <p><b>Auth + permission gate on every request</b> (§7.2): {@link #requirePermission}
 * resolves the signed-in SwagAPI panel username via {@code IWebService
 * #getSessionUsername}; empty -&gt; {@code 401} with no body. Present -&gt; hops to the
 * main thread (permission checks are real Bukkit API calls) to resolve {@code
 * swaghub.dashboard.view} (GET) or {@code swaghub.dashboard.edit} (POST) against
 * either the currently-online {@link Player} of that name, or — if nobody by that name
 * is online right now — {@code Bukkit.getOfflinePlayer(username).isOp()} as the only
 * available fallback (this plugin deliberately has no Vault dependency; see
 * DECISIONS.md Step 9 for why this is a permanent, documented limitation, not a bug).
 * Failing that check -&gt; {@code 403} with no body. Because the permission check itself
 * always runs via {@code Bukkit.getScheduler().runTask(...)}, every authorized
 * endpoint's own body (file I/O, module {@code reload()} calls, response writing) also
 * ends up executing on the main thread "for free" — the same accepted trade-off already
 * in use by SwagCore's dashboard handlers (see the cross-project memory note on this
 * pattern), not a new architectural decision made here.</p>
 */
public class SwagHubWebApiHandler {

    private static final String PERM_VIEW = "swaghub.dashboard.view";
    private static final String PERM_EDIT = "swaghub.dashboard.edit";

    /**
     * Same shape as {@code ActionParser}'s own tag-line pattern — used only to reject
     * obviously-malformed action strings written through the announcements editor
     * (§10.4's "human-readable field error, never a stack trace" contract), not to
     * duplicate the full action-registry schema validation §7.4 only actually requires
     * for the (out-of-scope this step) menu editor.
     */
    private static final Pattern ACTION_LINE_PATTERN = Pattern.compile("^\\[[a-zA-Z0-9-]+]\\s*.*$", Pattern.DOTALL);

    private final SwagHub plugin;
    private final Gson gson = new Gson();

    public SwagHubWebApiHandler(SwagHub plugin) {
        this.plugin = plugin;
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        try {
            switch (path) {
                case "/status" -> dispatchReadOnly(exchange, method, this::handleStatus);
                case "/config/core" -> dispatch(exchange, method, this::getCore, this::postCore);
                case "/config/scoreboard" -> dispatch(exchange, method, this::getScoreboard, this::postScoreboard);
                case "/config/tablist" -> dispatch(exchange, method, this::getTablist, this::postTablist);
                case "/config/announcements" -> dispatch(exchange, method, this::getAnnouncements, this::postAnnouncements);
                case "/config/world-protection" -> dispatch(exchange, method, this::getWorldProtection, this::postWorldProtection);
                case "/config/join-spawn" -> dispatch(exchange, method, this::getJoinSpawn, this::postJoinSpawn);
                case "/config/chat-controls" -> dispatch(exchange, method, this::getChatControls, this::postChatControls);
                case "/config/network" -> dispatch(exchange, method, this::getNetwork, this::postNetwork);
                case "/config/messages" -> dispatch(exchange, method, this::getMessages, this::postMessages);
                default -> SwagHubWebResponses.sendJson(exchange, 404, jsonError("Unknown endpoint"));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Web editor API error on " + path, e);
            try {
                SwagHubWebResponses.sendJson(exchange, 500, jsonError("Internal server error"));
            } catch (IOException ignored) {
            }
        }
    }

    // ─── Dispatch + auth/permission gate ───────────────────────────

    private void dispatchReadOnly(HttpExchange exchange, String method, Handler getHandler) throws IOException {
        if (!"GET".equals(method)) {
            SwagHubWebResponses.sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        requirePermission(exchange, PERM_VIEW, getHandler);
    }

    private void dispatch(HttpExchange exchange, String method, Handler getHandler, Handler postHandler) throws IOException {
        if ("GET".equals(method)) {
            requirePermission(exchange, PERM_VIEW, getHandler);
        } else if ("POST".equals(method)) {
            requirePermission(exchange, PERM_EDIT, postHandler);
        } else {
            SwagHubWebResponses.sendJson(exchange, 405, jsonError("Method Not Allowed"));
        }
    }

    /**
     * §7.2's auth gate. Empty session -&gt; 401, no body. Present but lacking the
     * required permission -&gt; 403, no body. Otherwise runs {@code onAuthorized} on the
     * main thread (see class javadoc for why every handler body ends up there too).
     */
    private void requirePermission(HttpExchange exchange, String permission, Handler onAuthorized) throws IOException {
        var webService = plugin.getWebService();
        if (webService == null) {
            // Unreachable in practice — this handler is only ever registered by
            // WebEditorModule after confirming a non-null, running IWebService — but
            // guarded rather than risking an NPE mid-request.
            SwagHubWebResponses.sendEmpty(exchange, 401);
            return;
        }

        Optional<String> usernameOpt = webService.getSessionUsername(exchange);
        if (usernameOpt.isEmpty()) {
            SwagHubWebResponses.sendEmpty(exchange, 401);
            return;
        }
        String username = usernameOpt.get();

        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayerExact(username);
                boolean allowed = online != null
                        ? online.hasPermission(permission)
                        : Bukkit.getOfflinePlayer(username).isOp();
                try {
                    if (!allowed) {
                        SwagHubWebResponses.sendEmpty(exchange, 403);
                        return;
                    }
                    onAuthorized.handle(exchange);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Web editor API error on " + exchange.getRequestURI().getPath(), e);
                    try {
                        SwagHubWebResponses.sendJson(exchange, 500, jsonError("Internal server error"));
                    } catch (IOException ignored) {
                    }
                }
            });
        } catch (Exception schedulingException) {
            // The plugin is disabling/reloading right this instant — runTask refuses new
            // work in that window. Not an internal server error in the usual sense.
            plugin.getLogger().log(Level.WARNING, "Could not schedule a web editor request (SwagHub is likely reloading)", schedulingException);
            SwagHubWebResponses.sendJson(exchange, 503, jsonError("SwagHub is currently reloading or shutting down."));
        }
    }

    // ─── GET /status ────────────────────────────────────────────────

    private void handleStatus(HttpExchange exchange) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", plugin.getPluginMeta().getVersion());
        root.addProperty("serverRole", plugin.getCompatibilityManager().getServerRole().name().toLowerCase(Locale.ROOT));

        JsonArray modules = new JsonArray();
        CompatibilityManager compat = plugin.getCompatibilityManager();
        for (Module module : plugin.getModuleManager().getModules()) {
            String key = module.getConfigKey();
            JsonObject m = new JsonObject();
            m.addProperty("configKey", key);
            m.addProperty("enabled", module.isEnabled());
            String yieldedTo = compat.getYieldedModules().get(key);
            if (yieldedTo != null) {
                m.addProperty("yieldedTo", yieldedTo);
            } else {
                m.add("yieldedTo", JsonNull.INSTANCE);
            }
            CompatibilityManager.OverrideMode override = compat.getOverrides()
                    .getOrDefault(key, CompatibilityManager.OverrideMode.AUTO);
            m.addProperty("override", override.name().toLowerCase(Locale.ROOT));
            modules.add(m);
        }
        root.add("modules", modules);

        JsonObject proxy = new JsonObject();
        var proxyService = plugin.getProxyService();
        proxy.addProperty("available", proxyService != null);
        proxy.addProperty("enabled", proxyService != null && proxyService.isEnabled());
        proxy.addProperty("totalOnline", proxyService != null ? proxyService.getCachedTotalCount() : 0);
        root.add("proxy", proxy);

        JsonObject update = new JsonObject();
        var updateService = plugin.getUpdateService();
        if (updateService == null || !updateService.isEnabled()) {
            update.addProperty("checked", false);
        } else {
            update.addProperty("checked", true);
            var infoOpt = updateService.getUpdateInfo("SwagHub");
            if (infoOpt.isPresent()) {
                var info = infoOpt.get();
                update.addProperty("available", info.isUpdateAvailable());
                update.addProperty("latestVersion", info.latestVersion());
                update.addProperty("currentVersion", info.currentVersion());
            } else {
                update.addProperty("available", false);
            }
        }
        root.add("update", update);

        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(root));
    }

    // ─── GET/POST /config/core ──────────────────────────────────────

    private JsonObject buildCoreState() {
        JsonObject root = new JsonObject();
        root.addProperty("serverRole", plugin.getCompatibilityManager().getServerRole().name().toLowerCase(Locale.ROOT));
        root.add("hubWorlds", gson.toJsonTree(plugin.getConfig().getStringList("hub-worlds")));

        JsonObject modules = new JsonObject();
        for (Module module : plugin.getModuleManager().getModules()) {
            String key = module.getConfigKey();
            String path = "modules." + key;
            if (plugin.getConfig().isSet(path)) {
                modules.addProperty(key, plugin.getConfig().getBoolean(path));
            } else {
                modules.add(key, JsonNull.INSTANCE);
            }
        }
        root.add("modules", modules);

        JsonObject compatibility = new JsonObject();
        compatibility.addProperty("autoYield", plugin.getCompatibilityManager().isAutoYieldEnabled());
        JsonObject overrides = new JsonObject();
        for (Map.Entry<String, CompatibilityManager.OverrideMode> entry : plugin.getCompatibilityManager().getOverrides().entrySet()) {
            overrides.addProperty(entry.getKey(), entry.getValue().name().toLowerCase(Locale.ROOT));
        }
        compatibility.add("overrides", overrides);
        root.add("compatibility", compatibility);
        return root;
    }

    private void getCore(HttpExchange exchange) throws IOException {
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(buildCoreState()));
    }

    private void postCore(HttpExchange exchange) throws IOException {
        JsonObject body = readBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body.");
            return;
        }

        // ── Validate everything BEFORE touching any config state (§10.4/§7.4: never a
        //    partial write). ──
        String newRole = null;
        if (body.has("serverRole")) {
            JsonElement el = body.get("serverRole");
            String raw = el.isJsonPrimitive() ? el.getAsString() : null;
            if (!isValidServerRole(raw)) {
                sendError(exchange, 400, "serverRole must be 'hub' or 'game'.");
                return;
            }
            newRole = raw.trim().toLowerCase(Locale.ROOT);
        }

        List<String> newHubWorlds = null;
        if (body.has("hubWorlds")) {
            JsonElement el = body.get("hubWorlds");
            if (!isStringArray(el)) {
                sendError(exchange, 400, "hubWorlds must be an array of world names.");
                return;
            }
            newHubWorlds = toStringList(el);
        }

        Map<String, Boolean> moduleOverrides = null; // value null in map == "clear the override"
        if (body.has("modules")) {
            JsonElement el = body.get("modules");
            if (!el.isJsonObject()) {
                sendError(exchange, 400, "modules must be an object of module key -> true/false/null.");
                return;
            }
            moduleOverrides = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : el.getAsJsonObject().entrySet()) {
                JsonElement v = entry.getValue();
                if (v.isJsonNull()) {
                    moduleOverrides.put(entry.getKey(), null);
                } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
                    moduleOverrides.put(entry.getKey(), v.getAsBoolean());
                } else {
                    sendError(exchange, 400, "modules." + entry.getKey() + " must be true, false, or null.");
                    return;
                }
            }
        }

        Boolean newAutoYield = null;
        Map<String, String> newOverrideModes = null;
        if (body.has("compatibility")) {
            JsonElement el = body.get("compatibility");
            if (!el.isJsonObject()) {
                sendError(exchange, 400, "compatibility must be an object.");
                return;
            }
            JsonObject compat = el.getAsJsonObject();
            if (compat.has("autoYield")) {
                JsonElement ay = compat.get("autoYield");
                if (!ay.isJsonPrimitive() || !ay.getAsJsonPrimitive().isBoolean()) {
                    sendError(exchange, 400, "compatibility.autoYield must be a boolean.");
                    return;
                }
                newAutoYield = ay.getAsBoolean();
            }
            if (compat.has("overrides")) {
                JsonElement ov = compat.get("overrides");
                if (!ov.isJsonObject()) {
                    sendError(exchange, 400, "compatibility.overrides must be an object.");
                    return;
                }
                newOverrideModes = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> entry : ov.getAsJsonObject().entrySet()) {
                    JsonElement v = entry.getValue();
                    String raw = v.isJsonPrimitive() ? v.getAsString() : null;
                    if (!isValidOverrideMode(raw)) {
                        sendError(exchange, 400, "compatibility.overrides." + entry.getKey()
                                + " must be 'auto', 'enabled', or 'disabled'.");
                        return;
                    }
                    newOverrideModes.put(entry.getKey(),
                            CompatibilityManager.OverrideMode.valueOf(raw.trim().toUpperCase(Locale.ROOT))
                                    .name().toLowerCase(Locale.ROOT));
                }
            }
        }

        // ── All validation passed — apply, save, and reload. "modules"/"compatibility.
        //    overrides", when present in the body at all, fully REPLACE the existing
        //    section (see DECISIONS.md Step 9) rather than being patched key-by-key. ──
        var cfg = plugin.getConfig();
        if (newRole != null) {
            cfg.set("server-role", newRole);
        }
        if (newHubWorlds != null) {
            cfg.set("hub-worlds", newHubWorlds);
        }
        if (moduleOverrides != null) {
            cfg.set("modules", null);
            for (Map.Entry<String, Boolean> entry : moduleOverrides.entrySet()) {
                if (entry.getValue() != null) {
                    cfg.set("modules." + entry.getKey(), entry.getValue());
                }
            }
        }
        if (newAutoYield != null) {
            cfg.set("compatibility.auto-yield", newAutoYield);
        }
        if (newOverrideModes != null) {
            cfg.set("compatibility.overrides", null);
            for (Map.Entry<String, String> entry : newOverrideModes.entrySet()) {
                cfg.set("compatibility.overrides." + entry.getKey(), entry.getValue());
            }
        }

        plugin.getConfigManager().save();
        reloadEverything();

        JsonObject response = buildCoreState();
        response.addProperty("saved", true);
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(response));
    }

    /** Exactly what {@code /ah reload} already does — see {@code SwagHubCommand#handleReload}. */
    private void reloadEverything() {
        plugin.getConfigManager().reload();
        plugin.getMessageUtil().load();
        plugin.getCompatibilityManager().load();
        plugin.getModuleManager().reloadAll();
    }

    // ─── GET/POST /config/scoreboard ────────────────────────────────

    private static final String SCOREBOARD_FILE = "scoreboard.yml";

    private void getScoreboard(HttpExchange exchange) throws IOException {
        File file = new File(plugin.getDataFolder(), SCOREBOARD_FILE);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        JsonObject root = scoreboardToJson(yaml);
        addModuleStatus(root, "scoreboard");
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(root));
    }

    private JsonObject scoreboardToJson(YamlConfiguration yaml) {
        JsonObject root = new JsonObject();
        root.addProperty("updateIntervalTicks", yaml.getLong("update-interval-ticks", 20L));
        JsonObject worlds = new JsonObject();
        ConfigurationSection worldsSection = yaml.getConfigurationSection("worlds");
        if (worldsSection != null) {
            for (String key : worldsSection.getKeys(false)) {
                worlds.add(key, scoreboardWorldToJson(worldsSection.getConfigurationSection(key)));
            }
        }
        root.add("worlds", worlds);
        return root;
    }

    private JsonObject scoreboardWorldToJson(ConfigurationSection worldSection) {
        JsonObject obj = new JsonObject();
        obj.add("title", animatedTextToJson(worldSection, "title"));
        obj.add("lines", gson.toJsonTree(worldSection != null ? worldSection.getStringList("lines") : List.of()));
        return obj;
    }

    private void postScoreboard(HttpExchange exchange) throws IOException {
        JsonObject body = readBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body.");
            return;
        }

        long updateIntervalTicks = 20L;
        if (body.has("updateIntervalTicks")) {
            JsonElement el = body.get("updateIntervalTicks");
            if (!isNumber(el)) {
                sendError(exchange, 400, "updateIntervalTicks must be a number.");
                return;
            }
            updateIntervalTicks = Math.max(1L, el.getAsLong());
        }

        if (!body.has("worlds") || !body.get("worlds").isJsonObject()) {
            sendError(exchange, 400, "worlds must be an object of world name -> {title, lines}.");
            return;
        }
        JsonObject worldsObj = body.getAsJsonObject("worlds");

        for (Map.Entry<String, JsonElement> entry : worldsObj.entrySet()) {
            String field = "worlds." + entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                sendError(exchange, 400, field + " must be an object.");
                return;
            }
            JsonObject w = entry.getValue().getAsJsonObject();
            if (w.has("lines") && !isStringArray(w.get("lines"))) {
                sendError(exchange, 400, field + ".lines must be an array of strings.");
                return;
            }
            String titleError = validateAnimatedTextField(w, "title", field + ".title");
            if (titleError != null) {
                sendError(exchange, 400, titleError);
                return;
            }
        }

        YamlConfiguration out = new YamlConfiguration();
        out.set("update-interval-ticks", updateIntervalTicks);
        for (Map.Entry<String, JsonElement> entry : worldsObj.entrySet()) {
            JsonObject w = entry.getValue().getAsJsonObject();
            String path = "worlds." + entry.getKey();
            out.set(path + ".lines", w.has("lines") ? toStringList(w.get("lines")) : List.of());
            writeAnimatedText(out, path + ".title", w.has("title") ? w.getAsJsonObject("title") : null);
        }
        if (!out.contains("worlds")) {
            out.createSection("worlds");
        }

        if (!saveYaml(exchange, out, SCOREBOARD_FILE)) {
            return;
        }
        if (plugin.getScoreboardModule() != null) {
            plugin.getScoreboardModule().reload();
        }

        JsonObject response = scoreboardToJson(out);
        addModuleStatus(response, "scoreboard");
        response.addProperty("saved", true);
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(response));
    }

    // ─── GET/POST /config/tablist ────────────────────────────────────

    private static final String TABLIST_FILE = "tablist.yml";

    private void getTablist(HttpExchange exchange) throws IOException {
        File file = new File(plugin.getDataFolder(), TABLIST_FILE);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        JsonObject root = tablistToJson(yaml);
        addModuleStatus(root, "tablist");
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(root));
    }

    private JsonObject tablistToJson(YamlConfiguration yaml) {
        JsonObject root = new JsonObject();
        root.addProperty("updateIntervalTicks", yaml.getLong("update-interval-ticks", 20L));
        JsonObject worlds = new JsonObject();
        ConfigurationSection worldsSection = yaml.getConfigurationSection("worlds");
        if (worldsSection != null) {
            for (String key : worldsSection.getKeys(false)) {
                ConfigurationSection worldSection = worldsSection.getConfigurationSection(key);
                JsonObject obj = new JsonObject();
                obj.add("header", animatedTextToJson(worldSection, "header"));
                obj.add("footer", animatedTextToJson(worldSection, "footer"));
                worlds.add(key, obj);
            }
        }
        root.add("worlds", worlds);
        return root;
    }

    private void postTablist(HttpExchange exchange) throws IOException {
        JsonObject body = readBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body.");
            return;
        }

        long updateIntervalTicks = 20L;
        if (body.has("updateIntervalTicks")) {
            JsonElement el = body.get("updateIntervalTicks");
            if (!isNumber(el)) {
                sendError(exchange, 400, "updateIntervalTicks must be a number.");
                return;
            }
            updateIntervalTicks = Math.max(1L, el.getAsLong());
        }

        if (!body.has("worlds") || !body.get("worlds").isJsonObject()) {
            sendError(exchange, 400, "worlds must be an object of world name -> {header, footer}.");
            return;
        }
        JsonObject worldsObj = body.getAsJsonObject("worlds");

        for (Map.Entry<String, JsonElement> entry : worldsObj.entrySet()) {
            String field = "worlds." + entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                sendError(exchange, 400, field + " must be an object.");
                return;
            }
            JsonObject w = entry.getValue().getAsJsonObject();
            String headerError = validateAnimatedTextField(w, "header", field + ".header");
            if (headerError != null) {
                sendError(exchange, 400, headerError);
                return;
            }
            String footerError = validateAnimatedTextField(w, "footer", field + ".footer");
            if (footerError != null) {
                sendError(exchange, 400, footerError);
                return;
            }
        }

        YamlConfiguration out = new YamlConfiguration();
        out.set("update-interval-ticks", updateIntervalTicks);
        for (Map.Entry<String, JsonElement> entry : worldsObj.entrySet()) {
            JsonObject w = entry.getValue().getAsJsonObject();
            String path = "worlds." + entry.getKey();
            writeAnimatedText(out, path + ".header", w.has("header") ? w.getAsJsonObject("header") : null);
            writeAnimatedText(out, path + ".footer", w.has("footer") ? w.getAsJsonObject("footer") : null);
        }
        if (!out.contains("worlds")) {
            out.createSection("worlds");
        }

        if (!saveYaml(exchange, out, TABLIST_FILE)) {
            return;
        }
        if (plugin.getTablistModule() != null) {
            plugin.getTablistModule().reload();
        }

        JsonObject response = tablistToJson(out);
        addModuleStatus(response, "tablist");
        response.addProperty("saved", true);
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(response));
    }

    // ─── GET/POST /config/announcements ─────────────────────────────

    private static final String ANNOUNCEMENTS_FILE = "announcements.yml";

    private void getAnnouncements(HttpExchange exchange) throws IOException {
        File file = new File(plugin.getDataFolder(), ANNOUNCEMENTS_FILE);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        JsonObject root = announcementsToJson(yaml);
        addModuleStatus(root, "announcements");
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(root));
    }

    private JsonObject announcementsToJson(YamlConfiguration yaml) {
        JsonObject root = new JsonObject();
        root.addProperty("checkIntervalTicks", yaml.getLong("check-interval-ticks", 20L));
        root.addProperty("defaultIntervalTicks", yaml.getLong("default-interval-ticks", 600L));
        JsonObject worlds = new JsonObject();
        ConfigurationSection worldsSection = yaml.getConfigurationSection("worlds");
        if (worldsSection != null) {
            for (String key : worldsSection.getKeys(false)) {
                worlds.add(key, announcementWorldToJson(worldsSection.getConfigurationSection(key)));
            }
        }
        root.add("worlds", worlds);
        return root;
    }

    private JsonObject announcementWorldToJson(ConfigurationSection worldSection) {
        JsonObject obj = new JsonObject();
        if (worldSection == null) {
            obj.addProperty("rotation", "SEQUENTIAL");
            obj.add("intervalTicksOverride", JsonNull.INSTANCE);
            obj.add("entries", new JsonArray());
            return obj;
        }
        obj.addProperty("rotation", worldSection.getString("rotation", "sequential").toUpperCase(Locale.ROOT));
        if (worldSection.isSet("interval-ticks")) {
            obj.addProperty("intervalTicksOverride", worldSection.getLong("interval-ticks"));
        } else {
            obj.add("intervalTicksOverride", JsonNull.INSTANCE);
        }
        JsonArray entries = new JsonArray();
        for (Map<?, ?> raw : worldSection.getMapList("entries")) {
            JsonObject entryObj = new JsonObject();
            JsonArray actions = new JsonArray();
            Object actionsObj = raw.get("actions");
            if (actionsObj instanceof List<?> list) {
                for (Object a : list) {
                    if (a != null) {
                        actions.add(a.toString());
                    }
                }
            }
            entryObj.add("actions", actions);
            entries.add(entryObj);
        }
        obj.add("entries", entries);
        return obj;
    }

    private void postAnnouncements(HttpExchange exchange) throws IOException {
        JsonObject body = readBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body.");
            return;
        }

        long checkIntervalTicks = 20L;
        if (body.has("checkIntervalTicks")) {
            JsonElement el = body.get("checkIntervalTicks");
            if (!isNumber(el)) {
                sendError(exchange, 400, "checkIntervalTicks must be a number.");
                return;
            }
            checkIntervalTicks = Math.max(1L, el.getAsLong());
        }
        long defaultIntervalTicks = 600L;
        if (body.has("defaultIntervalTicks")) {
            JsonElement el = body.get("defaultIntervalTicks");
            if (!isNumber(el)) {
                sendError(exchange, 400, "defaultIntervalTicks must be a number.");
                return;
            }
            defaultIntervalTicks = Math.max(1L, el.getAsLong());
        }

        if (!body.has("worlds") || !body.get("worlds").isJsonObject()) {
            sendError(exchange, 400, "worlds must be an object of world name -> {rotation, intervalTicksOverride, entries}.");
            return;
        }
        JsonObject worldsObj = body.getAsJsonObject("worlds");

        // Validate everything before writing anything.
        for (Map.Entry<String, JsonElement> we : worldsObj.entrySet()) {
            String field = "worlds." + we.getKey();
            if (!we.getValue().isJsonObject()) {
                sendError(exchange, 400, field + " must be an object.");
                return;
            }
            JsonObject w = we.getValue().getAsJsonObject();
            if (w.has("rotation")) {
                JsonElement r = w.get("rotation");
                String raw = r.isJsonPrimitive() ? r.getAsString() : null;
                if (!isValidRotation(raw)) {
                    sendError(exchange, 400, field + ".rotation must be 'SEQUENTIAL' or 'RANDOM'.");
                    return;
                }
            }
            if (w.has("intervalTicksOverride") && !w.get("intervalTicksOverride").isJsonNull()) {
                if (!isNumber(w.get("intervalTicksOverride"))) {
                    sendError(exchange, 400, field + ".intervalTicksOverride must be a number or null.");
                    return;
                }
            }
            if (!w.has("entries") || !w.get("entries").isJsonArray()) {
                sendError(exchange, 400, field + ".entries must be an array.");
                return;
            }
            int idx = 0;
            for (JsonElement entryEl : w.getAsJsonArray("entries")) {
                idx++;
                String entryField = field + ".entries[" + idx + "]";
                if (!entryEl.isJsonObject() || !entryEl.getAsJsonObject().has("actions")
                        || !isStringArray(entryEl.getAsJsonObject().get("actions"))
                        || entryEl.getAsJsonObject().getAsJsonArray("actions").isEmpty()) {
                    sendError(exchange, 400, entryField + " must have a non-empty 'actions' array of strings.");
                    return;
                }
                for (JsonElement actionEl : entryEl.getAsJsonObject().getAsJsonArray("actions")) {
                    String action = actionEl.getAsString();
                    if (!ACTION_LINE_PATTERN.matcher(action.trim()).matches()) {
                        sendError(exchange, 400, entryField + " has a malformed action (expected \"[tag] argument\"): \"" + action + "\"");
                        return;
                    }
                }
            }
        }

        YamlConfiguration out = new YamlConfiguration();
        out.set("check-interval-ticks", checkIntervalTicks);
        out.set("default-interval-ticks", defaultIntervalTicks);
        for (Map.Entry<String, JsonElement> we : worldsObj.entrySet()) {
            JsonObject w = we.getValue().getAsJsonObject();
            String path = "worlds." + we.getKey();
            String rotation = w.has("rotation") ? w.get("rotation").getAsString().trim().toUpperCase(Locale.ROOT) : "SEQUENTIAL";
            out.set(path + ".rotation", rotation.toLowerCase(Locale.ROOT));
            if (w.has("intervalTicksOverride") && !w.get("intervalTicksOverride").isJsonNull()) {
                out.set(path + ".interval-ticks", w.get("intervalTicksOverride").getAsLong());
            }
            List<Map<String, Object>> entriesOut = new ArrayList<>();
            for (JsonElement entryEl : w.getAsJsonArray("entries")) {
                List<String> actions = toStringList(entryEl.getAsJsonObject().get("actions"));
                Map<String, Object> entryMap = new LinkedHashMap<>();
                entryMap.put("actions", actions);
                entriesOut.add(entryMap);
            }
            out.set(path + ".entries", entriesOut);
        }
        if (!out.contains("worlds")) {
            out.createSection("worlds");
        }

        if (!saveYaml(exchange, out, ANNOUNCEMENTS_FILE)) {
            return;
        }
        if (plugin.getAnnouncementsModule() != null) {
            plugin.getAnnouncementsModule().reload();
        }

        JsonObject response = announcementsToJson(out);
        addModuleStatus(response, "announcements");
        response.addProperty("saved", true);
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(response));
    }

    // ─── GET/POST /config/world-protection ──────────────────────────

    /**
     * Reads world-protection.* straight off {@code plugin.getConfig()} — the SAME live,
     * already-loaded {@link org.bukkit.configuration.file.FileConfiguration} object
     * {@code /config/core} above reads/writes, NOT a fresh file load. This matters for
     * the write side ({@link #postWorldProtection}): every field is applied via {@code
     * cfg.set(...)} onto that live object and only the keys present in the request body
     * are ever touched, so a save here can never silently wipe {@code server-role},
     * {@code hub-worlds}, {@code modules}, {@code compatibility}, or any other
     * config.yml section this endpoint doesn't know about — the exact bug class a
     * from-scratch {@code YamlConfiguration} rebuild caused in SwagTournaments.
     */
    private JsonObject buildWorldProtectionState() {
        var cfg = plugin.getConfig();
        JsonObject root = new JsonObject();
        root.addProperty("denyBlockBreak", cfg.getBoolean("world-protection.deny-block-break", true));
        root.addProperty("denyBlockPlace", cfg.getBoolean("world-protection.deny-block-place", true));
        root.addProperty("disableHunger", cfg.getBoolean("world-protection.disable-hunger", true));
        root.addProperty("disableFallDamage", cfg.getBoolean("world-protection.disable-fall-damage", true));
        root.addProperty("disableAllDamage", cfg.getBoolean("world-protection.disable-all-damage", false));
        root.addProperty("disablePvp", cfg.getBoolean("world-protection.disable-pvp", true));
        root.add("pvpZones", pvpZonesToJson(cfg.getMapList("world-protection.pvp-zones")));
        root.addProperty("lockWeather", cfg.getBoolean("world-protection.lock-weather", true));
        root.addProperty("clearWeather", cfg.getBoolean("world-protection.clear-weather", true));
        root.addProperty("lockTime", cfg.getBoolean("world-protection.lock-time", true));
        root.addProperty("fixedTime", cfg.getLong("world-protection.fixed-time", 6000L));
        root.addProperty("denyMobSpawning", cfg.getBoolean("world-protection.deny-mob-spawning", true));
        root.addProperty("denyItemDrop", cfg.getBoolean("world-protection.deny-item-drop", true));
        root.addProperty("denyItemPickup", cfg.getBoolean("world-protection.deny-item-pickup", true));
        root.addProperty("denyLeafDecay", cfg.getBoolean("world-protection.deny-leaf-decay", true));
        root.addProperty("denyFireSpread", cfg.getBoolean("world-protection.deny-fire-spread", true));
        root.addProperty("denyBlockBurn", cfg.getBoolean("world-protection.deny-block-burn", true));
        root.addProperty("denyTnt", cfg.getBoolean("world-protection.deny-tnt", true));
        return root;
    }

    /** Mirrors {@code WorldProtectionModule#readSettings}'s own tolerant parsing — one malformed zone never aborts the rest. */
    private JsonArray pvpZonesToJson(List<Map<?, ?>> rawZones) {
        JsonArray arr = new JsonArray();
        for (Map<?, ?> zoneMap : rawZones) {
            try {
                Map<?, ?> c1 = (Map<?, ?>) zoneMap.get("corner1");
                Map<?, ?> c2 = (Map<?, ?>) zoneMap.get("corner2");
                JsonObject zoneObj = new JsonObject();
                zoneObj.addProperty("name", String.valueOf(zoneMap.get("name")));
                zoneObj.addProperty("world", String.valueOf(zoneMap.get("world")));
                zoneObj.add("corner1", cornerToJson(c1));
                zoneObj.add("corner2", cornerToJson(c2));
                arr.add(zoneObj);
            } catch (Exception ex) {
                plugin.getLogger().warning("Web editor: skipping malformed entry in world-protection.pvp-zones: " + zoneMap);
            }
        }
        return arr;
    }

    private JsonObject cornerToJson(Map<?, ?> corner) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", ((Number) corner.get("x")).doubleValue());
        obj.addProperty("y", ((Number) corner.get("y")).doubleValue());
        obj.addProperty("z", ((Number) corner.get("z")).doubleValue());
        return obj;
    }

    private void getWorldProtection(HttpExchange exchange) throws IOException {
        JsonObject root = buildWorldProtectionState();
        addModuleStatus(root, "world-protection");
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(root));
    }

    private static final String[] WORLD_PROTECTION_BOOLEAN_KEYS = {
            "denyBlockBreak", "denyBlockPlace", "disableHunger", "disableFallDamage",
            "disableAllDamage", "disablePvp", "lockWeather", "clearWeather", "lockTime",
            "denyMobSpawning", "denyItemDrop", "denyItemPickup", "denyLeafDecay",
            "denyFireSpread", "denyBlockBurn", "denyTnt"
    };

    private void postWorldProtection(HttpExchange exchange) throws IOException {
        JsonObject body = readBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body.");
            return;
        }

        // ── Validate everything BEFORE touching any config state (§10.4/§7.4). ──
        Map<String, Boolean> boolFields = new LinkedHashMap<>();
        for (String key : WORLD_PROTECTION_BOOLEAN_KEYS) {
            if (body.has(key)) {
                JsonElement el = body.get(key);
                if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean()) {
                    sendError(exchange, 400, key + " must be a boolean.");
                    return;
                }
                boolFields.put(key, el.getAsBoolean());
            }
        }

        Long fixedTime = null;
        if (body.has("fixedTime")) {
            JsonElement el = body.get("fixedTime");
            if (!isNumber(el)) {
                sendError(exchange, 400, "fixedTime must be a number.");
                return;
            }
            fixedTime = el.getAsLong();
        }

        List<Map<String, Object>> zonesOut = null;
        if (body.has("pvpZones")) {
            JsonElement el = body.get("pvpZones");
            if (!el.isJsonArray()) {
                sendError(exchange, 400, "pvpZones must be an array.");
                return;
            }
            zonesOut = new ArrayList<>();
            int idx = 0;
            for (JsonElement zoneEl : el.getAsJsonArray()) {
                idx++;
                String field = "pvpZones[" + idx + "]";
                if (!zoneEl.isJsonObject()) {
                    sendError(exchange, 400, field + " must be an object.");
                    return;
                }
                JsonObject zoneObj = zoneEl.getAsJsonObject();
                if (!zoneObj.has("name") || !zoneObj.get("name").isJsonPrimitive()) {
                    sendError(exchange, 400, field + ".name is required.");
                    return;
                }
                if (!zoneObj.has("world") || !zoneObj.get("world").isJsonPrimitive()) {
                    sendError(exchange, 400, field + ".world is required.");
                    return;
                }
                Map<String, Object> c1 = parseCorner(zoneObj, "corner1");
                if (c1 == null) {
                    sendError(exchange, 400, field + ".corner1 must have numeric x, y, z.");
                    return;
                }
                Map<String, Object> c2 = parseCorner(zoneObj, "corner2");
                if (c2 == null) {
                    sendError(exchange, 400, field + ".corner2 must have numeric x, y, z.");
                    return;
                }
                Map<String, Object> zoneMap = new LinkedHashMap<>();
                zoneMap.put("name", zoneObj.get("name").getAsString());
                zoneMap.put("world", zoneObj.get("world").getAsString());
                zoneMap.put("corner1", c1);
                zoneMap.put("corner2", c2);
                zonesOut.add(zoneMap);
            }
        }

        // ── All validation passed — apply onto the LIVE config object (never a blank
        //    rebuild), save, reload. Only keys present in the body are ever touched. ──
        var cfg = plugin.getConfig();
        for (Map.Entry<String, Boolean> entry : boolFields.entrySet()) {
            cfg.set("world-protection." + camelToKebab(entry.getKey()), entry.getValue());
        }
        if (fixedTime != null) {
            cfg.set("world-protection.fixed-time", fixedTime);
        }
        if (zonesOut != null) {
            cfg.set("world-protection.pvp-zones", zonesOut);
        }

        plugin.getConfigManager().save();
        plugin.getModuleManager().getModule("world-protection").ifPresent(Module::reload);

        JsonObject response = buildWorldProtectionState();
        addModuleStatus(response, "world-protection");
        response.addProperty("saved", true);
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(response));
    }

    /** Returns a {@code {x,y,z}} map if {@code zoneObj.<cornerKey>} is a valid numeric-triple object, otherwise {@code null}. */
    private Map<String, Object> parseCorner(JsonObject zoneObj, String cornerKey) {
        if (!zoneObj.has(cornerKey) || !zoneObj.get(cornerKey).isJsonObject()) {
            return null;
        }
        JsonObject c = zoneObj.getAsJsonObject(cornerKey);
        if (!isNumber(c.get("x")) || !isNumber(c.get("y")) || !isNumber(c.get("z"))) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", c.get("x").getAsDouble());
        map.put("y", c.get("y").getAsDouble());
        map.put("z", c.get("z").getAsDouble());
        return map;
    }

    /** {@code "denyBlockBreak"} -&gt; {@code "deny-block-break"} — the config.yml key-naming convention used throughout this file. */
    private static String camelToKebab(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('-').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ─── GET/POST /config/join-spawn ─────────────────────────────────

    /**
     * Bundles three unrelated-but-small modules into one tab/one endpoint, exactly the
     * way {@code /config/core} bundles server-role/hub-worlds/modules/compatibility —
     * "join-settings", "spawn", and "double-jump" each get their own nested JSON object
     * (with their own {@link #addModuleStatus} block) so the frontend can show
     * per-module enabled/yielded state independently even though they save together.
     * Reads/writes the SAME live {@code plugin.getConfig()} object every other
     * config.yml-backed endpoint above does — never a from-scratch rebuild.
     */
    private static final String[] JOIN_SETTINGS_BOOL_FIELDS = {
            "clearInventory", "setGamemode", "healAndFeed", "joinFirework"
    };
    private static final String[] SPAWN_BOOL_FIELDS = {
            "cancelOnMove", "spawnOnJoin", "spawnOnVoidFall", "spawnOnRespawn"
    };

    private JsonObject buildJoinSpawnState() {
        var cfg = plugin.getConfig();
        JsonObject root = new JsonObject();

        JsonObject joinSettings = new JsonObject();
        joinSettings.addProperty("clearInventory", cfg.getBoolean("join-settings.clear-inventory", true));
        joinSettings.addProperty("setGamemode", cfg.getBoolean("join-settings.set-gamemode", true));
        joinSettings.addProperty("gamemode", cfg.getString("join-settings.gamemode", "ADVENTURE"));
        joinSettings.addProperty("healAndFeed", cfg.getBoolean("join-settings.heal-and-feed", true));
        joinSettings.addProperty("joinFirework", cfg.getBoolean("join-settings.join-firework", true));
        joinSettings.add("firstJoinActions", gson.toJsonTree(cfg.getStringList("join-settings.first-join.actions")));
        addModuleStatus(joinSettings, "join-settings");
        root.add("joinSettings", joinSettings);

        JsonObject spawn = new JsonObject();
        spawn.addProperty("lobbyTeleportDelayTicks", cfg.getLong("spawn.lobby-teleport-delay-ticks", 60L));
        spawn.addProperty("cancelOnMove", cfg.getBoolean("spawn.cancel-on-move", true));
        spawn.addProperty("spawnOnJoin", cfg.getBoolean("spawn.spawn-on-join", true));
        spawn.addProperty("spawnOnVoidFall", cfg.getBoolean("spawn.spawn-on-void-fall", true));
        spawn.addProperty("spawnOnRespawn", cfg.getBoolean("spawn.spawn-on-respawn", true));
        addModuleStatus(spawn, "spawn");
        root.add("spawn", spawn);

        JsonObject doubleJump = new JsonObject();
        doubleJump.addProperty("power", cfg.getDouble("double-jump.power", 1.4));
        doubleJump.addProperty("height", cfg.getDouble("double-jump.height", 1.2));
        doubleJump.addProperty("particle", cfg.getString("double-jump.particle", "CLOUD"));
        doubleJump.addProperty("sound", cfg.getString("double-jump.sound", "ENTITY_BAT_TAKEOFF"));
        doubleJump.addProperty("bedrock", cfg.getBoolean("double-jump.bedrock", true));
        doubleJump.add("regions", regionsToJson(cfg.getMapList("double-jump.regions")));
        addModuleStatus(doubleJump, "double-jump");
        root.add("doubleJump", doubleJump);

        return root;
    }

    /** Same tolerant, one-bad-entry-never-aborts-the-rest shape as {@link #pvpZonesToJson} — see that method's javadoc. */
    private JsonArray regionsToJson(List<Map<?, ?>> rawRegions) {
        JsonArray arr = new JsonArray();
        for (Map<?, ?> regionMap : rawRegions) {
            try {
                Map<?, ?> c1 = (Map<?, ?>) regionMap.get("corner1");
                Map<?, ?> c2 = (Map<?, ?>) regionMap.get("corner2");
                JsonObject regionObj = new JsonObject();
                regionObj.addProperty("name", String.valueOf(regionMap.get("name")));
                regionObj.addProperty("world", String.valueOf(regionMap.get("world")));
                regionObj.add("corner1", cornerToJson(c1));
                regionObj.add("corner2", cornerToJson(c2));
                arr.add(regionObj);
            } catch (Exception ex) {
                plugin.getLogger().warning("Web editor: skipping malformed entry in double-jump.regions: " + regionMap);
            }
        }
        return arr;
    }

    private void getJoinSpawn(HttpExchange exchange) throws IOException {
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(buildJoinSpawnState()));
    }

    private void postJoinSpawn(HttpExchange exchange) throws IOException {
        JsonObject body = readBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body.");
            return;
        }

        // ── Validate everything BEFORE touching any config state. ──
        Map<String, Boolean> jsBoolFields = new LinkedHashMap<>();
        String gamemode = null;
        List<String> firstJoinActions = null;
        if (body.has("joinSettings")) {
            JsonElement el = body.get("joinSettings");
            if (!el.isJsonObject()) {
                sendError(exchange, 400, "joinSettings must be an object.");
                return;
            }
            JsonObject js = el.getAsJsonObject();
            for (String key : JOIN_SETTINGS_BOOL_FIELDS) {
                if (js.has(key)) {
                    JsonElement v = js.get(key);
                    if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) {
                        sendError(exchange, 400, "joinSettings." + key + " must be a boolean.");
                        return;
                    }
                    jsBoolFields.put(key, v.getAsBoolean());
                }
            }
            if (js.has("gamemode")) {
                JsonElement v = js.get("gamemode");
                String raw = v.isJsonPrimitive() ? v.getAsString() : null;
                if (!isValidGameMode(raw)) {
                    sendError(exchange, 400, "joinSettings.gamemode must be a valid GameMode (SURVIVAL, CREATIVE, ADVENTURE, or SPECTATOR).");
                    return;
                }
                gamemode = raw.trim().toUpperCase(Locale.ROOT);
            }
            if (js.has("firstJoinActions")) {
                JsonElement v = js.get("firstJoinActions");
                if (!isStringArray(v)) {
                    sendError(exchange, 400, "joinSettings.firstJoinActions must be an array of strings.");
                    return;
                }
                for (JsonElement actionEl : v.getAsJsonArray()) {
                    String action = actionEl.getAsString();
                    if (!ACTION_LINE_PATTERN.matcher(action.trim()).matches()) {
                        sendError(exchange, 400, "joinSettings.firstJoinActions has a malformed action (expected \"[tag] argument\"): \"" + action + "\"");
                        return;
                    }
                }
                firstJoinActions = toStringList(v);
            }
        }

        Map<String, Boolean> spawnBoolFields = new LinkedHashMap<>();
        Long lobbyDelayTicks = null;
        if (body.has("spawn")) {
            JsonElement el = body.get("spawn");
            if (!el.isJsonObject()) {
                sendError(exchange, 400, "spawn must be an object.");
                return;
            }
            JsonObject sp = el.getAsJsonObject();
            for (String key : SPAWN_BOOL_FIELDS) {
                if (sp.has(key)) {
                    JsonElement v = sp.get(key);
                    if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) {
                        sendError(exchange, 400, "spawn." + key + " must be a boolean.");
                        return;
                    }
                    spawnBoolFields.put(key, v.getAsBoolean());
                }
            }
            if (sp.has("lobbyTeleportDelayTicks")) {
                JsonElement v = sp.get("lobbyTeleportDelayTicks");
                if (!isNumber(v)) {
                    sendError(exchange, 400, "spawn.lobbyTeleportDelayTicks must be a number.");
                    return;
                }
                lobbyDelayTicks = Math.max(0L, v.getAsLong());
            }
        }

        Double djPower = null;
        Double djHeight = null;
        String djParticle = null;
        String djSound = null;
        Boolean djBedrock = null;
        List<Map<String, Object>> djRegions = null;
        if (body.has("doubleJump")) {
            JsonElement el = body.get("doubleJump");
            if (!el.isJsonObject()) {
                sendError(exchange, 400, "doubleJump must be an object.");
                return;
            }
            JsonObject dj = el.getAsJsonObject();
            if (dj.has("power")) {
                if (!isNumber(dj.get("power"))) {
                    sendError(exchange, 400, "doubleJump.power must be a number.");
                    return;
                }
                djPower = dj.get("power").getAsDouble();
            }
            if (dj.has("height")) {
                if (!isNumber(dj.get("height"))) {
                    sendError(exchange, 400, "doubleJump.height must be a number.");
                    return;
                }
                djHeight = dj.get("height").getAsDouble();
            }
            if (dj.has("particle")) {
                JsonElement v = dj.get("particle");
                String raw = v.isJsonPrimitive() ? v.getAsString() : null;
                if (!isValidParticle(raw)) {
                    sendError(exchange, 400, "doubleJump.particle must be a valid Bukkit Particle name (e.g. CLOUD).");
                    return;
                }
                djParticle = raw.trim().toUpperCase(Locale.ROOT);
            }
            if (dj.has("sound")) {
                JsonElement v = dj.get("sound");
                String raw = v.isJsonPrimitive() ? v.getAsString() : null;
                if (!isValidSound(raw)) {
                    sendError(exchange, 400, "doubleJump.sound must be a valid Bukkit Sound name (e.g. ENTITY_BAT_TAKEOFF).");
                    return;
                }
                djSound = raw.trim().toUpperCase(Locale.ROOT);
            }
            if (dj.has("bedrock")) {
                JsonElement v = dj.get("bedrock");
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) {
                    sendError(exchange, 400, "doubleJump.bedrock must be a boolean.");
                    return;
                }
                djBedrock = v.getAsBoolean();
            }
            if (dj.has("regions")) {
                djRegions = parseCornerZones(exchange, dj.get("regions"), "doubleJump.regions");
                if (djRegions == null) {
                    return; // error already sent by parseCornerZones
                }
            }
        }

        // ── All validation passed — apply onto the LIVE config object, save, reload. ──
        var cfg = plugin.getConfig();
        for (Map.Entry<String, Boolean> entry : jsBoolFields.entrySet()) {
            cfg.set("join-settings." + camelToKebab(entry.getKey()), entry.getValue());
        }
        if (gamemode != null) {
            cfg.set("join-settings.gamemode", gamemode);
        }
        if (firstJoinActions != null) {
            cfg.set("join-settings.first-join.actions", firstJoinActions);
        }

        for (Map.Entry<String, Boolean> entry : spawnBoolFields.entrySet()) {
            cfg.set("spawn." + camelToKebab(entry.getKey()), entry.getValue());
        }
        if (lobbyDelayTicks != null) {
            cfg.set("spawn.lobby-teleport-delay-ticks", lobbyDelayTicks);
        }

        if (djPower != null) {
            cfg.set("double-jump.power", djPower);
        }
        if (djHeight != null) {
            cfg.set("double-jump.height", djHeight);
        }
        if (djParticle != null) {
            cfg.set("double-jump.particle", djParticle);
        }
        if (djSound != null) {
            cfg.set("double-jump.sound", djSound);
        }
        if (djBedrock != null) {
            cfg.set("double-jump.bedrock", djBedrock);
        }
        if (djRegions != null) {
            cfg.set("double-jump.regions", djRegions);
        }

        plugin.getConfigManager().save();
        plugin.getModuleManager().getModule("join-settings").ifPresent(Module::reload);
        plugin.getModuleManager().getModule("spawn").ifPresent(Module::reload);
        plugin.getModuleManager().getModule("double-jump").ifPresent(Module::reload);

        JsonObject response = buildJoinSpawnState();
        response.addProperty("saved", true);
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(response));
    }

    /**
     * Shared two-corner-cuboid array parser — same shape {@code world-protection.pvp-zones}
     * uses (see {@link #parseCorner}), reused here for {@code double-jump.regions} rather
     * than duplicating the corner-parsing logic itself. Sends its own {@code 400} and
     * returns {@code null} on any validation failure (an empty-but-valid array returns a
     * non-null empty list, so {@code null} unambiguously means "already handled").
     */
    private List<Map<String, Object>> parseCornerZones(HttpExchange exchange, JsonElement el, String fieldPrefix) throws IOException {
        if (!el.isJsonArray()) {
            sendError(exchange, 400, fieldPrefix + " must be an array.");
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int idx = 0;
        for (JsonElement zoneEl : el.getAsJsonArray()) {
            idx++;
            String field = fieldPrefix + "[" + idx + "]";
            if (!zoneEl.isJsonObject()) {
                sendError(exchange, 400, field + " must be an object.");
                return null;
            }
            JsonObject zoneObj = zoneEl.getAsJsonObject();
            if (!zoneObj.has("name") || !zoneObj.get("name").isJsonPrimitive()) {
                sendError(exchange, 400, field + ".name is required.");
                return null;
            }
            if (!zoneObj.has("world") || !zoneObj.get("world").isJsonPrimitive()) {
                sendError(exchange, 400, field + ".world is required.");
                return null;
            }
            Map<String, Object> c1 = parseCorner(zoneObj, "corner1");
            if (c1 == null) {
                sendError(exchange, 400, field + ".corner1 must have numeric x, y, z.");
                return null;
            }
            Map<String, Object> c2 = parseCorner(zoneObj, "corner2");
            if (c2 == null) {
                sendError(exchange, 400, field + ".corner2 must have numeric x, y, z.");
                return null;
            }
            Map<String, Object> zoneMap = new LinkedHashMap<>();
            zoneMap.put("name", zoneObj.get("name").getAsString());
            zoneMap.put("world", zoneObj.get("world").getAsString());
            zoneMap.put("corner1", c1);
            zoneMap.put("corner2", c2);
            out.add(zoneMap);
        }
        return out;
    }

    // ─── GET/POST /config/chat-controls ──────────────────────────────

    /** Bundles four small, never-yield-together modules (lockchat/clearchat/player-hider/anti-wdl) into one tab, same reasoning as {@link #buildJoinSpawnState}. */
    private JsonObject buildChatControlsState() {
        var cfg = plugin.getConfig();
        JsonObject root = new JsonObject();

        JsonObject lockchat = new JsonObject();
        lockchat.addProperty("cooldownSeconds", cfg.getLong("lockchat.cooldown-seconds", 0L));
        lockchat.addProperty("commandBlockerMode", cfg.getString("lockchat.command-blocker.mode", "blacklist"));
        lockchat.add("commandBlockerCommands", gson.toJsonTree(cfg.getStringList("lockchat.command-blocker.commands")));
        addModuleStatus(lockchat, "lockchat");
        root.add("lockchat", lockchat);

        JsonObject clearchat = new JsonObject();
        clearchat.addProperty("lines", cfg.getInt("clearchat.lines", 100));
        clearchat.addProperty("clearForEveryone", cfg.getBoolean("clearchat.clear-for-everyone", true));
        addModuleStatus(clearchat, "clearchat");
        root.add("clearchat", clearchat);

        JsonObject playerHider = new JsonObject();
        playerHider.addProperty("cooldownSeconds", cfg.getLong("player-hider.cooldown-seconds", 3L));
        addModuleStatus(playerHider, "player-hider");
        root.add("playerHider", playerHider);

        JsonObject antiWdl = new JsonObject();
        antiWdl.addProperty("action", cfg.getString("anti-wdl.action", "kick"));
        addModuleStatus(antiWdl, "anti-wdl");
        root.add("antiWdl", antiWdl);

        return root;
    }

    private void getChatControls(HttpExchange exchange) throws IOException {
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(buildChatControlsState()));
    }

    private void postChatControls(HttpExchange exchange) throws IOException {
        JsonObject body = readBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body.");
            return;
        }

        Long lockchatCooldown = null;
        String commandBlockerMode = null;
        List<String> commandBlockerCommands = null;
        if (body.has("lockchat")) {
            JsonElement el = body.get("lockchat");
            if (!el.isJsonObject()) {
                sendError(exchange, 400, "lockchat must be an object.");
                return;
            }
            JsonObject lc = el.getAsJsonObject();
            if (lc.has("cooldownSeconds")) {
                if (!isNumber(lc.get("cooldownSeconds"))) {
                    sendError(exchange, 400, "lockchat.cooldownSeconds must be a number.");
                    return;
                }
                lockchatCooldown = Math.max(0L, lc.get("cooldownSeconds").getAsLong());
            }
            if (lc.has("commandBlockerMode")) {
                JsonElement v = lc.get("commandBlockerMode");
                String raw = v.isJsonPrimitive() ? v.getAsString() : null;
                if (!isValidCommandBlockerMode(raw)) {
                    sendError(exchange, 400, "lockchat.commandBlockerMode must be 'blacklist' or 'whitelist'.");
                    return;
                }
                commandBlockerMode = raw.trim().toLowerCase(Locale.ROOT);
            }
            if (lc.has("commandBlockerCommands")) {
                JsonElement v = lc.get("commandBlockerCommands");
                if (!isStringArray(v)) {
                    sendError(exchange, 400, "lockchat.commandBlockerCommands must be an array of strings.");
                    return;
                }
                commandBlockerCommands = toStringList(v);
            }
        }

        Integer clearchatLines = null;
        Boolean clearForEveryone = null;
        if (body.has("clearchat")) {
            JsonElement el = body.get("clearchat");
            if (!el.isJsonObject()) {
                sendError(exchange, 400, "clearchat must be an object.");
                return;
            }
            JsonObject cc = el.getAsJsonObject();
            if (cc.has("lines")) {
                if (!isNumber(cc.get("lines"))) {
                    sendError(exchange, 400, "clearchat.lines must be a number.");
                    return;
                }
                clearchatLines = Math.max(1, cc.get("lines").getAsInt());
            }
            if (cc.has("clearForEveryone")) {
                JsonElement v = cc.get("clearForEveryone");
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) {
                    sendError(exchange, 400, "clearchat.clearForEveryone must be a boolean.");
                    return;
                }
                clearForEveryone = v.getAsBoolean();
            }
        }

        Long playerHiderCooldown = null;
        if (body.has("playerHider")) {
            JsonElement el = body.get("playerHider");
            if (!el.isJsonObject()) {
                sendError(exchange, 400, "playerHider must be an object.");
                return;
            }
            JsonObject ph = el.getAsJsonObject();
            if (ph.has("cooldownSeconds")) {
                if (!isNumber(ph.get("cooldownSeconds"))) {
                    sendError(exchange, 400, "playerHider.cooldownSeconds must be a number.");
                    return;
                }
                playerHiderCooldown = Math.max(0L, ph.get("cooldownSeconds").getAsLong());
            }
        }

        String antiWdlAction = null;
        if (body.has("antiWdl")) {
            JsonElement el = body.get("antiWdl");
            if (!el.isJsonObject()) {
                sendError(exchange, 400, "antiWdl must be an object.");
                return;
            }
            JsonObject aw = el.getAsJsonObject();
            if (aw.has("action")) {
                JsonElement v = aw.get("action");
                String raw = v.isJsonPrimitive() ? v.getAsString() : null;
                if (!isValidWdlAction(raw)) {
                    sendError(exchange, 400, "antiWdl.action must be 'kick' or 'warn'.");
                    return;
                }
                antiWdlAction = raw.trim().toLowerCase(Locale.ROOT);
            }
        }

        // ── All validation passed — apply onto the LIVE config object, save, reload. ──
        var cfg = plugin.getConfig();
        if (lockchatCooldown != null) {
            cfg.set("lockchat.cooldown-seconds", lockchatCooldown);
        }
        if (commandBlockerMode != null) {
            cfg.set("lockchat.command-blocker.mode", commandBlockerMode);
        }
        if (commandBlockerCommands != null) {
            cfg.set("lockchat.command-blocker.commands", commandBlockerCommands);
        }
        if (clearchatLines != null) {
            cfg.set("clearchat.lines", clearchatLines);
        }
        if (clearForEveryone != null) {
            cfg.set("clearchat.clear-for-everyone", clearForEveryone);
        }
        if (playerHiderCooldown != null) {
            cfg.set("player-hider.cooldown-seconds", playerHiderCooldown);
        }
        if (antiWdlAction != null) {
            cfg.set("anti-wdl.action", antiWdlAction);
        }

        plugin.getConfigManager().save();
        plugin.getModuleManager().getModule("lockchat").ifPresent(Module::reload);
        plugin.getModuleManager().getModule("clearchat").ifPresent(Module::reload);
        plugin.getModuleManager().getModule("player-hider").ifPresent(Module::reload);
        plugin.getModuleManager().getModule("anti-wdl").ifPresent(Module::reload);

        JsonObject response = buildChatControlsState();
        response.addProperty("saved", true);
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(response));
    }

    // ─── GET/POST /config/network ─────────────────────────────────────

    /**
     * Bundles {@code proxy} and {@code network} (the {@code networkstats} module's
     * config section — its module key and config-section name deliberately differ, see
     * {@link com.SwagDev.SwagHub.modules.networkstats.NetworkStatsModule}) into one tab.
     * {@code network.shared-secret} is sensitive: it is returned as plain text in the
     * GET response (this endpoint is already gated behind {@code swaghub.dashboard.view},
     * the same trust boundary every other secret-bearing admin surface in this plugin
     * uses) but is NEVER written to any log line in this class, unlike, say, a failed
     * save's exception message elsewhere in this file.
     */
    private JsonObject buildNetworkState() {
        var cfg = plugin.getConfig();
        JsonObject root = new JsonObject();

        JsonObject proxy = new JsonObject();
        proxy.addProperty("pollIntervalSeconds", cfg.getLong("proxy.poll-interval-seconds", 10L));
        proxy.add("servers", gson.toJsonTree(cfg.getStringList("proxy.servers")));
        proxy.addProperty("connectTimeoutTicks", cfg.getLong("proxy.connect-timeout-ticks", 40L));
        addModuleStatus(proxy, "proxy");
        root.add("proxy", proxy);

        JsonObject network = new JsonObject();
        network.addProperty("sharedSecret", cfg.getString("network.shared-secret", ""));
        JsonObject knownServers = new JsonObject();
        ConfigurationSection section = cfg.getConfigurationSection("network.known-servers");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                knownServers.addProperty(id, section.getString(id, ""));
            }
        }
        network.add("knownServers", knownServers);
        addModuleStatus(network, "networkstats");
        root.add("network", network);

        return root;
    }

    private void getNetwork(HttpExchange exchange) throws IOException {
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(buildNetworkState()));
    }

    private void postNetwork(HttpExchange exchange) throws IOException {
        JsonObject body = readBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body.");
            return;
        }

        Long pollIntervalSeconds = null;
        List<String> proxyServers = null;
        Long connectTimeoutTicks = null;
        if (body.has("proxy")) {
            JsonElement el = body.get("proxy");
            if (!el.isJsonObject()) {
                sendError(exchange, 400, "proxy must be an object.");
                return;
            }
            JsonObject px = el.getAsJsonObject();
            if (px.has("pollIntervalSeconds")) {
                if (!isNumber(px.get("pollIntervalSeconds"))) {
                    sendError(exchange, 400, "proxy.pollIntervalSeconds must be a number.");
                    return;
                }
                pollIntervalSeconds = Math.max(1L, px.get("pollIntervalSeconds").getAsLong());
            }
            if (px.has("servers")) {
                if (!isStringArray(px.get("servers"))) {
                    sendError(exchange, 400, "proxy.servers must be an array of strings.");
                    return;
                }
                proxyServers = toStringList(px.get("servers"));
            }
            if (px.has("connectTimeoutTicks")) {
                if (!isNumber(px.get("connectTimeoutTicks"))) {
                    sendError(exchange, 400, "proxy.connectTimeoutTicks must be a number.");
                    return;
                }
                connectTimeoutTicks = Math.max(0L, px.get("connectTimeoutTicks").getAsLong());
            }
        }

        String sharedSecret = null;
        Map<String, String> knownServers = null;
        if (body.has("network")) {
            JsonElement el = body.get("network");
            if (!el.isJsonObject()) {
                sendError(exchange, 400, "network must be an object.");
                return;
            }
            JsonObject nw = el.getAsJsonObject();
            if (nw.has("sharedSecret")) {
                JsonElement v = nw.get("sharedSecret");
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) {
                    sendError(exchange, 400, "network.sharedSecret must be a string.");
                    return;
                }
                sharedSecret = v.getAsString();
            }
            if (nw.has("knownServers")) {
                JsonElement v = nw.get("knownServers");
                if (!v.isJsonObject()) {
                    sendError(exchange, 400, "network.knownServers must be an object of server-id -> URL.");
                    return;
                }
                knownServers = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> entry : v.getAsJsonObject().entrySet()) {
                    JsonElement urlEl = entry.getValue();
                    if (!urlEl.isJsonPrimitive() || !urlEl.getAsJsonPrimitive().isString()) {
                        sendError(exchange, 400, "network.knownServers." + entry.getKey() + " must be a string URL.");
                        return;
                    }
                    knownServers.put(entry.getKey(), urlEl.getAsString());
                }
            }
        }

        // ── All validation passed — apply onto the LIVE config object, save, reload.
        //    sharedSecret is sensitive: it is set via cfg.set() like any other value but
        //    is NEVER passed to plugin.getLogger() anywhere in this method. ──
        var cfg = plugin.getConfig();
        if (pollIntervalSeconds != null) {
            cfg.set("proxy.poll-interval-seconds", pollIntervalSeconds);
        }
        if (proxyServers != null) {
            cfg.set("proxy.servers", proxyServers);
        }
        if (connectTimeoutTicks != null) {
            cfg.set("proxy.connect-timeout-ticks", connectTimeoutTicks);
        }
        if (sharedSecret != null) {
            cfg.set("network.shared-secret", sharedSecret);
        }
        if (knownServers != null) {
            cfg.set("network.known-servers", null);
            for (Map.Entry<String, String> entry : knownServers.entrySet()) {
                cfg.set("network.known-servers." + entry.getKey(), entry.getValue());
            }
        }

        plugin.getConfigManager().save();
        plugin.getModuleManager().getModule("proxy").ifPresent(Module::reload);
        plugin.getModuleManager().getModule("networkstats").ifPresent(Module::reload);

        JsonObject response = buildNetworkState();
        response.addProperty("saved", true);
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(response));
    }

    // ─── GET/POST /config/messages ───────────────────────────────────

    private static final String MESSAGES_FILE = "messages.yml";

    /**
     * Every message key is a flat top-level string in messages.yml (confirmed by
     * reading the resource file — no nested sections exist there), so this simply
     * enumerates {@code yaml.getKeys(false)} rather than hardcoding the key list —
     * any message key added by a future build step shows up automatically without a
     * code change here.
     */
    private JsonObject messagesToJson(YamlConfiguration yaml) {
        JsonObject root = new JsonObject();
        for (String key : yaml.getKeys(false)) {
            root.addProperty(key, yaml.getString(key, ""));
        }
        return root;
    }

    private void getMessages(HttpExchange exchange) throws IOException {
        File file = new File(plugin.getDataFolder(), MESSAGES_FILE);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(messagesToJson(yaml)));
    }

    private void postMessages(HttpExchange exchange) throws IOException {
        JsonObject body = readBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body.");
            return;
        }

        // ── Validate everything BEFORE touching any file state. ──
        for (Map.Entry<String, JsonElement> entry : body.entrySet()) {
            JsonElement v = entry.getValue();
            if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) {
                sendError(exchange, 400, "messages." + entry.getKey() + " must be a string.");
                return;
            }
        }

        // Load the EXISTING file from disk (never a blank YamlConfiguration) and only
        // .set() the keys present in the request body — anything this editor doesn't
        // know about (e.g. a key added by a later build step before the web editor
        // catches up) is left exactly as-is on disk. This is the same safe pattern
        // /config/core uses against plugin.getConfig() (a live object, incrementally
        // mutated) — messages.yml just isn't already loaded as a long-lived object
        // anywhere in this plugin, so it's re-read from disk here instead. Deliberately
        // NOT the scoreboard/tablist/announcements pattern above (a from-scratch
        // YamlConfiguration rebuilt from the whole POST body) — see the SwagTournaments
        // post-mortem in the class javadoc for exactly why a flat, hand-typed key list
        // like this one must never risk that.
        File file = new File(plugin.getDataFolder(), MESSAGES_FILE);
        YamlConfiguration existing = YamlConfiguration.loadConfiguration(file);
        for (Map.Entry<String, JsonElement> entry : body.entrySet()) {
            existing.set(entry.getKey(), entry.getValue().getAsString());
        }

        if (!saveYaml(exchange, existing, MESSAGES_FILE)) {
            return;
        }
        plugin.getMessageUtil().load();

        JsonObject response = messagesToJson(existing);
        response.addProperty("saved", true);
        SwagHubWebResponses.sendJson(exchange, 200, gson.toJson(response));
    }

    // ─── Shared helpers ──────────────────────────────────────────────

    /**
     * Reports whether {@code moduleKey}'s owning module is currently enabled and/or
     * yielded to another plugin (§7.4 — "the API must report yielded modules as
     * read-only with the reason, so the editor UI greys them out"). This never blocks a
     * write itself — see DECISIONS.md Step 9 for why POSTing to a yielded module's
     * editor is still allowed (it's harmless prep work; {@link Module#reload()} is a
     * documented no-op while the module is disabled).
     */
    private void addModuleStatus(JsonObject root, String moduleKey) {
        boolean enabled = plugin.getModuleManager().getModule(moduleKey).map(Module::isEnabled).orElse(false);
        String yieldedTo = plugin.getCompatibilityManager().getYieldedModules().get(moduleKey);
        root.addProperty("enabled", enabled);
        root.addProperty("yielded", yieldedTo != null);
        if (yieldedTo != null) {
            root.addProperty("yieldedTo", yieldedTo);
        } else {
            root.add("yieldedTo", JsonNull.INSTANCE);
        }
    }

    private JsonObject animatedTextToJson(ConfigurationSection worldSection, String key) {
        JsonObject obj = new JsonObject();
        List<String> frames = worldSection != null ? worldSection.getStringList(key + ".frames") : List.of();
        long interval = worldSection != null ? worldSection.getLong(key + ".frame-interval-ticks", 0L) : 0L;
        obj.add("frames", gson.toJsonTree(frames));
        obj.addProperty("frameIntervalTicks", interval);
        return obj;
    }

    /**
     * Validates {@code worldObj.<fieldName>} (e.g. {@code "title"}/{@code "header"}/
     * {@code "footer"}) if present, an {@code {frames: [...], frameIntervalTicks: N}}
     * shape. Absence entirely is fine — the owning module's own loader (see {@code
     * AnimatedText#parse}) already tolerates a missing block with a runtime warning.
     *
     * @return a human-readable error message, or {@code null} if valid
     */
    private String validateAnimatedTextField(JsonObject worldObj, String fieldName, String fieldPath) {
        if (!worldObj.has(fieldName)) {
            return null;
        }
        JsonElement el = worldObj.get(fieldName);
        if (!el.isJsonObject()) {
            return fieldPath + " must be an object.";
        }
        JsonObject obj = el.getAsJsonObject();
        if (obj.has("frames") && !isStringArray(obj.get("frames"))) {
            return fieldPath + ".frames must be an array of strings.";
        }
        if (obj.has("frameIntervalTicks") && !isNumber(obj.get("frameIntervalTicks"))) {
            return fieldPath + ".frameIntervalTicks must be a number.";
        }
        return null;
    }

    private void writeAnimatedText(YamlConfiguration out, String path, JsonObject animatedTextObj) {
        List<String> frames = animatedTextObj != null && animatedTextObj.has("frames")
                ? toStringList(animatedTextObj.get("frames")) : List.of();
        long interval = animatedTextObj != null && animatedTextObj.has("frameIntervalTicks")
                ? animatedTextObj.get("frameIntervalTicks").getAsLong() : 0L;
        out.set(path + ".frames", frames);
        out.set(path + ".frame-interval-ticks", interval);
    }

    /** Writes {@code out} to {@code fileName} under the plugin's data folder; 500s on failure. Returns whether it succeeded. */
    private boolean saveYaml(HttpExchange exchange, YamlConfiguration out, String fileName) throws IOException {
        try {
            out.save(new File(plugin.getDataFolder(), fileName));
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save " + fileName + " via the web editor", e);
            sendError(exchange, 500, "Failed to save " + fileName + ": " + e.getMessage());
            return false;
        }
    }

    private JsonObject readBody(HttpExchange exchange) {
        try (var reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            JsonElement el = gson.fromJson(reader, JsonElement.class);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        SwagHubWebResponses.sendJson(exchange, status, jsonError(message));
    }

    private String jsonError(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        return gson.toJson(obj);
    }

    private static boolean isNumber(JsonElement el) {
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
    }

    private static boolean isStringArray(JsonElement el) {
        if (el == null || !el.isJsonArray()) {
            return false;
        }
        for (JsonElement item : el.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                return false;
            }
        }
        return true;
    }

    private static List<String> toStringList(JsonElement el) {
        List<String> list = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                list.add(item.getAsString());
            }
        }
        return list;
    }

    private static boolean isValidServerRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            ServerRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean isValidOverrideMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            CompatibilityManager.OverrideMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean isValidRotation(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            Rotation.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean isValidGameMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            GameMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean isValidParticle(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            Particle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean isValidSound(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean isValidCommandBlockerMode(String raw) {
        return "blacklist".equalsIgnoreCase(raw) || "whitelist".equalsIgnoreCase(raw);
    }

    private static boolean isValidWdlAction(String raw) {
        return "kick".equalsIgnoreCase(raw) || "warn".equalsIgnoreCase(raw);
    }
}
