package com.SwagDev.SwagHub.modules.menu;

import com.SwagDev.SwagHub.item.ItemConfig;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Pure-data representation of one {@code selector-menus/*.yml} file (§5.3): title,
 * size, an optional filler item, per-slot items (each carrying its own actions via
 * {@link ItemConfig#getActions()}), a live-placeholder refresh interval, and an open
 * sound. No Bukkit {@code Inventory}/{@code Server} dependency — see
 * {@link MenuConfigParser}.
 */
public final class MenuConfig {

    private final String id;
    private final String title;
    private final int rows;
    private final ItemConfig filler;
    private final Map<Integer, ItemConfig> slots;
    private final long refreshIntervalTicks;
    private final String openSound;

    public MenuConfig(String id, String title, int rows, ItemConfig filler, Map<Integer, ItemConfig> slots,
                       long refreshIntervalTicks, String openSound) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = title;
        this.rows = rows;
        this.filler = filler;
        this.slots = slots == null ? Collections.emptyMap() : Collections.unmodifiableMap(slots);
        this.refreshIntervalTicks = refreshIntervalTicks;
        this.openSound = openSound;
    }

    public String getId() {
        return id;
    }

    /** Raw MiniMessage title string. */
    public String getTitle() {
        return title;
    }

    public int getRows() {
        return rows;
    }

    public int getSize() {
        return rows * 9;
    }

    /** {@code null} if no filler is configured (empty slots stay empty/air). */
    public ItemConfig getFiller() {
        return filler;
    }

    /** Slot index (0-based, within {@link #getSize()}) -&gt; item. Never {@code null}. */
    public Map<Integer, ItemConfig> getSlots() {
        return slots;
    }

    /** 0 = never auto-refresh this menu's live-placeholder slots. */
    public long getRefreshIntervalTicks() {
        return refreshIntervalTicks;
    }

    /** Raw {@link org.bukkit.Sound} enum name, or {@code null}/blank for no open sound. */
    public String getOpenSound() {
        return openSound;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MenuConfig other)) {
            return false;
        }
        return rows == other.rows
                && refreshIntervalTicks == other.refreshIntervalTicks
                && id.equals(other.id)
                && Objects.equals(title, other.title)
                && Objects.equals(filler, other.filler)
                && slots.equals(other.slots)
                && Objects.equals(openSound, other.openSound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, rows, filler, slots, refreshIntervalTicks, openSound);
    }

    @Override
    public String toString() {
        return "MenuConfig{id='" + id + "', rows=" + rows + ", slots=" + slots.size() + "}";
    }
}
