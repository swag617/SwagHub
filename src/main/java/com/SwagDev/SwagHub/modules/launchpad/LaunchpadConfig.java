package com.SwagDev.SwagHub.modules.launchpad;

import java.util.Objects;

/**
 * Pure-data representation of one configured launchpad — deliberately has ZERO Bukkit
 * {@code World}/{@code Location}/{@code Particle}/{@code Sound} dependency, mirroring
 * the {@code SpawnLocation}/{@code ItemConfig} split precedent, so it (and
 * {@link LaunchpadConfigParser}) can be unit-tested without a running server.
 * {@code particleName}/{@code soundName} validity against the live {@code Particle}/
 * {@code Sound} enums is checked later, by {@code LaunchpadModule} at the moment a
 * pad is actually triggered — the same "parse-time vs. build-time validation split"
 * already established for {@code ItemConfig}/{@code ItemBuilder}.
 */
public final class LaunchpadConfig {

    private final String id;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final double power;
    private final double height;
    private final String particleName;
    private final String soundName;

    public LaunchpadConfig(String id, String worldName, int x, int y, int z, double power, double height,
                            String particleName, String soundName) {
        this.id = Objects.requireNonNull(id, "id");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.x = x;
        this.y = y;
        this.z = z;
        this.power = power;
        this.height = height;
        this.particleName = particleName;
        this.soundName = soundName;
    }

    public String getId() {
        return id;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public double getPower() {
        return power;
    }

    public double getHeight() {
        return height;
    }

    /** Raw config particle name, not yet resolved against {@link org.bukkit.Particle} — may be {@code null}. */
    public String getParticleName() {
        return particleName;
    }

    /** Raw config sound name, not yet resolved against {@link org.bukkit.Sound} — may be {@code null}. */
    public String getSoundName() {
        return soundName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LaunchpadConfig other)) {
            return false;
        }
        return x == other.x && y == other.y && z == other.z
                && Double.compare(power, other.power) == 0
                && Double.compare(height, other.height) == 0
                && id.equals(other.id)
                && worldName.equals(other.worldName)
                && Objects.equals(particleName, other.particleName)
                && Objects.equals(soundName, other.soundName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, worldName, x, y, z, power, height, particleName, soundName);
    }

    @Override
    public String toString() {
        return "LaunchpadConfig{id='" + id + "', world='" + worldName + "', x=" + x + ", y=" + y + ", z=" + z
                + ", power=" + power + ", height=" + height + "}";
    }
}
