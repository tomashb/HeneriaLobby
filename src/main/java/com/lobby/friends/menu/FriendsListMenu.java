package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.data.FriendData;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Paginated friends list menu with rich lore and quick actions inspired by the
 * design specification provided by the product team.
 */
public class FriendsListMenu implements Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 28;
    private static final String TITLE_FORMAT = "§8» §aListe des Amis (%d) - Page %d/%d";
    private static final String TITLE_PREFIX = "§8» §aListe des Amis";

    private static final int[] FRIEND_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;

    private Inventory inventory;
    private List<FriendData> allFriends = new ArrayList<>();
    private int currentPage = 1;
    private boolean active = true;
    private boolean switchingInventory;
    private boolean firstRender = true;

    public FriendsListMenu(final LobbyPlugin plugin, final FriendsManager friendsManager, final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadFriendsAndCreateMenu();
    }

    private void loadFriendsAndCreateMenu() {
        friendsManager.getFriends(player).thenAccept(friends -> {
            allFriends = friends != null ? new ArrayList<>(friends) : new ArrayList<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!active || !player.isOnline()) {
                    return;
                }
                renderMenu();
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "Erreur chargement amis: " + throwable.getMessage(), throwable);
            allFriends = new ArrayList<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!active || !player.isOnline()) {
                    return;
                }
                renderMenu();
            });
            return null;
        });
    }

    private void renderMenu() {
        final int totalPages = Math.max(1, (int) Math.ceil((double) allFriends.size() / ITEMS_PER_PAGE));
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        if (currentPage < 1) {
            currentPage = 1;
        }
        final String title = String.format(TITLE_FORMAT, allFriends.size(), currentPage, totalPages);
        inventory = Bukkit.createInventory(null, INVENTORY_SIZE, title);
        setupMenu();
        if (!player.isOnline()) {
            return;
        }
        switchingInventory = true;
        player.openInventory(inventory);
        Bukkit.getScheduler().runTask(plugin, () -> switchingInventory = false);
        if (firstRender) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            firstRender = false;
        }
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

        displayFriends();
        setupNavigation();
    }

    private void displayFriends() {
        if (allFriends.isEmpty()) {
            final ItemStack noFriends = createItem(Material.PAPER, "§7§lAucun ami trouvé");
            final ItemMeta meta = noFriends.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList(
                        "§7Vous n'avez pas encore d'amis",
                        "",
                        "§e💡 Comment ajouter des amis ?",
                        "§8▸ §7Utilisez le menu 'Ajouter un Ami'",
                        "§8▸ §7Recherchez des joueurs",
                        "§8▸ §7Consultez les suggestions",
                        "",
                        "§8» §aCommencez à vous faire des amis !"
                ));
                noFriends.setItemMeta(meta);
            }
            inventory.setItem(22, noFriends);
            return;
        }

        final int startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
        final int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allFriends.size());

        for (int i = 0; i < FRIEND_SLOTS.length && (startIndex + i) < endIndex; i++) {
            final FriendData friend = allFriends.get(startIndex + i);
            inventory.setItem(FRIEND_SLOTS[i], createFriendItem(friend));
        }
    }

    private ItemStack createFriendItem(final FriendData friend) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta itemMeta = head.getItemMeta();
        if (!(itemMeta instanceof SkullMeta meta)) {
            return head;
        }

        try {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(friend.getUuid())));
        } catch (IllegalArgumentException ignored) {
            // keep default skin when UUID is invalid
        }

        final boolean online = friend.isOnline();
        final boolean favorite = friend.isFavorite();
        final String baseName = friend.getPlayerName();
        final String statusDot = online ? " §2●" : " §8●";
        final String name = switch ((favorite ? 2 : 0) + (online ? 1 : 0)) {
            case 3 -> "§e⭐ §a§l" + baseName + statusDot;
            case 1 -> "§a§l" + baseName + statusDot;
            case 2 -> "§e⭐ §7§l" + baseName + statusDot;
            default -> "§7§l" + baseName + statusDot;
        };
        meta.setDisplayName(name);

        final List<String> lore = new ArrayList<>();
        lore.add(favorite ? "§6✨ AMI FAVORI" : "§7Ami");
        lore.add("");
        if (online) {
            lore.add("§7Statut: §aEn ligne");
            lore.add("§7Serveur: §eLobby");
        } else {
            lore.add("§7Statut: §cHors ligne");
            lore.add("§7Dernière connexion: §e" + friend.getRelativeLastInteraction());
        }
        lore.add("§7Ami depuis: §b" + friend.getFormattedFriendshipDate());
        lore.add("§7Messages échangés: §d" + friend.getMessagesExchanged());
        lore.add("");
        if (online) {
            lore.add("§8▸ §aClique gauche §8: §7Téléportation");
            lore.add("§8▸ §eClique milieu §8: §7Message privé");
        } else {
            lore.add("§8▸ §eClique milieu §8: §7Message hors-ligne");
        }
        lore.add("§8▸ §cClique droit §8: §7Menu d'options");
        lore.add("§8▸ §6Shift-Clic §8: §7Toggle favori");

        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private void setupNavigation() {
        final int totalPages = Math.max(1, (int) Math.ceil((double) allFriends.size() / ITEMS_PER_PAGE));

        if (currentPage > 1) {
            final ItemStack previous = createItem(Material.ARROW, "§c◀ Page Précédente");
            inventory.setItem(47, previous);
        }

        final ItemStack back = createItem(Material.BARRIER, "§e🏠 Retour Menu Principal");
        final ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setLore(Arrays.asList(
                    "§7Revenir au menu principal des amis",
                    "",
                    "§8» §eCliquez pour retourner"
            ));
            back.setItemMeta(backMeta);
        }
        inventory.setItem(49, back);

        final ItemStack refresh = createItem(Material.CLOCK, "§b🔄 Actualiser");
        inventory.setItem(50, refresh);

        if (currentPage < totalPages) {
            final ItemStack next = createItem(Material.ARROW, "§a▶ Page Suivante");
            inventory.setItem(51, next);
        }
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

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        final String title = event.getView().getTitle();
        if (!title.startsWith(TITLE_PREFIX)) {
            return;
        }

        event.setCancelled(true);

        final int slot = event.getSlot();
        if (slot == 47 && currentPage > 1) {
            currentPage--;
            renderMenu();
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                final FriendsMenuController controller = plugin.getFriendsMenuController();
                if (controller != null) {
                    controller.openMainMenu(player);
                }
            }, 3L);
            return;
        }
        if (slot == 50) {
            player.sendMessage("§b🔄 Actualisation de la liste...");
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            loadFriendsAndCreateMenu();
            return;
        }
        final int totalPages = Math.max(1, (int) Math.ceil((double) allFriends.size() / ITEMS_PER_PAGE));
        if (slot == 51 && currentPage < totalPages) {
            currentPage++;
            renderMenu();
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            return;
        }

        for (int i = 0; i < FRIEND_SLOTS.length; i++) {
            if (FRIEND_SLOTS[i] != slot) {
                continue;
            }
            final int friendIndex = (currentPage - 1) * ITEMS_PER_PAGE + i;
            if (friendIndex >= allFriends.size()) {
                return;
            }
            handleFriendClick(allFriends.get(friendIndex), event.getClick());
            return;
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
        if (!event.getView().getTitle().startsWith(TITLE_PREFIX)) {
            return;
        }
        if (switchingInventory) {
            return;
        }
        active = false;
        HandlerList.unregisterAll(this);
    }

    private void handleFriendClick(final FriendData friend, final ClickType clickType) {
        // Les actions détaillées (téléportation, messagerie, favoris, options)
        // sont désormais gérées par {@link FriendsListClickHandler}. Ce menu se
        // concentre sur l'affichage et la navigation.
    }
}
