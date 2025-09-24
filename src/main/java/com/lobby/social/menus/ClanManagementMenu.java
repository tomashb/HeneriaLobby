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
    private static final int SIZE = 27;

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
        placeItems();
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getSlot();
        if (slot == 11) {
            player.closeInventory();
            player.performCommand("clan delete");
        } else if (slot == 26) {
            menuManager.openMenu(player, "clan_menu");
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void fillBackground() {
        final ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        final ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
    }

    private void placeItems() {
        final ItemStack disband = new ItemStack(Material.TNT);
        final ItemMeta disbandMeta = disband.getItemMeta();
        if (disbandMeta != null) {
            disbandMeta.setDisplayName("§4§lDissoudre le Clan");
            disbandMeta.setLore(List.of(
                    "§7Supprime définitivement votre clan.",
                    "§cAction irréversible!",
                    "§r",
                    "§eTapez /clan disband confirm après la commande."
            ));
            disband.setItemMeta(disbandMeta);
        }
        inventory.setItem(11, disband);

        inventory.setItem(26, decorate(assetManager.getHead("hdb:9334"), "§cRetour",
                List.of("§7Revenir au menu du clan")));

        inventory.setItem(15, decorate(new ItemStack(Material.BOOK), "§eGestion des Rangs",
                List.of("§7Bientôt disponible")));
        inventory.setItem(13, decorate(new ItemStack(Material.PAPER), "§bAnnonce de Clan",
                List.of("§7Bientôt disponible")));
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
}
