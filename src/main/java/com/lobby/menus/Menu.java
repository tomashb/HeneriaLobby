package com.lobby.menus;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Minimal contract for all inventory based menus used by the lobby plugin.
 * Implementations are responsible for building their inventory contents and
 * reacting to click events originating from the inventory that they own.
 */
public interface Menu {

    /**
     * Opens the menu for the given player. Implementations should build the
     * inventory contents before calling {@link Player#openInventory}.
     *
     * @param player the player that should see the menu
     */
    void open(Player player);

    /**
     * Handles a click inside the menu inventory.
     *
     * @param event the inventory click event to process
     */
    void handleClick(InventoryClickEvent event);
}
