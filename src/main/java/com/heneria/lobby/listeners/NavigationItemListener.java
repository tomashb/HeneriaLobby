package com.heneria.lobby.listeners;

import com.heneria.lobby.menu.GUIManager;
import com.heneria.lobby.menu.MenuItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Handles persistent hotbar navigation items defined in configuration.
 */
public class NavigationItemListener implements Listener {

    private final GUIManager guiManager;

    public NavigationItemListener(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        guiManager.getNavigationItems().forEach((slot, item) ->
                player.getInventory().setItem(slot, item.getItemStack()));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (guiManager.isNavigationItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && guiManager.isNavigationItem(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && guiManager.isNavigationItem(event.getItem())) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                MenuItem item = guiManager.getNavigationItem(event.getItem());
                String action = item.getAction();
                if (action.startsWith("open_menu:")) {
                    String name = action.split(":", 2)[1];
                    guiManager.openMenu(event.getPlayer(), name);
                } else if (action.startsWith("run_command:")) {
                    String cmd = action.split(":", 2)[1];
                    event.getPlayer().performCommand(cmd);
                }
            }
        }
    }
}

