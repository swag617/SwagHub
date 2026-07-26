package com.SwagDev.SwagHub.api.event;

import com.SwagDev.SwagHub.modules.menu.MenuConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * §5.11's developer API: fired by {@code MenuModule#openMenu(Player, String)} right
 * after {@code player.openInventory(inventory)} has already succeeded (§5.3) —
 * fire-and-forget/informational only, per §5.11's own list (only
 * {@code PlayerSendToServerEvent} and {@code PlayerDoubleJumpEvent} are named
 * cancellable there; this one deliberately is not, since the inventory is already
 * open by the time this fires — there is nothing left to veto).
 *
 * <p><b>Field choice (resolved ambiguity, DECISIONS.md Step 8):</b> exposes BOTH the
 * bare menu id ({@link #getMenuId()}) for simple listeners and the full
 * {@link MenuConfig} ({@link #getMenu()}) for listeners that want the title/size/slot
 * layout without a second lookup into {@code MenuModule#getMenus()} — cheap to carry
 * both since {@code openMenu} already has the resolved {@link MenuConfig} in hand at
 * the fire site.</p>
 */
public class MenuOpenEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final MenuConfig menu;

    public MenuOpenEvent(Player player, MenuConfig menu) {
        this.player = player;
        this.menu = menu;
    }

    /** The player who just had this menu opened for them. */
    public Player getPlayer() {
        return player;
    }

    /** The full menu definition that was just opened. */
    public MenuConfig getMenu() {
        return menu;
    }

    /** Convenience for {@link #getMenu()}{@code .getId()}. */
    public String getMenuId() {
        return menu.getId();
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
