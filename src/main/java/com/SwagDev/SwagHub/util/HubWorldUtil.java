package com.SwagDev.SwagHub.util;

import com.SwagDev.SwagHub.SwagHub;
import org.bukkit.World;

import java.util.List;

/**
 * The ONE utility method every hub-behavior module must call before acting (§4's
 * world whitelist requirement) — never re-implement this check locally in a module.
 *
 * <p>Static like {@code ComponentUtil} in SwagCore: this is a stateless, pure
 * function of (config, world), not a manager holding mutable state, so it does not
 * fall under the "no static abuse" rule (which targets singleton managers/global
 * mutable state, not side-effect-free helpers).</p>
 */
public final class HubWorldUtil {

    private HubWorldUtil() {
    }

    /**
     * @return {@code true} if hub behavior should apply in this world — either the
     * world is explicitly listed in {@code hub-worlds}, or the list is empty (meaning
     * "all worlds", per config.yml's documented default). {@code false} for a null world.
     */
    public static boolean isHubWorld(SwagHub plugin, World world) {
        if (world == null) {
            return false;
        }
        List<String> hubWorlds = plugin.getConfig().getStringList("hub-worlds");
        if (hubWorlds.isEmpty()) {
            return true;
        }
        return hubWorlds.contains(world.getName());
    }
}
