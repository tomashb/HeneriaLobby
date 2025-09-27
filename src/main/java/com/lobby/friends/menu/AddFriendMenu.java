package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.menu.FriendsMenuController;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rich add-friend menu that offers multiple entry points to find and add
 * players. Each submenu is implemented as its own inventory.
 */
public class AddFriendMenu implements Listener {

    private static final String TITLE = "§8» §aAjouter un Ami";
    private static final int SIZE = 36;
    private static final int BACK_SLOT = 31;
    private static final int SEARCH_SLOT = 10;
    private static final int SUGGESTIONS_SLOT = 11;
    private static final int NEARBY_SLOT = 12;
    private static final int HISTORY_SLOT = 13;
    private static final int IMPORT_SLOT = 14;

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;
    private final Inventory inventory;

    public AddFriendMenu(final LobbyPlugin plugin, final FriendsManager friendsManager, final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, SIZE, TITLE);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        setupMenu();
    }

    private void setupMenu() {
        final ItemStack greenGlass = createItem(Material.GREEN_STAINED_GLASS_PANE, " ");
        final int[] greenSlots = {0, 1, 2, 6, 7, 8, 9, 17, 18, 26, 27, 28, 29, 33, 34, 35};
        for (int slot : greenSlots) {
            inventory.setItem(slot, greenGlass);
        }

        final ItemStack searchButton = createItem(Material.COMPASS, "§a§l🔍 Rechercher un Joueur");
        final ItemMeta searchMeta = searchButton.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setLore(Arrays.asList(
                    "§7Recherchez un joueur par son nom",
                    "§7pour lui envoyer une demande d'ami",
                    "",
                    "§a▸ Recherches récentes: §20",
                    "§7▸ Dernière recherche: §8Aucune",
                    "§e▸ Réussite: §6100%",
                    "",
                    "§8» §aCliquez pour rechercher"
            ));
            searchButton.setItemMeta(searchMeta);
        }
        inventory.setItem(SEARCH_SLOT, searchButton);

        final ItemStack suggestionsButton = createItem(Material.BOOK, "§e§l📝 Joueurs Suggérés");
        final ItemMeta suggestionsMeta = suggestionsButton.getItemMeta();
        if (suggestionsMeta != null) {
            suggestionsMeta.setLore(Arrays.asList(
                    "§7Suggestions intelligentes basées sur:",
                    "§8▸ §7Amis en commun",
                    "§8▸ §7Serveurs fréquentés",
                    "§8▸ §7Activités similaires",
                    "§8▸ §7Compatibilité horaire",
                    "",
                    "§e▸ Suggestions disponibles: §6?",
                    "§7▸ Dernière mise à jour: §8À l'instant",
                    "",
                    "§8» §eCliquez pour voir les suggestions"
            ));
            suggestionsButton.setItemMeta(suggestionsMeta);
        }
        inventory.setItem(SUGGESTIONS_SLOT, suggestionsButton);

        final ItemStack nearbyButton = createItem(Material.COMPASS, "§b§l📍 Joueurs Proches");
        final ItemMeta nearbyMeta = nearbyButton.getItemMeta();
        if (nearbyMeta != null) {
            final List<Player> nearbyPlayers = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                    .filter(p -> p.getWorld().equals(player.getWorld()))
                    .filter(p -> p.getLocation().distance(player.getLocation()) <= 100)
                    .collect(Collectors.toList());
            final int totalOnline = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
            nearbyMeta.setLore(Arrays.asList(
                    "§7Joueurs actuellement détectés:",
                    "",
                    "§b▸ À proximité (<100m): §3" + nearbyPlayers.size(),
                    "§7▸ Dans votre monde: §8" + player.getWorld().getPlayers().size(),
                    "§6▸ Sur le serveur: §e" + totalOnline,
                    "§d▸ Sur le réseau: §5" + totalOnline,
                    "",
                    "§8» §bCliquez pour voir la liste"
            ));
            nearbyButton.setItemMeta(nearbyMeta);
        }
        inventory.setItem(NEARBY_SLOT, nearbyButton);

        final ItemStack historyButton = createItem(Material.PAPER, "§7§l📋 Historique & Statistiques");
        final ItemMeta historyMeta = historyButton.getItemMeta();
        if (historyMeta != null) {
            historyMeta.setLore(Arrays.asList(
                    "§7Consultez vos données d'ajout d'amis",
                    "",
                    "§7▸ Recherches effectuées: §80",
                    "§7▸ Demandes envoyées: §80",
                    "§7▸ Taux d'acceptation: §8100%",
                    "§7▸ Dernière activité: §8Maintenant",
                    "",
                    "§8» §7Cliquez pour consulter l'historique"
            ));
            historyButton.setItemMeta(historyMeta);
        }
        inventory.setItem(HISTORY_SLOT, historyButton);

        final ItemStack importButton = createItem(Material.ENDER_CHEST, "§d§l💾 Importer des Amis");
        final ItemMeta importMeta = importButton.getItemMeta();
        if (importMeta != null) {
            importMeta.setLore(Arrays.asList(
                    "§7Importez vos amis depuis:",
                    "§8▸ §7Autres serveurs du réseau",
                    "§8▸ §7Fichiers de sauvegarde",
                    "§8▸ §7Listes externes (Discord, etc.)",
                    "§8▸ §7Anciens pseudos",
                    "",
                    "§d▸ Sources disponibles: §51",
                    "",
                    "§8» §dCliquez pour importer"
            ));
            importButton.setItemMeta(importMeta);
        }
        inventory.setItem(IMPORT_SLOT, importButton);

        final ItemStack backButton = createItem(Material.BARRIER, "§e🏠 Retour Menu Principal");
        final ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setLore(Arrays.asList(
                    "§7Revenir au menu principal",
                    "§7des amis",
                    "",
                    "§8» §eCliquez pour retourner"
            ));
            backButton.setItemMeta(backMeta);
        }
        inventory.setItem(BACK_SLOT, backButton);
    }

    private ItemStack createItem(final Material material, final String name) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open() {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        final int slot = event.getSlot();
        switch (slot) {
            case SEARCH_SLOT -> handleSearch();
            case SUGGESTIONS_SLOT -> handleSuggestions();
            case NEARBY_SLOT -> handleNearbyPlayers();
            case HISTORY_SLOT -> handleHistory();
            case IMPORT_SLOT -> handleImport();
            case BACK_SLOT -> handleBack();
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)) {
            return;
        }
        if (!viewer.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        if (TITLE.equals(event.getView().getTitle())) {
            HandlerList.unregisterAll(this);
        }
    }

    private void handleSearch() {
        player.closeInventory();
        player.sendMessage("§a🔍 Recherche de joueur");
        player.sendMessage("§7Tapez le nom du joueur que vous voulez ajouter en ami:");
        player.sendMessage("§7(ou tapez 'cancel' pour annuler)");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> new PlayerSearchMenu(plugin, friendsManager, player).open(), 1L);
    }

    private void handleSuggestions() {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> new SuggestionsMenu(plugin, friendsManager, player).open(), 1L);
    }

    private void handleNearbyPlayers() {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> new NearbyPlayersMenu(plugin, friendsManager, player).open(), 1L);
    }

    private void handleHistory() {
        player.sendMessage("§7📋 Historique et statistiques en développement !");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleImport() {
        player.sendMessage("§d💾 Import d'amis en développement !");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleBack() {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final FriendsMenuController controller = plugin.getFriendsMenuController();
            if (controller != null) {
                controller.openMainMenu(player);
            }
        }, 2L);
    }
}
