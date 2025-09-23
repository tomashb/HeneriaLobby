package com.lobby.menus;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Central listener that delegates all inventory interactions to the
 * appropriate {@link Menu} implementation.
 */
public class GlobalListener implements Listener {

    private final MenuManager menuManager;

    public GlobalListener(final MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        menu.handleClick(event);
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        menuManager.handleMenuClosed(player);
    }
}
