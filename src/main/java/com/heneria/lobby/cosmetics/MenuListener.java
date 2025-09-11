package com.heneria.lobby.cosmetics;

import com.heneria.lobby.HeneriaLobbyPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Listener handling clicks in cosmetic menus.
 */
public class MenuListener implements Listener {

    private final HeneriaLobbyPlugin plugin;
    private final CosmeticsManager cosmeticsManager;

    public MenuListener(HeneriaLobbyPlugin plugin, CosmeticsManager cosmeticsManager) {
        this.plugin = plugin;
        this.cosmeticsManager = cosmeticsManager;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!isCosmeticMenu(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        String cosmeticId = getCosmeticIdFromItem(clicked);
        if (cosmeticId == null) {
            handleMenuAction(player, clicked);
            return;
        }
        if (!cosmeticsManager.isOwned(player, cosmeticId)) {
            cosmeticsManager.purchaseCosmetic(player, cosmeticId);
        } else if (cosmeticsManager.isEquipped(player, cosmeticId)) {
            cosmeticsManager.unequipCosmetic(player, cosmeticId);
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        } else {
            cosmeticsManager.equipCosmetic(player, cosmeticId);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
        cosmeticsManager.refreshMenu(player);
    }

    @EventHandler
    public void onArmorSlotClick(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.ARMOR || event.getSlot() != 39) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "cosmetic_hat"),
                PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    private boolean isCosmeticMenu(String title) {
        return cosmeticsManager.isCosmeticMenu(title);
    }

    private String getCosmeticIdFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "cosmetic_id"), PersistentDataType.STRING);
    }

    private void handleMenuAction(Player player, ItemStack item) {
        Material type = item.getType();
        if (type == Material.BARRIER) {
            player.closeInventory();
            plugin.getGuiManager().openMenu(player, "shop");
            return;
        }
        if (type == Material.ARROW && item.getItemMeta() != null) {
            String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            if ("Page suivante".equalsIgnoreCase(name)) {
                cosmeticsManager.openNextPage(player);
            } else if ("Page précédente".equalsIgnoreCase(name)) {
                cosmeticsManager.openPreviousPage(player);
            }
        }
    }
}
