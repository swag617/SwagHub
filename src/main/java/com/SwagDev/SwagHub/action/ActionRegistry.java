package com.SwagDev.SwagHub.action;

import com.SwagDev.SwagHub.SwagHub;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of {@link ActionType}s by tag. This is the extension point later build
 * steps use to add {@code [server]}, {@code [teleport]}, {@code [open-menu]}, etc.
 * without touching {@link ActionParser}.
 */
public class ActionRegistry {

    private final SwagHub plugin;
    private final Map<String, ActionType> types = new LinkedHashMap<>();

    public ActionRegistry(SwagHub plugin) {
        this.plugin = plugin;
    }

    /** Registers an action type. Registering the same tag twice logs a warning and keeps the newest registration. */
    public void register(ActionType type) {
        String tag = type.getTag().toLowerCase(Locale.ROOT);
        if (types.containsKey(tag)) {
            plugin.getLogger().warning("Action type '[" + tag + "]' is already registered — overwriting with the new registration.");
        }
        types.put(tag, type);
    }

    public void unregister(String tag) {
        if (tag == null) {
            return;
        }
        types.remove(tag.toLowerCase(Locale.ROOT));
    }

    public Optional<ActionType> get(String tag) {
        if (tag == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(types.get(tag.toLowerCase(Locale.ROOT)));
    }

    public boolean isRegistered(String tag) {
        return tag != null && types.containsKey(tag.toLowerCase(Locale.ROOT));
    }

    public Set<String> getRegisteredTags() {
        return Collections.unmodifiableSet(types.keySet());
    }
}
