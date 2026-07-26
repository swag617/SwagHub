package com.SwagDev.SwagHub.modules.hologram;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure-data representation of one configured hologram — deliberately has ZERO Bukkit
 * {@code World}/{@code Entity}/{@code TextDisplay} dependency, mirroring the
 * {@code SpawnLocation}/{@code LaunchpadConfig} split precedent, so it (and
 * {@link HologramConfigParser}/{@link HologramConfigIO}) can be unit-tested without a
 * running server. Immutable — every mutation (addline/setline/removeline/movehere) in
 * {@code HologramModule} builds and stores a fresh instance rather than mutating one in
 * place, matching every other DTO in this codebase.
 *
 * <p>{@link #getRefreshIntervalTicks()} is {@code null} when the hologram should use
 * {@code holograms.yml}'s module-wide {@code update-interval-ticks} default — an
 * explicit per-hologram override is optional.</p>
 */
public final class HologramConfig {

    private final String id;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final List<String> lines;
    private final Long refreshIntervalTicks;

    public HologramConfig(String id, String worldName, double x, double y, double z,
                           List<String> lines, Long refreshIntervalTicks) {
        this.id = Objects.requireNonNull(id, "id");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.x = x;
        this.y = y;
        this.z = z;
        this.lines = Collections.unmodifiableList(List.copyOf(lines));
        this.refreshIntervalTicks = refreshIntervalTicks;
    }

    public String getId() {
        return id;
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

    /** Ordered top-to-bottom. Never null; may be empty only transiently — {@code HologramModule} never persists an empty list. */
    public List<String> getLines() {
        return lines;
    }

    /** Per-hologram refresh override, in ticks — {@code null} means "use the module default". */
    public Long getRefreshIntervalTicks() {
        return refreshIntervalTicks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HologramConfig other)) {
            return false;
        }
        return Double.compare(x, other.x) == 0
                && Double.compare(y, other.y) == 0
                && Double.compare(z, other.z) == 0
                && id.equals(other.id)
                && worldName.equals(other.worldName)
                && lines.equals(other.lines)
                && Objects.equals(refreshIntervalTicks, other.refreshIntervalTicks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, worldName, x, y, z, lines, refreshIntervalTicks);
    }

    @Override
    public String toString() {
        return "HologramConfig{id='" + id + "', world='" + worldName + "', x=" + x + ", y=" + y + ", z=" + z
                + ", lines=" + lines.size() + ", refreshIntervalTicks=" + refreshIntervalTicks + "}";
    }
}
