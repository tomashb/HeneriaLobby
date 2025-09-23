package com.lobby.menus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class JeuxMenu implements Menu, InventoryHolder {

    private static final int INVENTORY_SIZE = 54;
    private static final String TITLE = "§8» §aMenu des Jeux";
    private static final int SLOT_BEDWARS = 20;
    private static final int SLOT_PROFILE = 49;

    private final AssetManager assetManager;
    private final Inventory inventory;

    public JeuxMenu(final AssetManager assetManager) {
        this.assetManager = assetManager;
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, TITLE);
    }

    @Override
    public void open(final Player player) {
        buildItems(player);
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        switch (event.getSlot()) {
            case SLOT_BEDWARS -> {
                player.closeInventory();
                player.sendMessage("§eTéléportation vers BedWars...");
                player.performCommand("server bedwars");
            }
            case SLOT_PROFILE -> {
                player.closeInventory();
                player.sendMessage("§cLe profil est actuellement indisponible.");
            }
            default -> {
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void buildItems(final Player player) {
        final ItemStack filler = createFiller();
        final ItemStack[] contents = new ItemStack[INVENTORY_SIZE];
        Arrays.fill(contents, filler);

        contents[SLOT_BEDWARS] = createBedwarsItem();
        contents[SLOT_PROFILE] = createProfileItem(player);

        inventory.setContents(contents);
    }

    private ItemStack createFiller() {
        final ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        final ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
        }
        return filler;
    }

    private ItemStack createBedwarsItem() {
        final ItemStack head = assetManager.getHead("hdb:67957");
        final ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lBedWars");
            final String players = assetManager.getGlobalPlaceholder("%lobby_online_bedwars%");
            meta.setLore(List.of(
                    "§7Le mode de jeu le plus apprécié !",
                    "§r",
                    "§8▸ §7Joueurs: §a" + players
            ));
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createProfileItem(final Player player) {
        final ItemStack profile = new ItemStack(Material.BOOK);
        final ItemMeta meta = profile.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lProfil");
            meta.setLore(List.of(
                    "§7Fonctionnalité en reconstruction",
                    "§7Revenez bientôt, " + player.getName() + " !"
            ));
            profile.setItemMeta(meta);
        }
        return profile;
    }
}
