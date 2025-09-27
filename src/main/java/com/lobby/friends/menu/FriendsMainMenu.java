package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.menu.statistics.FriendStatisticsMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Modernised friends main menu that focuses on providing immediate feedback
 * to the player while asynchronous data is being fetched.
 */
public class FriendsMainMenu extends BaseFriendsMenu {

    private static final String MENU_TITLE = "§8» §aMenu des Amis";
    private static final int INVENTORY_SIZE = 54;

    private Inventory inventory;
    private int totalFriends;
    private int onlineFriends;
    private int pendingRequests;

    public FriendsMainMenu(final LobbyPlugin plugin, final FriendsManager friendsManager) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public FriendsMainMenu(final LobbyPlugin plugin,
                           final FriendsManager friendsManager,
                           final FriendsMenuManager menuManager,
                           final Player player) {
        super(plugin, friendsManager, menuManager, player);
    }

    @Override
    protected void openMenu() {
        final Player viewer = getPlayer();
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE, MENU_TITLE);
        setupMenuWithDefaults();
        viewer.openInventory(inventory);
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        loadDataAndCreateMenu(viewer);
    }

    private void loadDataAndCreateMenu(final Player player) {
        totalFriends = 0;
        onlineFriends = 0;
        pendingRequests = 0;

        final CompletableFuture<?> friendsFuture = friendsManager.getFriends(player)
                .thenAccept(friends -> {
                    final int size = friends != null ? friends.size() : 0;
                    totalFriends = size;
                    if (friends != null && !friends.isEmpty()) {
                        onlineFriends = (int) friends.stream()
                                .filter(friend -> friend != null && friend.isOnline())
                                .count();
                    }
                })
                .exceptionally(throwable -> {
                    totalFriends = 0;
                    onlineFriends = 0;
                    return null;
                });
        final CompletableFuture<?> requestsFuture = friendsManager.getPendingRequests(player)
                .thenAccept(requests -> pendingRequests = requests != null ? requests.size() : 0)
                .exceptionally(throwable -> {
                    pendingRequests = 0;
                    return null;
                });

        CompletableFuture.allOf(friendsFuture, requestsFuture)
                .whenComplete((ignored, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement des données du menu des amis", throwable);
                    }
                    if (player == null || !player.isOnline()) {
                        return;
                    }
                    setupMenuWithRealData();
                    player.updateInventory();
                }));
    }

    private void setupMenuWithDefaults() {
        if (inventory == null) {
            return;
        }
        inventory.clear();

        final ItemStack greenGlass = createItem(Material.GREEN_STAINED_GLASS_PANE, " ");
        final int[] glassSlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53};
        for (int slot : glassSlots) {
            inventory.setItem(slot, greenGlass);
        }

        setupMainItems(0, 0, 0);
    }

    private void setupMenuWithRealData() {
        if (inventory == null) {
            return;
        }
        setupMainItems(totalFriends, onlineFriends, pendingRequests);
    }

    private void setupMainItems(final int friends, final int online, final int requestCount) {
        if (inventory == null) {
            return;
        }

        final ItemStack friendsList = createItem(Material.PLAYER_HEAD, "§a§l👥 Liste des Amis");
        final ItemMeta friendsMeta = friendsList.getItemMeta();
        if (friendsMeta != null) {
            friendsMeta.setLore(Arrays.asList(
                    "§7Consultez et gérez votre liste d'amis",
                    "",
                    "§a▸ Total d'amis: §2" + friends,
                    "§a▸ En ligne: §2" + online,
                    "§a▸ Hors ligne: §8" + Math.max(0, friends - online),
                    "",
                    "§8» §aCliquez pour ouvrir"
            ));
            friendsList.setItemMeta(friendsMeta);
        }
        inventory.setItem(11, friendsList);

        final ItemStack addFriend = createItem(Material.EMERALD, "§e§l➕ Ajouter un Ami");
        final ItemMeta addMeta = addFriend.getItemMeta();
        if (addMeta != null) {
            addMeta.setLore(Arrays.asList(
                    "§7Ajoutez de nouveaux amis",
                    "",
                    "§e▸ Recherche par nom",
                    "§e▸ Suggestions intelligentes",
                    "§e▸ Joueurs à proximité",
                    "",
                    "§8» §eCliquez pour explorer"
            ));
            addFriend.setItemMeta(addMeta);
        }
        inventory.setItem(13, addFriend);

        final ItemStack requests = createItem(Material.WRITABLE_BOOK, "§6§l📨 Demandes d'Amitié");
        final ItemMeta requestsMeta = requests.getItemMeta();
        if (requestsMeta != null) {
            requestsMeta.setLore(Arrays.asList(
                    "§7Gérez vos demandes d'amitié",
                    "",
                    "§6▸ Demandes reçues: §c" + requestCount,
                    requestCount > 0 ? "§c⚠ Demandes en attente !" : "§7Aucune demande",
                    "",
                    "§8» §6Cliquez pour gérer"
            ));
            requests.setItemMeta(requestsMeta);
        }
        inventory.setItem(15, requests);

        final ItemStack favorites = createItem(Material.NETHER_STAR, "§e⭐ Amis Favoris");
        final ItemMeta favMeta = favorites.getItemMeta();
        if (favMeta != null) {
            favMeta.setLore(Arrays.asList(
                    "§7Accès rapide à vos amis favoris",
                    "",
                    "§e▸ Téléportation prioritaire",
                    "§e▸ Notifications spéciales",
                    "",
                    "§8» §eCliquez pour voir"
            ));
            favorites.setItemMeta(favMeta);
        }
        inventory.setItem(20, favorites);

        final ItemStack settings = createItem(Material.REDSTONE_TORCH, "§6⚙️ Paramètres");
        final ItemMeta settingsMeta = settings.getItemMeta();
        if (settingsMeta != null) {
            settingsMeta.setLore(Arrays.asList(
                    "§7Configurez vos préférences",
                    "",
                    "§6▸ Notifications",
                    "§6▸ Confidentialité",
                    "§6▸ Permissions",
                    "",
                    "§8» §6Cliquez pour configurer"
            ));
            settings.setItemMeta(settingsMeta);
        }
        inventory.setItem(22, settings);

        final ItemStack stats = createItem(Material.BOOK, "§b📊 Statistiques");
        final ItemMeta statsMeta = stats.getItemMeta();
        if (statsMeta != null) {
            statsMeta.setLore(Arrays.asList(
                    "§7Consultez vos statistiques détaillées",
                    "",
                    "§b▸ Analyses temporelles",
                    "§b▸ Activité des amis",
                    "§b▸ Comparaisons serveur",
                    "",
                    "§8» §bCliquez pour analyser"
            ));
            stats.setItemMeta(statsMeta);
        }
        inventory.setItem(24, stats);

        final ItemStack blocked = createItem(Material.BARRIER, "§c🚫 Joueurs Bloqués");
        final ItemMeta blockedMeta = blocked.getItemMeta();
        if (blockedMeta != null) {
            blockedMeta.setLore(Arrays.asList(
                    "§7Gérez votre liste de blocage",
                    "",
                    "§c▸ Joueurs bloqués: §40",
                    "",
                    "§8» §cCliquez pour gérer"
            ));
            blocked.setItemMeta(blockedMeta);
        }
        inventory.setItem(29, blocked);

        final ItemStack close = createItem(Material.BARRIER, "§c✗ Fermer");
        final ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setLore(Arrays.asList(
                    "§7Fermer le menu des amis",
                    "",
                    "§8» §cCliquez pour fermer"
            ));
            close.setItemMeta(closeMeta);
        }
        inventory.setItem(49, close);
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

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public String getTitle() {
        return MENU_TITLE;
    }

    @Override
    public void handleMenuClick(final InventoryClickEvent event) {
        if (!titlesMatch(MENU_TITLE, event.getView().getTitle())) {
            return;
        }
        final Player clicker = getPlayer();
        if (clicker == null) {
            return;
        }
        final int slot = event.getSlot();
        switch (slot) {
            case 11 -> openFriendsList(clicker);
            case 13 -> openAddFriend(clicker);
            case 15 -> openRequests(clicker);
            case 20 -> openFavorites(clicker);
            case 22 -> openSettings(clicker);
            case 24 -> openStatistics(clicker);
            case 29 -> openBlocked(clicker);
            case 49 -> clicker.closeInventory();
            default -> {
            }
        }
    }

    @Override
    public void handleMenuClose(final InventoryCloseEvent event) {
        if (!titlesMatch(MENU_TITLE, event.getView().getTitle())) {
            return;
        }
        inventory = null;
        super.handleMenuClose(event);
    }

    private void openFriendsList(final Player player) {
        try {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> new FriendsListMenu(plugin, friendsManager, player), 3L);
        } catch (Exception exception) {
            player.sendMessage("§cErreur lors de l'ouverture de la liste");
            plugin.getLogger().log(Level.SEVERE, "Impossible d'ouvrir la liste des amis", exception);
        }
    }

    private void openAddFriend(final Player player) {
        try {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> new AddFriendMenu(plugin, friendsManager, player).open(), 3L);
        } catch (Exception exception) {
            player.sendMessage("§cErreur lors de l'ouverture du menu d'ajout");
            plugin.getLogger().log(Level.SEVERE, "Impossible d'ouvrir le menu d'ajout d'amis", exception);
        }
    }

    private void openRequests(final Player player) {
        try {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> new FriendRequestsMenu(plugin, friendsManager, menuManager, player).open(), 3L);
        } catch (Exception exception) {
            player.sendMessage("§cErreur lors de l'ouverture des demandes");
            plugin.getLogger().log(Level.SEVERE, "Impossible d'ouvrir les demandes d'amis", exception);
        }
    }

    private void openFavorites(final Player player) {
        try {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> new FavoriteFriendsMenu(plugin, friendsManager, menuManager, player).open(), 3L);
        } catch (Exception exception) {
            player.sendMessage("§cErreur lors de l'ouverture des favoris");
            plugin.getLogger().log(Level.SEVERE, "Impossible d'ouvrir les amis favoris", exception);
        }
    }

    private void openSettings(final Player player) {
        try {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> new FriendSettingsMenu(plugin, friendsManager, menuManager, player).open(), 3L);
        } catch (Exception exception) {
            player.sendMessage("§cErreur lors de l'ouverture des paramètres");
            plugin.getLogger().log(Level.SEVERE, "Impossible d'ouvrir les paramètres d'amis", exception);
        }
    }

    private void openStatistics(final Player player) {
        try {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> new FriendStatisticsMenu(plugin, friendsManager, player).open(), 3L);
        } catch (Exception exception) {
            player.sendMessage("§cErreur lors de l'ouverture des statistiques");
            plugin.getLogger().log(Level.SEVERE, "Impossible d'ouvrir les statistiques d'amis", exception);
        }
    }

    private void openBlocked(final Player player) {
        try {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> new BlockedPlayersMenu(plugin, friendsManager, player).open(), 3L);
        } catch (Exception exception) {
            player.sendMessage("§cErreur lors de l'ouverture des bloqués");
            plugin.getLogger().log(Level.SEVERE, "Impossible d'ouvrir la liste des joueurs bloqués", exception);
        }
    }
}
