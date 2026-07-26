package com.SwagDev.SwagHub.bedrock;

import com.SwagDev.SwagHub.SwagHub;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Patch 1, Fix 1 (§8 of the design doc — Bedrock support, never built in v1). The
 * single abstraction every SwagHub feature must go through to ask "is this player on
 * Bedrock" — {@code softdepend: [floodgate]} in {@code plugin.yml} means the
 * {@code org.geysermc.floodgate} classes may not exist on the classpath at all on a
 * Java-only server, so NO feature code (placeholders, {@code DoubleJumpModule}, a
 * future menu-forms renderer, etc.) may reference Floodgate's own API types directly —
 * every one of them calls only {@link #isBedrockPlayer(UUID)}/{@link #isAvailable()}
 * on whatever implementation {@link #create(SwagHub)} handed back at startup.
 *
 * <p><b>Two implementations, selected once at startup, never re-selected:</b>
 * {@link FloodgateBedrockService} (real Floodgate-backed detection) when the
 * {@code floodgate} plugin is present and enabled, {@link NoOpBedrockService}
 * (always reports "not Bedrock") otherwise — §8.3's "zero behavior change" acceptance
 * bar for Java-only servers falls straight out of the no-op implementation always
 * returning {@code false}, never out of extra {@code if (floodgate present)} branching
 * scattered through feature code.</p>
 *
 * <p><b>Reflection, not a compile-time Maven dependency (resolved ambiguity — see
 * DECISIONS.md Patch 1):</b> {@link FloodgateBedrockService} loads
 * {@code org.geysermc.floodgate.api.FloodgateApi} via {@code Class.forName(...)} and
 * invokes {@code getInstance()}/{@code isFloodgatePlayer(UUID)} reflectively, rather
 * than declaring {@code floodgate-api} as a {@code provided}-scope Maven dependency the
 * way {@code placeholderapi} is declared. This project's own engineering rule is to
 * verify a dependency's real Maven coordinates/repository against a jar or a live
 * build before depending on it (§0 rule 3) — no Floodgate jar or repository access was
 * available to verify with in this build environment, and this project's builds are
 * run with {@code mvn -o} (offline). Reflection sidesteps that risk entirely (zero new
 * {@code pom.xml} dependency, zero new Maven repository) while still satisfying the
 * "guard the Floodgate-backed impl's classloading" requirement even more literally
 * than a compile-time {@code provided} dependency would — {@link FloodgateBedrockService}
 * catches {@link ReflectiveOperationException} AND {@link LinkageError} around every
 * touch of the Floodgate API and falls back to reporting itself unavailable, at which
 * point {@link #create(SwagHub)} substitutes {@link NoOpBedrockService} instead.</p>
 */
public interface BedrockService {

    /**
     * @return {@code true} if {@code uuid} identifies a currently-known Bedrock
     * (Geyser/Floodgate) player. Always {@code false} when Floodgate isn't present,
     * when {@code uuid} is {@code null}, or if the reflective Floodgate call itself
     * fails for any reason — never throws.
     */
    boolean isBedrockPlayer(UUID uuid);

    /** @return {@code true} only for the real, successfully-initialized Floodgate-backed implementation. */
    boolean isAvailable();

    /**
     * Resolves the correct {@link BedrockService} implementation for this server,
     * exactly once, at startup — see this interface's own javadoc for why detection is
     * deferred to {@link SwagHub}'s post-boot {@code finishStartup()} step rather than
     * evaluated during {@code onEnable()} itself (Patch 1, Fix 5 — the exact same
     * plugin-enable-order hazard {@code CompatibilityManager} already had to solve).
     */
    static BedrockService create(SwagHub plugin) {
        Plugin floodgate = plugin.getServer().getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) {
            // §8.3: "zero log noise beyond one debug line" when Floodgate is absent —
            // Level.FINE is this project's one debug-level log call in this class.
            plugin.getLogger().fine("Floodgate not present — Bedrock support running in no-op mode "
                    + "(every player is treated as Java; zero behavior change).");
            return new NoOpBedrockService();
        }

        FloodgateBedrockService service = new FloodgateBedrockService(plugin);
        if (!service.isAvailable()) {
            // Floodgate the PLUGIN is present, but its API classes couldn't be loaded
            // reflectively (e.g. a mismatched/ancient Floodgate build) — fail safe to
            // the no-op implementation rather than leaving a half-broken service in
            // place that would silently misreport every player as Java.
            return new NoOpBedrockService();
        }

        plugin.getLogger().info("Floodgate detected — Bedrock-aware features enabled "
                + "(%swaghub_platform%, %swaghub_count_bedrock%, %swaghub_count_java%, "
                + "double-jump.bedrock).");
        return service;
    }
}
