package com.SwagDev.SwagHub.placeholder;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.bedrock.BedrockService;
import com.SwagDev.SwagHub.modules.proxy.ProxyService;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SwagHub's own internal placeholder resolver — {@code %swaghub_count_<server>%},
 * {@code %swaghub_count_total%}, {@code %swaghub_status_<server>%} (§3.2/§3.3/
 * §5.3's "online/offline status indicator"), and — as of build step 8 — the
 * player-scoped tokens §5.11 names: {@code %swaghub_vanished%},
 * {@code %swaghub_doublejump_enabled%}, {@code %swaghub_playerhider_state%}, and
 * {@code %swaghub_fly_enabled%}. Patch 1, Fix 1 (§8.1) adds three Bedrock tokens:
 * {@code %swaghub_platform%} ({@code "java"}/{@code "bedrock"}, player-scoped),
 * {@code %swaghub_count_bedrock%}, and {@code %swaghub_count_java%} (both server-wide
 * online counts on THIS server, via {@link com.SwagDev.SwagHub.bedrock.BedrockService}).
 *
 * <p><b>Deliberately a standalone class, not baked into {@code MenuModule}</b>: §5.11
 * names these exact token strings as the formal §8 {@code PlaceholderExpansion}'s
 * placeholders too, so {@link #resolveIdentifier(String, Player)} is shaped EXACTLY
 * like a {@code PlaceholderExpansion#onPlaceholderRequest(Player, String)}
 * implementation (bare identifier in, resolved string out, no {@code %} delimiters) —
 * {@link com.SwagDev.SwagHub.placeholder.SwagHubExpansion} simply constructs one of
 * these and delegates to it. See DECISIONS.md Step 4 and Step 8.</p>
 *
 * <p><b>Online/offline (§3.2):</b> "online" = the server's name is a key in
 * {@link ProxyService#getCachedCounts()} (a {@code PlayerCount} response has actually
 * been received for it at least once); "offline" = absent. No new {@code ProxyService}
 * state was added for this — see DECISIONS.md.</p>
 *
 * <p><b>Player-scoped tokens (build step 8, resolved ambiguity — see DECISIONS.md
 * Step 8):</b> {@code vanished}/{@code doublejump_enabled}/{@code fly_enabled} resolve
 * to the literal strings {@code "true"}/{@code "false"}; {@code playerhider_state}
 * resolves to the {@link com.SwagDev.SwagHub.modules.playerhider.PlayerHiderState}
 * enum name (e.g. {@code "ALL_VISIBLE"}). Each reads the OWNING module's live state
 * directly (not the module's own {@code isEnabled()} gate) — a disabled module simply
 * has nothing to report, so its token degrades to the same "false"/empty value as a
 * {@code null} player (see {@link #resolveIdentifier(String, Player)}'s javadoc)
 * rather than throwing or returning the raw token.</p>
 *
 * <p><b>PlaceholderAPI passthrough:</b> {@link #apply(String, Player)} additionally
 * runs {@code PlaceholderAPI.setPlaceholders(player, text)} over the result when the
 * {@code PlaceholderAPI} plugin is present AND enabled (checked by an actual runtime
 * plugin-manager lookup every call — never a hard import failure if it's absent).
 * This is for surfacing OTHER plugins' already-registered placeholders in menu/item
 * text; SwagHub's own formal {@code PlaceholderExpansion} registration (build step 8)
 * lives in {@link com.SwagDev.SwagHub.placeholder.SwagHubExpansion}, not here.</p>
 */
public final class SwagHubPlaceholders {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("%swaghub_([a-zA-Z0-9_\\-]+)%");

    private final SwagHub plugin;

    public SwagHubPlaceholders(SwagHub plugin) {
        this.plugin = plugin;
    }

    /**
     * Convenience overload for player-INDEPENDENT callers (none of {@code count_}/
     * {@code status_} ever need a player). Delegates to
     * {@link #resolveIdentifier(String, Player)} with a {@code null} player — any
     * player-scoped identifier resolved this way degrades per that method's own
     * null-player rule rather than throwing.
     */
    public Optional<String> resolveIdentifier(String identifier) {
        return resolveIdentifier(identifier, null);
    }

    /**
     * Resolves one bare identifier (no {@code swaghub_} prefix, no {@code %}
     * delimiters), e.g. {@code "count_total"}, {@code "count_survival"},
     * {@code "status_survival"}, or (build step 8) a player-scoped identifier —
     * {@code "vanished"}, {@code "doublejump_enabled"}, {@code "playerhider_state"},
     * {@code "fly_enabled"}. Returns {@link Optional#empty()} for an identifier this
     * resolver doesn't own (the caller should leave the original token untouched in
     * that case, e.g. so a later PlaceholderAPI pass can still see it if it happens
     * to look similar).
     *
     * <p><b>Null-player degradation (resolved ambiguity, DECISIONS.md Step 8):</b> a
     * player-scoped identifier resolved with {@code player == null} (e.g. PAPI's own
     * {@code %swaghub_vanished%} being evaluated for console output, or this class's
     * own zero-arg {@link #resolveIdentifier(String)} convenience) returns
     * {@code "false"} for the three boolean tokens and {@code ""} (empty string,
     * never the raw enum default) for {@code playerhider_state} — never
     * {@link Optional#empty()}, since the identifier IS recognized, it just has
     * nothing meaningful to report without a player. This mirrors how PlaceholderAPI
     * itself expects an owned-but-unanswerable placeholder to resolve to something
     * printable rather than leaving the raw {@code %...%} token in rendered text.</p>
     */
    public Optional<String> resolveIdentifier(String identifier, Player player) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String key = identifier.trim().toLowerCase(Locale.ROOT);
        ProxyService service = plugin.getProxyService();

        if (key.equals("count_total")) {
            return Optional.of(String.valueOf(service != null ? service.getCachedTotalCount() : 0));
        }
        // ─── §8.1 Bedrock tokens (Patch 1, Fix 1) — checked as literal matches BEFORE
        // the generic "count_" prefix fallback below, exactly like "count_total" above,
        // since these count players on THIS server by platform, not a proxy backend's
        // cached PlayerCount response (a real backend literally named "bedrock" or
        // "java" would collide otherwise — the same accepted trade-off "count_total"
        // already makes for its own reserved name).
        if (key.equals("count_bedrock") || key.equals("count_java")) {
            boolean wantBedrock = key.equals("count_bedrock");
            BedrockService bedrockService = plugin.getBedrockService();
            long count = plugin.getServer().getOnlinePlayers().stream()
                    .filter(p -> isBedrock(bedrockService, p) == wantBedrock)
                    .count();
            return Optional.of(String.valueOf(count));
        }
        if (key.startsWith("count_")) {
            String server = key.substring("count_".length());
            return Optional.of(String.valueOf(service != null ? service.getCachedCount(server) : 0));
        }
        if (key.startsWith("status_")) {
            String server = key.substring("status_".length()).toLowerCase(Locale.ROOT);
            boolean online = service != null && service.getCachedCounts().containsKey(server);
            return Optional.of(online ? "<green>Online</green>" : "<red>Offline</red>");
        }

        // ─── §8.1 Bedrock token (Patch 1, Fix 1) — player-scoped ───
        if (key.equals("platform")) {
            // A null player (console/PAPI-offline context) degrades to "java" rather
            // than Optional.empty() — matches every other player-scoped token below
            // (see this method's own "null-player degradation" javadoc) and is a safe
            // default: nothing console-side needs to distinguish platforms.
            boolean bedrock = player != null && isBedrock(plugin.getBedrockService(), player);
            return Optional.of(bedrock ? "bedrock" : "java");
        }

        // ─── Player-scoped tokens (build step 8, §5.11) ───
        if (key.equals("vanished")) {
            boolean vanished = player != null && plugin.getVanishModule() != null
                    && plugin.getVanishModule().isVanished(player);
            return Optional.of(String.valueOf(vanished));
        }
        if (key.equals("doublejump_enabled")) {
            boolean enabled = player != null && plugin.getDoubleJumpModule() != null
                    && plugin.getDoubleJumpModule().hasGrantedFlight(player);
            return Optional.of(String.valueOf(enabled));
        }
        if (key.equals("fly_enabled")) {
            boolean enabled = player != null && plugin.getFlyModule() != null
                    && plugin.getFlyModule().hasGrantedFlight(player);
            return Optional.of(String.valueOf(enabled));
        }
        if (key.equals("playerhider_state")) {
            String state = player == null || plugin.getPlayerHiderModule() == null
                    ? "" : plugin.getPlayerHiderModule().getState(player).name();
            return Optional.of(state);
        }
        return Optional.empty();
    }

    /**
     * Replaces every {@code %swaghub_...%} token in {@code text} via
     * {@link #resolveIdentifier(String)}, then runs the result through
     * PlaceholderAPI (if present/enabled) for {@code player}. Safe to call with a
     * {@code null} player (PlaceholderAPI itself tolerates a {@code null} player for
     * placeholders that don't need one; SwagHub's own tokens above never need a
     * player at all).
     */
    public String apply(String text, Player player) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = resolveInternalTokens(text, player);
        if (isPlaceholderApiPresent()) {
            result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result);
        }
        return result;
    }

    private String resolveInternalTokens(String text, Player player) {
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String identifier = matcher.group(1);
            String replacement = resolveIdentifier(identifier, player).orElse(matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private boolean isPlaceholderApiPresent() {
        var papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        return papi != null && papi.isEnabled();
    }

    /**
     * Null-safe {@link BedrockService#isBedrockPlayer(java.util.UUID)} — {@code
     * bedrockService} is only {@code null} if called before {@link
     * SwagHub#finishStartup(long)} has run, which never happens for a real placeholder
     * request (see that method's own javadoc).
     */
    private boolean isBedrock(BedrockService bedrockService, Player player) {
        return bedrockService != null && player != null && bedrockService.isBedrockPlayer(player.getUniqueId());
    }
}
