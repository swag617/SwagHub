package com.SwagDev.SwagHub.modules.doublejump;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * One config-defined cuboid where double jump is disabled — the exact same simple
 * "two-corner cuboid, no wand/selection UI" shape as
 * {@link com.SwagDev.SwagHub.modules.protection.PvpZone}, deliberately re-declared as
 * its own small class here rather than reused directly: {@code PvpZone} is javadoc'd
 * as scoped to {@code WorldProtectionModule}, and this module has its own
 * lifecycle/parsing/config location entirely independent of world-protection.
 */
public final class DoubleJumpRegion {

    private final String name;
    private final String worldName;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public DoubleJumpRegion(String name, String worldName, double x1, double y1, double z1,
                             double x2, double y2, double z2) {
        this.name = name;
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
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
