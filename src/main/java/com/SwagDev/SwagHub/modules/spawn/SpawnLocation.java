package com.SwagDev.SwagHub.modules.spawn;

import java.util.Objects;

/**
 * Pure-data representation of a stored spawn/lobby location — deliberately has
 * ZERO Bukkit {@code World}/{@code Location} dependency so it (and
 * {@link SpawnLocationIO}) can be unit-tested without a running server (§11 build
 * step 2's setlobby-restart test, see {@code SpawnLocationIOTest}).
 *
 * <p>The Bukkit {@code World} is resolved lazily, by name, only by
 * {@link SpawnStore#getBukkitLocation()} at the moment a player is actually
 * teleported — never at save/load time. This is deliberate: it keeps the
 * persistence layer itself completely decoupled from whether any particular world
 * happens to be loaded, per §4's "no static abuse" / testability spirit.</p>
 */
public final class SpawnLocation {

    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public SpawnLocation(String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SpawnLocation other)) {
            return false;
        }
        return Double.compare(x, other.x) == 0
                && Double.compare(y, other.y) == 0
                && Double.compare(z, other.z) == 0
                && Float.compare(yaw, other.yaw) == 0
                && Float.compare(pitch, other.pitch) == 0
                && worldName.equals(other.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, y, z, yaw, pitch);
    }

    @Override
    public String toString() {
        return "SpawnLocation{world='" + worldName + "', x=" + x + ", y=" + y + ", z=" + z
                + ", yaw=" + yaw + ", pitch=" + pitch + "}";
    }
}
