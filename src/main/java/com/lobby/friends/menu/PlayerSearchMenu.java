package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Search interface showing online players that match the query. Results are
 * paginated and provide quick actions to send requests.
 */
public class PlayerSearchMenu implements Listener {

    private static final int SIZE = 54;
    private static final int ITEMS_PER_PAGE = 21;
    private static final int[] RESULT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;
    private Inventory inventory;
    private List<Player> searchResults = Collections.emptyList();
    private int currentPage = 1;
    private String currentQuery = "";

    public PlayerSearchMenu(final LobbyPlugin plugin, final FriendsManager friendsManager, final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        performSearch("");
    }

    private void performSearch(final String query) {
        currentQuery = query == null ? "" : query;
        searchResults = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .filter(p -> currentQuery.isEmpty() || p.getName().toLowerCase(Locale.ROOT)
                        .contains(currentQuery.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        createMenu();
    }

    private void createMenu() {
        final String title = "§8» §aRésultats: " + (currentQuery.isEmpty() ? "Tous" : currentQuery)
                + " §8(" + searchResults.size() + ")";
        inventory = Bukkit.createInventory(null, SIZE, title);
        setupMenu();
        open();
    }

    private void setupMenu() {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        final ItemStack greenGlass = createItem(Material.GREEN_STAINED_GLASS_PANE, " ");
        final int[] greenSlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 53};
        for (int slot : greenSlots) {
            inventory.setItem(slot, greenGlass);
        }
        displayResults();
        setupNavigation();
    }

    private void displayResults() {
        if (searchResults.isEmpty()) {
            final ItemStack noResults = createItem(Material.PAPER, "§7§lAucun joueur trouvé");
            final ItemMeta meta = noResults.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList(
                        "§7Aucun joueur ne correspond",
                        "§7à votre recherche",
                        "",
                        "§e💡 Conseils:",
                        "§8▸ §7Vérifiez l'orthographe",
                        "§8▸ §7Le joueur doit être en ligne",
                        "§8▸ §7Essayez un nom plus court",
                        "",
                        "§8» §aUtilisez 'Nouvelle recherche'"
                ));
                noResults.setItemMeta(meta);
            }
            inventory.setItem(22, noResults);
            return;
        }

        final int startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
        final int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, searchResults.size());
        for (int i = 0; i < RESULT_SLOTS.length && startIndex + i < endIndex; i++) {
            final Player foundPlayer = searchResults.get(startIndex + i);
            inventory.setItem(RESULT_SLOTS[i], createPlayerItem(foundPlayer));
        }
    }

    private ItemStack createPlayerItem(final Player foundPlayer) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(foundPlayer);
            skullMeta.setDisplayName("§e§l" + foundPlayer.getName());
            final List<String> lore = new ArrayList<>();
            lore.add("§7Informations du joueur:");
            lore.add("§8▸ §7Statut: §aEn ligne");
            lore.add("§8▸ §7Niveau: §a?");
            lore.add("§8▸ §7Temps de jeu: §b?");
            lore.add("§8▸ §7Dernière connexion: §eActuellement");
            lore.add("§8▸ §7Amis en commun: §d?");
            lore.add("§8▸ §7Réputation: §6 5/5 ⭐");
            lore.add("§8▸ §7Serveur actuel: §eLobby");
            lore.add("");
            lore.add("§7Compatibilité estimée: §a85%");
            lore.add("");
            lore.add("§8▸ §aClique gauche §8: §7Envoyer demande");
            lore.add("§8▸ §eClique milieu §8: §7Voir profil détaillé");
            lore.add("§8▸ §cClique droit §8: §7Ajouter aux suggestions");
            skullMeta.setLore(lore);
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private void setupNavigation() {
        final int totalPages = Math.max(1, (int) Math.ceil((double) searchResults.size() / ITEMS_PER_PAGE));
        if (currentPage > 1) {
            inventory.setItem(47, createItem(Material.ARROW, "§c◀ Page Précédente"));
        }
        final ItemStack newSearch = createItem(Material.COMPASS, "§a🔍 Nouvelle Recherche");
        final ItemMeta newSearchMeta = newSearch.getItemMeta();
        if (newSearchMeta != null) {
            newSearchMeta.setLore(Arrays.asList(
                    "§7Effectuer une nouvelle recherche",
                    "§7de joueur",
                    "",
                    "§8» §aCliquez pour rechercher"
            ));
            newSearch.setItemMeta(newSearchMeta);
        }
        inventory.setItem(49, newSearch);
        if (currentPage < totalPages) {
            inventory.setItem(51, createItem(Material.ARROW, "§a▶ Page Suivante"));
        }
        final ItemStack back = createItem(Material.BARRIER, "§e◀ Retour Ajout d'Amis");
        final ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setLore(Arrays.asList(
                    "§7Revenir au menu d'ajout",
                    "§7d'amis",
                    "",
                    "§8» §eCliquez pour retourner"
            ));
            back.setItemMeta(backMeta);
        }
        inventory.setItem(52, back);
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
        if (inventory == null) {
            return;
        }
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final String title = event.getView().getTitle();
        if (title == null || !title.contains("§8» §aRésultats:")) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        final int slot = event.getSlot();
        if (slot == 47 && currentPage > 1) {
            currentPage--;
            setupMenu();
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            player.sendMessage("§a🔍 Nouvelle recherche de joueur");
            player.sendMessage("§7Tapez le nom du joueur dans le chat:");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            return;
        }
        final int totalPages = Math.max(1, (int) Math.ceil((double) searchResults.size() / ITEMS_PER_PAGE));
        if (slot == 51 && currentPage < totalPages) {
            currentPage++;
            setupMenu();
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            return;
        }
        if (slot == 52) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> new AddFriendMenu(plugin, friendsManager, player).open(), 2L);
            return;
        }
        for (int i = 0; i < RESULT_SLOTS.length; i++) {
            if (RESULT_SLOTS[i] != slot) {
                continue;
            }
            final int resultIndex = (currentPage - 1) * ITEMS_PER_PAGE + i;
            if (resultIndex >= searchResults.size()) {
                return;
            }
            handlePlayerClick(searchResults.get(resultIndex), event.getClick());
            break;
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
        if (event.getView().getTitle() != null && event.getView().getTitle().contains("§8» §aRésultats:")) {
            HandlerList.unregisterAll(this);
        }
    }

    private void handlePlayerClick(final Player targetPlayer, final org.bukkit.event.inventory.ClickType clickType) {
        switch (clickType) {
            case LEFT -> handleSendRequest(targetPlayer);
            case MIDDLE -> {
                player.sendMessage("§6👤 Profil détaillé en développement !");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case RIGHT -> {
                player.sendMessage("§b📝 Ajout aux suggestions en développement !");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            default -> {
            }
        }
    }

    private void handleSendRequest(final Player targetPlayer) {
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            player.sendMessage("§cCe joueur n'est plus en ligne.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            return;
        }
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> new SendRequestMenu(plugin, friendsManager, player, targetPlayer).open(), 2L);
    }
}
