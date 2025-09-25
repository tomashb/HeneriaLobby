package com.lobby.social.menus;

import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.social.clans.Clan;
import com.lobby.social.clans.ClanManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ClanBankMenu implements Menu, InventoryHolder {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &eBanque du Clan");
    private static final int SIZE = 54;
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.FRANCE);

    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final ClanManager clanManager;
    private final Clan clan;
    private final UUID viewerUuid;

    private Inventory inventory;

    public ClanBankMenu(final MenuManager menuManager,
                        final AssetManager assetManager,
                        final ClanManager clanManager,
                        final Clan clan,
                        final UUID viewerUuid) {
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.clanManager = clanManager;
        this.clan = clan;
        this.viewerUuid = viewerUuid;
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
        if (slot == 21) {
            menuManager.openMenu(player, "clan_bank_deposit_menu");
            return;
        }
        if (slot == 23 && canWithdraw()) {
            menuManager.openMenu(player, "clan_bank_withdraw_menu");
            return;
        }
        if (slot == 40) {
            menuManager.openMenu(player, "clan_bank_logs_menu");
            return;
        }
        if (slot == 50) {
            menuManager.openMenu(player, "clan_menu");
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void placeItems() {
        final ItemStack info = decorate(assetManager.getHead("hdb:52000"),
                ChatColor.GOLD.toString() + ChatColor.BOLD + "Solde du Clan",
                List.of(
                        ChatColor.AQUA + "▪ " + ChatColor.AQUA + "Solde actuel : " + ChatColor.YELLOW + formatCoins(clan.getBankCoins()) + " ⛁"
                ));
        inventory.setItem(4, info);

        final ItemStack deposit = decorate(assetManager.getHead("hdb:23533"),
                ChatColor.GREEN.toString() + ChatColor.BOLD + "Déposer des Coins",
                List.of(
                        ChatColor.YELLOW + "▶ Cliquez pour choisir un montant"
                ));
        inventory.setItem(21, deposit);

        if (canWithdraw()) {
            final ItemStack withdraw = decorate(assetManager.getHead("hdb:23534"),
                    ChatColor.RED.toString() + ChatColor.BOLD + "Retirer des Coins",
                    List.of(
                            ChatColor.YELLOW + "▶ Cliquez pour choisir un montant"
                    ));
            inventory.setItem(23, withdraw);
        }

        final ItemStack logs = decorate(new ItemStack(Material.WRITABLE_BOOK),
                ChatColor.AQUA.toString() + ChatColor.BOLD + "Historique des Transactions",
                List.of(
                        ChatColor.YELLOW + "▶ Cliquez pour consulter"
                ));
        inventory.setItem(40, logs);

        final ItemStack back = decorate(assetManager.getHead("hdb:9334"),
                ChatColor.RED.toString() + ChatColor.BOLD + "Retour",
                List.of(
                        ChatColor.RESET.toString(),
                        ChatColor.GRAY + "Revenir au menu du clan.",
                        ChatColor.RESET.toString(),
                        ChatColor.YELLOW + "▶ Cliquez pour revenir"
                ));
        inventory.setItem(50, back);
    }

    private void fillBackground() {
        final ItemStack filler = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
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

    private ItemStack createGlass(final Material material) {
        final ItemStack pane = new ItemStack(material);
        final ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
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

    private boolean canWithdraw() {
        return clanManager != null && clanManager.hasPermission(clan.getId(), viewerUuid, "clan.withdraw");
    }

    private String formatCoins(final long coins) {
        return NUMBER_FORMAT.format(Math.max(0L, coins));
    }
}

