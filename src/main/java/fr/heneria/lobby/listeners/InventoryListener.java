package fr.heneria.lobby.listeners;

import fr.heneria.lobby.HeneriaLobby;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public class InventoryListener implements Listener {

    private final HeneriaLobby plugin;

    public InventoryListener(HeneriaLobby plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked().getGameMode() == GameMode.CREATIVE) return;

        // If clicked outside
        if (event.getClickedInventory() == null) return;

        // If clicking in player inventory (Top or Bottom if player inventory is open, or just Bottom)
        // We generally want to protect the Hotbar items.
        // If the player clicks in their own inventory, we cancel.
        if (event.getClickedInventory().getType() == InventoryType.PLAYER) {
            event.setCancelled(true);
            return;
        }

        // If shift clicking from top inventory into player inventory, we should probably prevent it if it overwrites hotbar items.
        // But simply cancelling all clicks in Player inventory handles the "moving within player inventory" part.
        // What if they click in Top inventory and try to move item to Bottom?
        // Shift-click in Top:
        if (event.getClickedInventory() != event.getWhoClicked().getInventory()) {
             if (event.isShiftClick()) {
                 event.setCancelled(true); // Prevent shift clicking items from Menu to Inventory
             }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked().getGameMode() == GameMode.CREATIVE) return;

        // If dragging involves player inventory slots
        for (int slot : event.getRawSlots()) {
            if (slot >= event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        event.setCancelled(true);
    }
}
