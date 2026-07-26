package com.SwagDev.SwagHub.placeholder;

import com.SwagDev.SwagHub.SwagHub;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * §5.11's formal {@code PlaceholderAPI} expansion — {@code %swaghub_...%} — registered
 * (build step 8) only when the {@code PlaceholderAPI} plugin is present and enabled,
 * exactly as {@link SwagHubPlaceholders#apply(String, Player)} already runtime-checks
 * before ever touching a {@code me.clip.placeholderapi} class. This class is a THIN
 * wrapper: all actual resolution logic lives in {@link SwagHubPlaceholders}, which is
 * shaped identically to {@link #onPlaceholderRequest(Player, String)}'s own contract
 * on purpose (see that class's javadoc) — this class never duplicates it.
 *
 * <p><b>Verified against the {@code placeholderapi:2.11.6} jar (provided scope,
 * already declared in pom.xml) rather than assumed, per §0 rule 3</b> — decompiled
 * via {@code javap}, not just read from third-party tutorials: {@link
 * PlaceholderExpansion} only DECLARES {@link #getIdentifier()}/{@link #getAuthor()}/
 * {@link #getVersion()} as abstract; the actual per-request entry point PlaceholderAPI
 * calls at runtime is {@code PlaceholderHook#onRequest(OfflinePlayer, String)}, whose
 * default (non-overridden) implementation resolves the {@code OfflinePlayer} to an
 * online {@link Player} (or {@code null} if offline/absent) and delegates to {@link
 * #onPlaceholderRequest(Player, String)} — so overriding THAT method (the classic,
 * widely-documented signature) is correct and sufficient; there is no need to touch
 * {@code onRequest} directly. The base class's own {@code canRegister()} default of
 * {@code true} is fine unchanged; only {@link #persist()} is overridden — see
 * below.</p>
 *
 * <p><b>{@link #persist()} returns {@code true}</b> (resolved ambiguity, DECISIONS.md
 * Step 8): PlaceholderAPI unregisters non-persisting expansions on {@code /papi
 * reload}, which would silently break every {@code %swaghub_...%} token in every
 * menu/scoreboard/tablist/hologram until SwagHub itself was reloaded too. SwagHub
 * registers this expansion once, in {@code onEnable()}, and unregisters it once, in
 * {@code onDisable()} — an admin running {@code /papi reload} is not expected to also
 * need to run {@code /ah reload} just to keep SwagHub's own placeholders alive.</p>
 *
 * <p><b>Null-player requests</b> (a {@code null} {@code Player} reaches this method
 * whenever the underlying {@code OfflinePlayer} is offline/absent, e.g. console-
 * context placeholder evaluation) are passed straight through to
 * {@link SwagHubPlaceholders#resolveIdentifier(String, Player)} as-is — that method's
 * own javadoc documents exactly how each identifier degrades in that case.</p>
 */
public final class SwagHubExpansion extends PlaceholderExpansion {

    private final SwagHub plugin;
    private final SwagHubPlaceholders placeholders;

    public SwagHubExpansion(SwagHub plugin, SwagHubPlaceholders placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
    }

    @Override
    public String getIdentifier() {
        return "swaghub";
    }

    @Override
    public String getAuthor() {
        return "SwagDev";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        // Returning null here is PlaceholderAPI's own contract for "this expansion
        // doesn't own this identifier" — Optional#orElse(null) matches that exactly,
        // and leaves the token untouched for any OTHER expansion that might own it.
        return placeholders.resolveIdentifier(params, player).orElse(null);
    }
}
