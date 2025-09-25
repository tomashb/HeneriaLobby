package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.social.friends.FriendManager;
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

public class FriendSettingsMenu implements Menu, InventoryHolder {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &dParamètres (Amis)");
    private static final int SIZE = 54;

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final FriendManager friendManager;
    private final UUID viewerUuid;
    private final String requestStatus;
    private final String notificationStatus;
    private final String jumpStatus;

    private Inventory inventory;

    public FriendSettingsMenu(final LobbyPlugin plugin,
                              final MenuManager menuManager,
                              final AssetManager assetManager,
                              final FriendManager friendManager,
                              final UUID viewerUuid,
                              final String requestStatus,
                              final String notificationStatus,
                              final String jumpStatus) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.friendManager = friendManager;
        this.viewerUuid = viewerUuid;
        this.requestStatus = requestStatus;
        this.notificationStatus = notificationStatus;
        this.jumpStatus = jumpStatus;
    }

    @Override
    public void open(final Player player) {
        inventory = Bukkit.createInventory(this, SIZE, TITLE);
        fillBackground();
        placeBorder();
        placeItems();
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getSlot();
        if (slot == 20) {
            toggleRequests(player);
            return;
        }
        if (slot == 22) {
            toggleNotifications(player);
            return;
        }
        if (slot == 24) {
            toggleJump(player);
            return;
        }
        if (slot == 50) {
            menuManager.openMenu(player, "amis_menu");
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void placeItems() {
        inventory.setItem(20, decorate(assetManager.getHead("hdb:13389"),
                ChatColor.AQUA.toString() + ChatColor.BOLD + "Recevoir les Demandes",
                List.of(
                        ChatColor.RESET.toString(),
                        ChatColor.GRAY + "Déterminez qui peut vous ajouter en ami.",
                        ChatColor.RESET.toString(),
                        ChatColor.AQUA + "▪ " + ChatColor.AQUA + "INFO : " + ChatColor.WHITE + requestStatus,
                        ChatColor.RESET.toString(),
                        ChatColor.YELLOW + "▶ Cliquez pour basculer"
                )));

        inventory.setItem(22, decorate(assetManager.getHead("hdb:1455"),
                ChatColor.AQUA.toString() + ChatColor.BOLD + "Notifications de Connexion",
                List.of(
                        ChatColor.RESET.toString(),
                        ChatColor.GRAY + "Soyez alerté quand vos amis se connectent.",
                        ChatColor.RESET.toString(),
                        ChatColor.AQUA + "▪ " + ChatColor.AQUA + "INFO : " + ChatColor.WHITE + notificationStatus,
                        ChatColor.RESET.toString(),
                        ChatColor.YELLOW + "▶ Cliquez pour basculer"
                )));

        inventory.setItem(24, decorate(assetManager.getHead("hdb:32010"),
                ChatColor.AQUA.toString() + ChatColor.BOLD + "Téléportation Directe",
                List.of(
                        ChatColor.RESET.toString(),
                        ChatColor.GRAY + "Autorise vos amis à vous rejoindre instantanément.",
                        ChatColor.RESET.toString(),
                        ChatColor.AQUA + "▪ " + ChatColor.AQUA + "INFO : " + ChatColor.WHITE + jumpStatus,
                        ChatColor.RESET.toString(),
                        ChatColor.YELLOW + "▶ Cliquez pour basculer"
                )));

        final ItemStack back = decorate(assetManager.getHead("hdb:9334"),
                ChatColor.RED.toString() + ChatColor.BOLD + "Retour",
                List.of(
                        ChatColor.RESET.toString(),
                        ChatColor.GRAY + "Revenir au menu social principal.",
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

    private void placeBorder() {
        final ItemStack primary = createGlass(Material.LIME_STAINED_GLASS_PANE);
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

    private void toggleRequests(final Player player) {
        if (plugin == null) {
            friendManager.cycleRequestAcceptance(viewerUuid);
            reopen(player);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            friendManager.cycleRequestAcceptance(viewerUuid);
            Bukkit.getScheduler().runTask(plugin, () -> reopen(player));
        });
    }

    private void toggleNotifications(final Player player) {
        if (plugin == null) {
            friendManager.toggleNotifications(viewerUuid);
            reopen(player);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            friendManager.toggleNotifications(viewerUuid);
            Bukkit.getScheduler().runTask(plugin, () -> reopen(player));
        });
    }

    private void toggleJump(final Player player) {
        if (plugin == null) {
            friendManager.cycleFriendVisibility(viewerUuid);
            reopen(player);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            friendManager.cycleFriendVisibility(viewerUuid);
            Bukkit.getScheduler().runTask(plugin, () -> reopen(player));
        });
    }

    private void reopen(final Player player) {
        SocialHeavyMenus.openFriendSettingsMenu(menuManager, player);
    }
}

