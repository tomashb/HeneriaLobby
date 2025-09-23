package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.npcs.ActionProcessor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MenuListener implements Listener {

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;

    public MenuListener(final LobbyPlugin plugin, final MenuManager menuManager) {
        this.plugin = plugin;
        this.menuManager = menuManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final Inventory top = event.getView().getTopInventory();
        if (top == null) {
            return;
        }
        if (!menuManager.isMenuInventory(player.getUniqueId(), top)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().equals(player.getInventory())) {
            return;
        }
        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        final Map<Integer, String> actions = menuManager.getActions(player.getUniqueId());
        final String action = actions.get(slot);
        if (action == null || action.isBlank()) {
            return;
        }
        executeAction(player, action);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final Inventory top = event.getView().getTopInventory();
        if (top == null) {
            return;
        }
        if (menuManager.isMenuInventory(player.getUniqueId(), top)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        final Inventory top = event.getInventory();
        if (top == null) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        if (menuManager.isMenuInventory(uuid, top)) {
            menuManager.clearSession(uuid);
        }
    }

    private void executeAction(final Player player, final String action) {
        final String trimmed = action.trim();
        if (trimmed.regionMatches(true, 0, "[MENU]", 0, 6)) {
            final String target = trimmed.substring(6).trim();
            if (!target.isEmpty()) {
                menuManager.openMenu(player, target);
            }
            return;
        }
        final var npcManager = plugin.getNpcManager();
        final ActionProcessor actionProcessor = npcManager != null ? npcManager.getActionProcessor() : null;
        if (actionProcessor != null) {
            actionProcessor.processActions(List.of(trimmed), player, null);
        }
    }
}
