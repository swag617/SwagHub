package com.SwagDev.SwagHub.modules.portal;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * One config-defined cuboid proxy portal — the exact same simple "two-corner cuboid,
 * no shared abstraction" shape as {@code PvpZone}/{@code DoubleJumpRegion}, deliberately
 * re-declared as its own small class here (see {@code HologramModule}/{@code
 * PortalModule}'s package javadoc and DECISIONS.md Step 7 for why a shared cuboid-region
 * abstraction was deliberately NOT extracted even on this third occurrence), plus the
 * two pieces of data a portal needs beyond plain geometry: the destination backend
 * server name, and an optional per-portal cooldown override (in seconds; {@code null}
 * means "use {@code portals.yml}'s module-wide {@code cooldown-seconds} default").
 */
public final class PortalRegion {

    private final String id;
    private final String worldName;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;
    private final String serverName;
    private final Long cooldownSecondsOverride;

    public PortalRegion(String id, String worldName, double x1, double y1, double z1,
                         double x2, double y2, double z2, String serverName, Long cooldownSecondsOverride) {
        this.id = Objects.requireNonNull(id, "id");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.serverName = Objects.requireNonNull(serverName, "serverName");
        this.cooldownSecondsOverride = cooldownSecondsOverride;
    }

    public String getId() {
        return id;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getMinX() {
        return minX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMinZ() {
        return minZ;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMaxY() {
        return maxY;
    }

    public double getMaxZ() {
        return maxZ;
    }

    public String getServerName() {
        return serverName;
    }

    /** Per-portal cooldown override, in seconds — {@code null} means "use the module default". */
    public Long getCooldownSecondsOverride() {
        return cooldownSecondsOverride;
    }

    public boolean contains(Location location) {
        World world = location.getWorld();
        if (world == null || !world.getName().equals(worldName)) {
            return false;
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
