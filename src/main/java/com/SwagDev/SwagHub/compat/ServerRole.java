package com.SwagDev.SwagHub.compat;

import java.util.Locale;

/**
 * SwagHub's deployment-role config key (§6.2).
 *
 * <p>Read from {@code server-role} in config.yml. This only changes the *defaults*
 * that individual {@link com.SwagDev.SwagHub.module.Module}s pick when their own
 * config toggle is absent — it never hard-locks a module on or off. A module is
 * always free to consult {@link com.SwagDev.SwagHub.SwagHub#getCompatibilityManager()}
 * {@code .getServerRole()} from its own {@code isEnabledByDefault()} override.</p>
 */
public enum ServerRole {

    /** This server is the network's hub/lobby — all modules follow their own toggle. */
    HUB,

    /**
     * This server is a "main"/game server that something like SwagCore already runs
     * full-fat on. Hub-behavior modules default OFF; utility-only modules (holograms,
     * menus, proxy, portals, launchpads) default ON.
     */
    GAME;

    /**
     * Parses the {@code server-role} config value, falling back to {@link #HUB} for
     * any missing or unrecognized value (logged nowhere — HUB is a safe, non-destructive
     * default since it simply keeps every module's individual toggle in charge).
     */
    public static ServerRole fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return HUB;
        }
        try {
            return ServerRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return HUB;
        }
    }
}
