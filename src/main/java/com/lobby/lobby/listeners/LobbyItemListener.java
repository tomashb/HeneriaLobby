package com.lobby.lobby.listeners;

import com.lobby.lobby.LobbyManager;
import com.lobby.lobby.items.LobbyItemManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

public class LobbyItemListener implements Listener {

    private final LobbyManager lobbyManager;
    private final LobbyItemManager itemManager;

    public LobbyItemListener(final LobbyManager lobbyManager, final LobbyItemManager itemManager) {
        this.lobbyManager = lobbyManager;
        this.itemManager = itemManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!itemManager.isEnabled()) {
            return;
        }
        if (!itemManager.shouldPreventMove() && lobbyManager.isBypassing(player)) {
            return;
        }
        final ItemStack currentItem = event.getCurrentItem();
        final ItemStack cursorItem = event.getCursor();
        if (event.getHotbarButton() >= 0) {
            final ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (itemManager.isLobbyItem(hotbarItem)) {
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
        }
        if (itemManager.isLobbyItem(currentItem) || itemManager.isLobbyItem(cursorItem)) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!itemManager.isEnabled()) {
            return;
        }
        if (!itemManager.shouldPreventMove() && lobbyManager.isBypassing(player)) {
            return;
        }
        if (itemManager.isLobbyItem(event.getOldCursor())) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        final Player player = event.getPlayer();
        if (!itemManager.isEnabled()) {
            return;
        }
        if (!itemManager.shouldPreventDrop() && lobbyManager.isBypassing(player)) {
            return;
        }
        if (itemManager.isLobbyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(final PlayerSwapHandItemsEvent event) {
        final Player player = event.getPlayer();
        if (!itemManager.isEnabled()) {
            return;
        }
        if (!itemManager.shouldPreventMove() && lobbyManager.isBypassing(player)) {
            return;
        }
        if (itemManager.isLobbyItem(event.getMainHandItem()) || itemManager.isLobbyItem(event.getOffHandItem())) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(final PlayerItemDamageEvent event) {
        if (!itemManager.shouldPreventDamage()) {
            return;
        }
        if (itemManager.isLobbyItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemConsume(final PlayerItemConsumeEvent event) {
        if (!itemManager.shouldPreventConsume()) {
            return;
        }
        if (itemManager.isLobbyItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (!itemManager.isEnabled()) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
            }
            default -> {
                return;
            }
        }
        final ItemStack item = event.getItem();
        if (!itemManager.isLobbyItem(item)) {
            return;
        }
        event.setCancelled(true);
        itemManager.handleInteraction(player, item);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(final PlayerDeathEvent event) {
        if (!itemManager.isEnabled()) {
            return;
        }
        final Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            final ItemStack drop = iterator.next();
            if (itemManager.isLobbyItem(drop)) {
                iterator.remove();
            }
        }
    }
}
