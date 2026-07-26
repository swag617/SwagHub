package com.SwagDev.SwagHub.util;

import com.SwagDev.SwagHub.SwagHub;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinates {@link Player#hidePlayer(org.bukkit.plugin.Plugin, Player)}/
 * {@link Player#showPlayer(org.bukkit.plugin.Plugin, Player)} calls between multiple
 * SwagHub modules that each independently want to hide/show the same (viewer, target)
 * pair for their own reason — currently {@code PlayerHiderModule} ("player-hider") and
 * {@code VanishModule} ("vanish").
 *
 * <p><b>Why this exists (a Step 6 design addition beyond what either module's own spec
 * text asked for, added because it's load-bearing for correctness):</b> Bukkit's
 * per-viewer visibility API tracks "is this (viewer, target) pair hidden by plugin X"
 * as a single set keyed by the {@link org.bukkit.plugin.Plugin} instance passed in —
 * NOT per calling class. Since {@code PlayerHiderModule} and {@code VanishModule} are
 * both part of the same {@link SwagHub} plugin instance, if each called
 * {@code hidePlayer}/{@code showPlayer} directly and independently, one module's
 * {@code showPlayer} call could silently undo the OTHER module's still-wanted
 * {@code hidePlayer} for the exact same pair (e.g. player-hider's state is
 * {@code ALL_VISIBLE} for viewer Alice, so it calls
 * {@code showPlayer(plugin, alice, bob)} — but bob is currently vanished and should
 * stay hidden from Alice; without this coordinator that {@code showPlayer} call would
 * incorrectly un-hide bob). This class tracks a small {@code Set<String>} of "reasons"
 * currently hiding each pair and only calls the real Bukkit API when the NET hidden/
 * shown state actually changes, exactly mirroring the same "only touch what you
 * yourself are responsible for" discipline this build step's flight stand-down logic
 * (see {@code DoubleJumpModule}/{@code FlyModule}) already established for
 * {@code Player#setAllowFlight}.</p>
 */
public final class PlayerVisibilityCoordinator {

    private final Map<UUID, Map<UUID, Set<String>>> hiddenReasons = new HashMap<>();

    /**
     * Records that {@code reason} does (or no longer does) want {@code target} hidden
     * from {@code viewer}, and calls the real Bukkit API only if the net hidden/shown
     * state for this pair actually changes as a result.
     */
    public void setHidden(SwagHub plugin, Player viewer, Player target, String reason, boolean hidden) {
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        Map<UUID, Set<String>> perTarget = hiddenReasons.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>());
        Set<String> reasons = perTarget.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>());

        boolean wasHidden = !reasons.isEmpty();
        if (hidden) {
            reasons.add(reason);
        } else {
            reasons.remove(reason);
        }
        boolean nowHidden = !reasons.isEmpty();

        if (wasHidden == nowHidden) {
            return; // no net change — never call the Bukkit API for a no-op
        }
        if (nowHidden) {
            viewer.hidePlayer(plugin, target);
        } else {
            viewer.showPlayer(plugin, target);
            perTarget.remove(target.getUniqueId());
        }
    }

    /** Drops all tracked state for {@code viewer} (e.g. on quit) — never touches the Bukkit API, since a quitting player needs no visibility calls made on their behalf. */
    public void clearViewer(UUID viewer) {
        hiddenReasons.remove(viewer);
    }

    /** Drops all tracked "hidden from" state naming {@code target} across every viewer (e.g. on the target's own quit). */
    public void clearTargetEverywhere(UUID target) {
        for (Map<UUID, Set<String>> perTarget : hiddenReasons.values()) {
            perTarget.remove(target);
        }
    }
}
