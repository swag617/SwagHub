package com.SwagDev.SwagHub.modules.announcements;

import java.util.List;
import java.util.Objects;

/**
 * One {@code announcements.yml} {@code worlds.<name>} (or {@code worlds.default})
 * entry (§5.6): a rotation mode, an optional per-world interval override, and the
 * list of entries to rotate through. Zero Bukkit dependency — see
 * {@link AnnouncementConfigParser}.
 */
public final class AnnouncementWorldConfig {

    private final Rotation rotation;
    private final Long intervalTicksOverride;
    private final List<AnnouncementEntry> entries;

    public AnnouncementWorldConfig(Rotation rotation, Long intervalTicksOverride, List<AnnouncementEntry> entries) {
        this.rotation = Objects.requireNonNull(rotation, "rotation");
        this.intervalTicksOverride = intervalTicksOverride;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public Rotation getRotation() {
        return rotation;
    }

    /** {@code null} means "use {@code announcements.yml}'s top-level {@code default-interval-ticks}". */
    public Long getIntervalTicksOverride() {
        return intervalTicksOverride;
    }

    /** Never {@code null}; may be empty (nothing to announce for this world). */
    public List<AnnouncementEntry> getEntries() {
        return entries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnnouncementWorldConfig other)) {
            return false;
        }
        return rotation == other.rotation
                && Objects.equals(intervalTicksOverride, other.intervalTicksOverride)
                && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rotation, intervalTicksOverride, entries);
    }

    @Override
    public String toString() {
        return "AnnouncementWorldConfig{rotation=" + rotation + ", intervalTicksOverride=" + intervalTicksOverride
                + ", entries=" + entries.size() + "}";
    }
}
