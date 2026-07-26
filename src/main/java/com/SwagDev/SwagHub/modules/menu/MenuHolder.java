package com.SwagDev.SwagHub.modules.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;

/**
 * Marks an {@link Inventory} as belonging to SwagHub's menu engine, keyed by the
 * owning menu's (lower-cased) id. This is the ONLY thing {@link MenuModule}'s
 * click/drag/close listeners use to decide "is this inventory ours" — never
 * title-string matching, per this build step's explicit instructions (title strings
 * are user-editable MiniMessage and can collide with any other plugin's GUI).
 */
public final class MenuHolder implements InventoryHolder {

    private final String menuId;
    private Inventory inventory;

    public MenuHolder(String menuId) {
        this.menuId = Objects.requireNonNull(menuId, "menuId");
    }

    public String getMenuId() {
        return menuId;
    }

    /** Set once, immediately after {@code Bukkit.createInventory(this, ...)} constructs the real inventory. */
    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
