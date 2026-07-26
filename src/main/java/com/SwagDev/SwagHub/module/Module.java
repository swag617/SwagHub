package com.SwagDev.SwagHub.module;

import com.SwagDev.SwagHub.SwagHub;

import java.util.logging.Level;

/**
 * Base class for every SwagHub feature module (§4).
 *
 * <p>Concrete modules are constructor-injected with the plugin instance (no static
 * abuse). {@link ModuleManager} owns the enable/disable/reload lifecycle; it never
 * calls {@link #onEnable()}/{@link #onDisable()}/{@link #onReload()} directly, always
 * going through {@link #enable()}/{@link #disable()}/{@link #reload()} so that:</p>
 * <ul>
 *   <li>a module's own exception can never take down another module or the whole
 *       plugin (exception isolation per module, per SwagCore's engineering rules);</li>
 *   <li>{@link #isEnabled()} always reflects reality, even after a failed enable;</li>
 *   <li>disabled modules are guaranteed to register no listeners and no tasks —
 *       concrete modules must do that registration inside {@link #onEnable()} and
 *       fully reverse it inside {@link #onDisable()}, never in a constructor.</li>
 * </ul>
 */
public abstract class Module {

    protected final SwagHub plugin;
    private final String configKey;
    private boolean enabled;

    protected Module(SwagHub plugin, String configKey) {
        this.plugin = plugin;
        this.configKey = configKey;
    }

    /** The key used under {@code modules.<key>} in config.yml and in the compatibility registry. */
    public final String getConfigKey() {
        return configKey;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether this module should be on by default when {@code modules.<key>} is absent
     * from config.yml. Subclasses may consult {@code plugin.getCompatibilityManager()
     * .getServerRole()} here to vary the default between {@code hub} and {@code game}
     * server roles, per §6.2.
     */
    public boolean isEnabledByDefault() {
        return true;
    }

    public final void enable() {
        if (enabled) {
            return;
        }
        try {
            onEnable();
            enabled = true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Module '" + configKey + "' threw an exception while enabling; it will remain disabled.", ex);
            enabled = false;
        }
    }

    public final void disable() {
        if (!enabled) {
            return;
        }
        try {
            onDisable();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Module '" + configKey + "' threw an exception while disabling.", ex);
        } finally {
            enabled = false;
        }
    }

    public final void reload() {
        if (!enabled) {
            return;
        }
        try {
            onReload();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Module '" + configKey + "' threw an exception while reloading.", ex);
        }
    }

    /** Register listeners/tasks and any other startup work. Never called while already enabled. */
    protected abstract void onEnable();

    /** Fully unregister every listener/task registered in {@link #onEnable()}. Must never leak. */
    protected abstract void onDisable();

    /** Re-read this module's own config file(s) and apply changes. Only called while enabled. */
    protected abstract void onReload();
}
