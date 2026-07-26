package com.SwagDev.SwagHub.module;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.CompatibilityManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registers every {@link Module}, and is the only thing that ever calls a module's
 * {@code enable()}/{@code disable()}/{@code reload()}. Every enable/reload decision is
 * routed through {@link CompatibilityManager} first, so a module never has to know
 * about auto-yield or overrides itself.
 *
 * <p>No real modules exist yet in this build step — this class is nonetheless a
 * complete framework so later steps only need to call {@link #register(Module)} and
 * fill in {@link Module#onEnable()}/{@link Module#onDisable()}/{@link Module#onReload()}.</p>
 */
public class ModuleManager {

    private final SwagHub plugin;
    private final CompatibilityManager compatibilityManager;
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager(SwagHub plugin, CompatibilityManager compatibilityManager) {
        this.plugin = plugin;
        this.compatibilityManager = compatibilityManager;
    }

    /** Registers a module. Does not enable it — call {@link #enableAll()} once all modules are registered. */
    public void register(Module module) {
        modules.put(module.getConfigKey(), module);
    }

    /** Applies the correct enabled/disabled state to every registered module, in registration order. */
    public void enableAll() {
        for (Module module : modules.values()) {
            applyDesiredState(module);
        }
    }

    /** Disables every currently-enabled module, in reverse registration order (last-enabled first). */
    public void disableAll() {
        List<Module> ordered = new ArrayList<>(modules.values());
        Collections.reverse(ordered);
        for (Module module : ordered) {
            module.disable();
        }
    }

    /**
     * Re-evaluates every module's desired state against the (already-reloaded) config and
     * compatibility manager, then transitions each module accordingly:
     * <ul>
     *   <li>should be enabled, currently isn't -&gt; {@link Module#enable()}</li>
     *   <li>should be disabled, currently is enabled -&gt; {@link Module#disable()}</li>
     *   <li>should stay enabled -&gt; {@link Module#reload()}</li>
     *   <li>should stay disabled -&gt; no-op</li>
     * </ul>
     * Used by {@code /ah reload} so config changes to {@code modules.*} or
     * {@code compatibility.overrides.*} take effect without a server restart.
     */
    public void reloadAll() {
        for (Module module : modules.values()) {
            applyDesiredState(module);
        }
    }

    private void applyDesiredState(Module module) {
        boolean requestedByConfig = plugin.getConfig()
                .getBoolean("modules." + module.getConfigKey(), module.isEnabledByDefault());
        boolean shouldBeEnabled = compatibilityManager.shouldEnable(module.getConfigKey(), requestedByConfig);

        if (shouldBeEnabled && !module.isEnabled()) {
            module.enable();
        } else if (!shouldBeEnabled && module.isEnabled()) {
            module.disable();
        } else if (shouldBeEnabled) {
            module.reload();
        }
        // else: should stay disabled and already is — nothing to do.
    }

    public Optional<Module> getModule(String configKey) {
        return Optional.ofNullable(modules.get(configKey));
    }

    public Collection<Module> getModules() {
        return Collections.unmodifiableCollection(modules.values());
    }
}
