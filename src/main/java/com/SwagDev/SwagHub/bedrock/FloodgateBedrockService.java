package com.SwagDev.SwagHub.bedrock;

import com.SwagDev.SwagHub.SwagHub;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Reflective Floodgate-backed {@link BedrockService} — see {@link BedrockService}'s own
 * javadoc for why this calls {@code org.geysermc.floodgate.api.FloodgateApi} via
 * reflection instead of a compile-time dependency.
 *
 * <p>Every reflective call is wrapped so a Floodgate API surface change, a missing
 * method, or any other {@link ReflectiveOperationException}/{@link LinkageError} can
 * never propagate into calling feature code — it degrades this ONE instance to
 * {@link #isAvailable()} {@code false} (at construction time) or to
 * {@link #isBedrockPlayer(UUID)} returning {@code false} with one logged warning (at
 * call time), never a plugin-wide failure.</p>
 */
final class FloodgateBedrockService implements BedrockService {

    private final SwagHub plugin;
    private Object floodgateApiInstance;
    private Method isFloodgatePlayerMethod;
    private boolean available;

    FloodgateBedrockService(SwagHub plugin) {
        this.plugin = plugin;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            Object instance = getInstance.invoke(null);
            if (instance == null) {
                throw new IllegalStateException("FloodgateApi.getInstance() returned null");
            }
            this.isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            this.floodgateApiInstance = instance;
            this.available = true;
        } catch (ReflectiveOperationException | LinkageError | IllegalStateException ex) {
            plugin.getLogger().warning("The 'floodgate' plugin is present, but its API could not be "
                    + "loaded reflectively (" + ex.getClass().getSimpleName()
                    + ") — Bedrock players will be treated as Java players until this is resolved "
                    + "(e.g. update Floodgate, or check for a classpath conflict).");
            this.available = false;
        }
    }

    @Override
    public boolean isBedrockPlayer(UUID uuid) {
        if (!available || uuid == null) {
            return false;
        }
        try {
            Object result = isFloodgatePlayerMethod.invoke(floodgateApiInstance, uuid);
            return result instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().log(Level.WARNING,
                    "FloodgateApi#isFloodgatePlayer(UUID) failed reflectively — treating '" + uuid
                            + "' as a Java player for this call.", ex);
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
