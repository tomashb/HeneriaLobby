package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.social.clans.Clan;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * Lightweight management panel for clan leaders. Only exposes the currently
 * implemented actions, leaving room for future expansion.
 */
public class ClanManagementMenu implements Menu, InventoryHolder {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &cGestion du Clan");
    private static final int SIZE = 54;

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final Clan clan;
    private final UUID viewerUuid;

    private Inventory inventory;

    public ClanManagementMenu(final LobbyPlugin plugin,
                              final MenuManager menuManager,
                              final AssetManager assetManager,
                              final Clan clan,
                              final UUID viewerUuid) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.clan = clan;
        this.viewerUuid = viewerUuid;
    }

    public boolean isAccessible() {
        return clan != null && clan.isLeader(viewerUuid);
    }

    @Override
    public void open(final Player player) {
        inventory = Bukkit.createInventory(this, SIZE, TITLE);
        fillBackground();
        placeBorders();
        placeItems();
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getSlot();
        if (slot == 22) {
            player.closeInventory();
            player.performCommand("clan delete");
        } else if (slot == 50) {
            menuManager.openMenu(player, "clan_menu");
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void fillBackground() {
        final ItemStack filler = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
    }

    private void placeItems() {
        final ItemStack disband = new ItemStack(Material.TNT);
        final ItemMeta disbandMeta = disband.getItemMeta();
        if (disbandMeta != null) {
            disbandMeta.setDisplayName("§4§lDISSOUDRE LE CLAN");
            disbandMeta.setLore(List.of(
                    "§cATTENTION : Cette action est",
                    "§cDÉFINITIVE.",
                    "§r",
                    "§4▶ Cliquez pour dissoudre"
            ));
            disband.setItemMeta(disbandMeta);
        }
        inventory.setItem(22, disband);

        final ItemStack ranks = decorate(new ItemStack(Material.BOOK), "§eGestion des Rangs",
                List.of(
                        "§r",
                        "§7Bientôt disponible"
                ));
        inventory.setItem(30, ranks);

        final ItemStack announcement = decorate(new ItemStack(Material.PAPER), "§bAnnonce de Clan",
                List.of(
                        "§r",
                        "§7Bientôt disponible"
                ));
        inventory.setItem(32, announcement);

        final ItemStack back = decorate(assetManager.getHead("hdb:9334"),
                "§c§lRetour",
                List.of(
                        "§r",
                        "§7Revenir au menu du clan"
                ));
        inventory.setItem(50, back);
    }

    private void placeBorders() {
        final ItemStack primary = createGlass(Material.BLUE_STAINED_GLASS_PANE);
        final int[] primarySlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53};
        for (int slot : primarySlots) {
            inventory.setItem(slot, primary);
        }
        final ItemStack secondary = createGlass(Material.GRAY_STAINED_GLASS_PANE);
        final int[] secondarySlots = {39, 40, 41};
        for (int slot : secondarySlots) {
            inventory.setItem(slot, secondary);
        }
    }

    private ItemStack decorate(final ItemStack item, final String name, final List<String> lore) {
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGlass(final Material material) {
        final ItemStack pane = new ItemStack(material);
        final ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }
}
