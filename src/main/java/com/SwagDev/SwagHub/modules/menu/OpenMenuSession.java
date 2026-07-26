package com.SwagDev.SwagHub.modules.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

/**
 * Tracks one player's currently-open SwagHub menu: which {@link MenuConfig} it is,
 * the actual {@link Inventory} instance they're looking at (one per viewer — menus
 * are never shared across players), and the live-placeholder refresh task, if any.
 *
 * <p>Package-private: only {@link MenuModule} ever creates or reads one of these.</p>
 */
final class OpenMenuSession {

    private final MenuConfig menu;
    private final Inventory inventory;
    private BukkitTask refreshTask;

    OpenMenuSession(MenuConfig menu, Inventory inventory) {
        this.menu = menu;
        this.inventory = inventory;
    }

    MenuConfig getMenu() {
        return menu;
    }

    Inventory getInventory() {
        return inventory;
    }

    void setRefreshTask(BukkitTask refreshTask) {
        this.refreshTask = refreshTask;
    }

    /** Cancels the refresh task if one is running. Safe to call more than once. */
    void cancelRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }
}
