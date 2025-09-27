package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.menu.FriendsMainMenu;
import com.lobby.friends.menu.FriendsMenuManager;
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

/**
 * Menu dedicated to the different options available when adding a friend.
 */
public class AddFriendMenu implements Listener {

    private static final String TITLE = "§8» §aAjouter un Ami";
    private static final int SIZE = 54;

    private static final int SEARCH_SLOT = 20;
    private static final int SUGGESTIONS_SLOT = 22;
    private static final int NEARBY_SLOT = 24;
    private static final int IMPORT_SLOT = 30;
    private static final int PENDING_SLOT = 32;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

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
        updatePendingInvitations();
    }

    private void setupMenu() {
        final ItemStack greenGlass = createItem(Material.LIME_STAINED_GLASS_PANE, " ");
        final int[] greenSlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53};
        for (int slot : greenSlots) {
            inventory.setItem(slot, greenGlass);
        }

        final ItemStack search = createItem(Material.COMPASS, "§b🔍 Recherche par Nom");
        final ItemMeta searchMeta = search.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setLore(Arrays.asList(
                    "§7Recherchez un joueur par son nom",
                    "",
                    "§b▸ Recherche exacte",
                    "§b▸ Suggestions automatiques",
                    "§b▸ Historique des recherches",
                    "",
                    "§8» §bCliquez pour rechercher"
            ));
            search.setItemMeta(searchMeta);
        }
        inventory.setItem(SEARCH_SLOT, search);

        final ItemStack suggestions = createItem(Material.LIGHT_BLUE_DYE, "§e💡 Suggestions Intelligentes");
        final ItemMeta suggestionsMeta = suggestions.getItemMeta();
        if (suggestionsMeta != null) {
            suggestionsMeta.setLore(Arrays.asList(
                    "§7Découvrez des joueurs compatibles",
                    "",
                    "§e▸ Basé sur vos activités",
                    "§e▸ Amis d'amis",
                    "§e▸ Centres d'intérêt communs",
                    "",
                    "§8» §eCliquez pour voir les suggestions"
            ));
            suggestions.setItemMeta(suggestionsMeta);
        }
        inventory.setItem(SUGGESTIONS_SLOT, suggestions);

        final ItemStack nearby = createItem(Material.ENDER_PEARL, "§6🌍 Joueurs à Proximité");
        final ItemMeta nearbyMeta = nearby.getItemMeta();
        if (nearbyMeta != null) {
            nearbyMeta.setLore(Arrays.asList(
                    "§7Trouvez des joueurs près de vous",
                    "",
                    "§6▸ Même serveur",
                    "§6▸ Même monde",
                    "§6▸ Distance configurable",
                    "",
                    "§8» §6Cliquez pour explorer"
            ));
            nearby.setItemMeta(nearbyMeta);
        }
        inventory.setItem(NEARBY_SLOT, nearby);

        final ItemStack importCode = createItem(Material.WRITABLE_BOOK, "§d💾 Code d'Ami");
        final ItemMeta importMeta = importCode.getItemMeta();
        if (importMeta != null) {
            final String code = "#" + player.getName().toUpperCase().substring(0, Math.min(4, player.getName().length())) + "1234";
            importMeta.setLore(Arrays.asList(
                    "§7Utilisez un code d'ami pour",
                    "§7ajouter rapidement quelqu'un",
                    "",
                    "§d▸ Votre code: §f" + code,
                    "§d▸ Saisissez un code reçu",
                    "",
                    "§8» §dCliquez pour utiliser un code"
            ));
            importCode.setItemMeta(importMeta);
        }
        inventory.setItem(IMPORT_SLOT, importCode);

        setPendingItem(0);

        final ItemStack back = createItem(Material.ARROW, "§c« Retour");
        final ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setLore(Arrays.asList(
                    "§7Revenir au menu principal",
                    "",
                    "§8» §cCliquez pour retourner"
            ));
            back.setItemMeta(backMeta);
        }
        inventory.setItem(BACK_SLOT, back);

        final ItemStack close = createItem(Material.BARRIER, "§c✕ Fermer");
        final ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setLore(Arrays.asList(
                    "§7Fermer le menu",
                    "",
                    "§8» §cCliquez pour fermer"
            ));
            close.setItemMeta(closeMeta);
        }
        inventory.setItem(CLOSE_SLOT, close);
    }

    private void updatePendingInvitations() {
        friendsManager.getPendingRequests(player).thenAccept(requests ->
                Bukkit.getScheduler().runTask(plugin, () -> setPendingItem(requests != null ? requests.size() : 0))
        );
    }

    private void setPendingItem(final int pendingCount) {
        final ItemStack pending = createItem(Material.CLOCK, "§c⏳ Invitations Envoyées");
        final ItemMeta pendingMeta = pending.getItemMeta();
        if (pendingMeta != null) {
            pendingMeta.setLore(Arrays.asList(
                    "§7Consultez vos invitations en attente",
                    "",
                    "§c▸ En attente: §4" + pendingCount,
                    "§c▸ Expirées: §40",
                    "",
                    "§8» §cCliquez pour gérer"
            ));
            pending.setItemMeta(pendingMeta);
        }
        inventory.setItem(PENDING_SLOT, pending);
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
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        switch (event.getSlot()) {
            case SEARCH_SLOT -> handleSearch();
            case SUGGESTIONS_SLOT -> handleSuggestions();
            case NEARBY_SLOT -> handleNearbyPlayers();
            case IMPORT_SLOT -> handleFriendCode();
            case PENDING_SLOT -> handlePendingInvitations();
            case BACK_SLOT -> handleBack();
            case CLOSE_SLOT -> handleClose();
            default -> {
            }
        }
    }

    private void handleClose() {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        HandlerList.unregisterAll(this);
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
        player.sendMessage("§b🔍 Recherche de joueur");
        player.sendMessage("§7Tapez le nom du joueur dans le chat:");
        player.sendMessage("§7(ou tapez 'cancel' pour annuler)");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    private void handleSuggestions() {
        player.sendMessage("§e💡 Suggestions intelligentes:");
        player.sendMessage("§7- Analyse des joueurs compatibles");
        player.sendMessage("§7- Basé sur vos activités communes");
        player.sendMessage("§7- Amis d'amis potentiels");
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
    }

    private void handleNearbyPlayers() {
        player.sendMessage("§6🌍 Joueurs à proximité:");

        int nearbyCount = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (!online.getWorld().equals(player.getWorld())) {
                continue;
            }
            final double distance = online.getLocation().distance(player.getLocation());
            if (distance <= 100) {
                player.sendMessage("§8▸ §e" + online.getName() + " §7(§a" + String.format("%.1f", distance) + "m§7)");
                nearbyCount++;
            }
        }

        if (nearbyCount == 0) {
            player.sendMessage("§7Aucun joueur trouvé dans un rayon de 100 blocs");
        }

        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
    }

    private void handleFriendCode() {
        player.closeInventory();
        player.sendMessage("§d💾 Code d'ami:");
        player.sendMessage("§7Votre code: §f#" + player.getName().toUpperCase().substring(0, Math.min(4, player.getName().length())) + "1234");
        player.sendMessage("§7Tapez le code d'un ami pour l'ajouter:");
        player.sendMessage("§7(ou tapez 'cancel' pour annuler)");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    private void handlePendingInvitations() {
        player.sendMessage("§c⏳ Invitations envoyées:");
        player.sendMessage("§7- Aucune invitation en attente");
        player.sendMessage("§7- Toutes vos demandes ont été traitées");
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
    }

    private void handleBack() {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        final FriendsMenuManager menuManager = plugin.getFriendsMenuManager();
        if (menuManager == null) {
            player.sendMessage("§cLe gestionnaire de menus d'amis est indisponible.");
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> new FriendsMainMenu(plugin, friendsManager, menuManager, player).open(), 3L);
    }
}
