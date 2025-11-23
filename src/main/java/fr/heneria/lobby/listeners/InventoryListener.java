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
        // Global protection for all inventories for non-creative players in Lobby
        // This covers both the Player Inventory (Hotbar protection) and Custom Menus (GUI protection).
        if (event.getWhoClicked().getGameMode() == GameMode.CREATIVE) return;

        // If clicked outside
        if (event.getClickedInventory() == null) return;

        // Cancel all clicks by default to ensure "Inamovible" behavior and Menu protection.
        // If we want to allow specific interactions (like clicking a button), the InteractListener
        // will handle the logic (reading the action) but the EVENT itself should be cancelled
        // to prevent the item from being picked up.

        event.setCancelled(true);

        // Note: Logic for executing actions is handled in InteractListener (which listens to the same event but focuses on the action).
        // To avoid conflict/double handling, we can either merge them or let this one purely handle "Protection" (Cancelling).
        // InteractListener will read the item, do the action, and since it's cancelled here, the item won't move.
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
