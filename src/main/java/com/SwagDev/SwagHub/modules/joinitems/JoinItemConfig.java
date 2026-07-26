package com.SwagDev.SwagHub.modules.joinitems;

import com.SwagDev.SwagHub.item.ItemConfig;

import java.util.Objects;

/**
 * A join item's appearance/actions ({@link ItemConfig}) plus the one field unique to
 * join items: which hotbar slot (0-8) it's given in. Pure data, no Bukkit dependency
 * — see {@link JoinItemConfigParser} for why this matters for testability.
 */
public final class JoinItemConfig {

    private final ItemConfig item;
    private final int slot;

    public JoinItemConfig(ItemConfig item, int slot) {
        this.item = Objects.requireNonNull(item, "item");
        this.slot = slot;
    }

    public ItemConfig getItem() {
        return item;
    }

    /** Hotbar slot, always 0-8 (validated by {@link JoinItemConfigParser}). */
    public int getSlot() {
        return slot;
    }

    public String getId() {
        return item.getId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JoinItemConfig other)) {
            return false;
        }
        return slot == other.slot && item.equals(other.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, slot);
    }

    @Override
    public String toString() {
        return "JoinItemConfig{item=" + item + ", slot=" + slot + "}";
    }
}
