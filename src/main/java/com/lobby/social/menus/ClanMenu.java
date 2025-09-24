package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
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

/**
 * Clan overview main menu. Provides quick access to clan sub-menus and offers
 * clear instructions for players not yet affiliated with a clan.
 */
public class ClanMenu implements Menu, InventoryHolder {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &9Mon Clan");
    private static final int SIZE = 54;

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final ClanManager clanManager;
    private final Clan clan;
    private final UUID viewerUuid;

    private Inventory inventory;

    public ClanMenu(final LobbyPlugin plugin,
                    final MenuManager menuManager,
                    final AssetManager assetManager,
                    final ClanManager clanManager,
                    final Clan clan,
                    final UUID viewerUuid) {
        this.plugin = plugin;
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
        if (clan == null) {
            placeJoinView();
        } else {
            placeClanView(player);
        }
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getSlot();
        if (clan == null) {
            handleJoinClicks(player, slot);
            return;
        }
        switch (slot) {
            case 10 -> ClanMenus.openClanMembersMenu(player);
            case 12 -> ClanMenus.openClanVaultMenu(player);
            case 14 -> openOrMessage(player, "clan_upgrades_menu");
            case 16 -> openOrMessage(player, "clan_wars_menu");
            case 22 -> {
                if (clan.isLeader(viewerUuid)) {
                    SocialHeavyMenus.openClanManagementMenu(menuManager, player);
                } else {
                    player.sendMessage("§cSeul le chef de clan peut accéder à la gestion.");
                }
            }
            case 42 -> leaveClanAsync(player);
            case 49 -> menuManager.openMenu(player, "profil_menu");
            default -> {
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void handleJoinClicks(final Player player, final int slot) {
        if (slot == 21) {
            player.closeInventory();
            player.sendMessage("§eUtilisez §a/clan create <nom> <tag> §epour créer votre clan (Coût: 50 000 coins).");
            return;
        }
        if (slot == 23) {
            if (!menuManager.openMenu(player, "clan_list_menu")) {
                player.sendMessage("§cLa liste des clans n'est pas disponible.");
            }
            return;
        }
        if (slot == 49) {
            menuManager.openMenu(player, "profil_menu");
        }
    }

    private void placeJoinView() {
        inventory.setItem(21, decorate(assetManager.getHead("hdb:8971"), "§a§lCréer un Clan",
                List.of(
                        "§7Fondez votre propre clan et",
                        "§7dominez les classements !",
                        "§r",
                        "§eCoût: 50 000 Coins"
                )));

        inventory.setItem(23, decorate(new ItemStack(Material.SPYGLASS), "§b§lRechercher des Clans",
                List.of("§7Consultez la liste des clans ouverts")));

        inventory.setItem(49, decorate(assetManager.getHead("hdb:9334"), "§cRetour", List.of("§7Revenir au profil")));
    }

    private void placeClanView(final Player player) {
        placeInfoPanel();

        inventory.setItem(10, decorate(assetManager.getHead("hdb:9723"), "§a§lMembres du Clan",
                List.of("§7Consultez la liste détaillée")));

        inventory.setItem(12, decorate(assetManager.getHead("hdb:52000"), "§e§lBanque du Clan",
                List.of("§7Solde actuel: §6" + formatCoins(clan.getBankCoins()),
                        "§7Déposez ou retirez des fonds")));

        inventory.setItem(14, decorate(new ItemStack(Material.ENCHANTING_TABLE), "§d§lAméliorations",
                List.of("§7Débloquez des bonus pour votre clan")));

        inventory.setItem(16, decorate(new ItemStack(Material.DIAMOND_SWORD), "§c§lGuerres & Alliances",
                List.of("§7Gérez vos alliances et défis")));

        inventory.setItem(42, decorate(assetManager.getHead("hdb:31408"), "§c§lQuitter le Clan",
                List.of("§7Quittez votre clan actuel")));

        if (clan.isLeader(viewerUuid)) {
            inventory.setItem(22, decorate(new ItemStack(Material.ANVIL), "§c§lGestion du Clan",
                    List.of("§7Accédez aux paramètres avancés")));
        }

        inventory.setItem(49, decorate(assetManager.getHead("hdb:9334"), "§cRetour", List.of("§7Revenir au profil")));
    }

    private void placeInfoPanel() {
        final ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        final ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lClan : §7[" + clan.getTag() + "] §f" + clan.getName());
            meta.setLore(List.of(
                    "§7Description: §f" + (clan.getDescription() == null ? "Aucune" : clan.getDescription()),
                    "§7Niveau: §b" + clan.getLevel(),
                    "§7Membres: §e" + clan.getMembers().size() + "§7/§e" + clan.getMaxMembers(),
                    "§7Points: §d" + clan.getPoints(),
                    "§7Banque: §6" + formatCoins(clan.getBankCoins())
            ));
            book.setItemMeta(meta);
        }
        for (int slot = 2; slot <= 6; slot++) {
            inventory.setItem(slot, book.clone());
        }
    }

    private void fillBackground() {
        final ItemStack filler = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        final ItemStack border = createGlass(Material.BLUE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        final int[] borderSlots = {
                0, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 17, 18, 26, 27, 35,
                36, 37, 38, 39, 40, 41, 42, 43, 44,
                45, 46, 47, 48, 49, 50, 51, 52, 53
        };
        for (int slot : borderSlots) {
            inventory.setItem(slot, border);
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
        final ItemStack glass = new ItemStack(material);
        final ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        return glass;
    }

    private void openOrMessage(final Player player, final String menuId) {
        if (!menuManager.openMenu(player, menuId)) {
            player.sendMessage("§cCe menu est actuellement indisponible.");
        }
    }

    private void leaveClanAsync(final Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (clanManager.leaveClan(player)) {
                Bukkit.getScheduler().runTask(plugin, () -> SocialHeavyMenus.openClanMenu(menuManager, player));
            }
        });
    }

    private String formatCoins(final long coins) {
        return NumberFormat.getInstance(Locale.FRANCE).format(coins);
    }
}
