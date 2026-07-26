package com.SwagDev.SwagHub.modules.playerhider;

/**
 * §5.7 player hider's three cycling states: {@code ALL_VISIBLE} -&gt;
 * {@code HIDE_OTHERS} -&gt; {@code RANKS_ONLY} -&gt; back to {@code ALL_VISIBLE}.
 * Persisted as this enum's {@code name()} on {@code SwagHubPlayerData.Data
 * #playerHiderState}.
 */
public enum PlayerHiderState {
    ALL_VISIBLE,
    HIDE_OTHERS,
    RANKS_ONLY;

    public PlayerHiderState next() {
        return switch (this) {
            case ALL_VISIBLE -> HIDE_OTHERS;
            case HIDE_OTHERS -> RANKS_ONLY;
            case RANKS_ONLY -> ALL_VISIBLE;
        };
    }

    /** Parses a stored/config state name, falling back to {@link #ALL_VISIBLE} for anything unrecognized. */
    public static PlayerHiderState fromName(String raw) {
        if (raw == null) {
            return ALL_VISIBLE;
        }
        try {
            return PlayerHiderState.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ALL_VISIBLE;
        }
    }
}
