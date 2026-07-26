package com.SwagDev.SwagHub.modules.protection;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * One config-defined cuboid PvP-zone exception (§5.1's "optional PvP zone region
 * exception"). Deliberately a small, {@link com.SwagDev.SwagHub.modules.protection.WorldProtectionModule}-scoped
 * data model — NOT a shared "region" abstraction. §3.3's wand-based proxy-portal
 * region selection is a separate, later build step; this is a simple two-corner
 * cuboid read straight from config, with no selection UI.
 */
public final class PvpZone {

    private final String name;
    private final String worldName;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public PvpZone(String name, String worldName, double x1, double y1, double z1, double x2, double y2, double z2) {
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
