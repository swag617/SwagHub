package com.SwagDev.SwagHub.modules.announcements;

import java.util.List;
import java.util.Objects;

/**
 * One {@code entries:} list item (§5.6) — a raw {@code [tag] argument} action list,
 * executed through the existing {@code ActionParser}/{@code ActionRegistry} exactly
 * like every other action list in SwagHub (join items, menu slots, first-join). Raw
 * strings, not pre-parsed {@code ParsedAction}s — parsing happens once per firing in
 * {@code AnnouncementsModule}, mirroring {@code ItemConfig#getActions()}'s "raw
 * strings, parsed later" shape.
 */
public final class AnnouncementEntry {

    private final List<String> actions;

    public AnnouncementEntry(List<String> actions) {
        this.actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /** Never empty — {@code AnnouncementConfigParser} skips any entry with no actions. */
    public List<String> getActions() {
        return actions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnnouncementEntry other)) {
            return false;
        }
        return actions.equals(other.actions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actions);
    }

    @Override
    public String toString() {
        return "AnnouncementEntry{actions=" + actions.size() + "}";
    }
}
