package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.data.FriendData;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.menu.options.FriendOptionsMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fully featured friends list menu backed by the friends manager/database.
 */
public class FriendsListMenu implements Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 28;
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
    private String inventoryTitle;
    private List<FriendData> allFriends = Collections.emptyList();
    private int currentPage = 1;
    private final AtomicBoolean opened = new AtomicBoolean(false);

    public FriendsListMenu(final LobbyPlugin plugin, final FriendsManager friendsManager, final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadFriendsAndCreateMenu();
    }

    private void loadFriendsAndCreateMenu() {
        friendsManager.getFriends(player).thenAccept(friends -> {
            this.allFriends = friends != null ? friends : Collections.emptyList();
            Bukkit.getScheduler().runTask(plugin, () -> {
                createInventory();
                setupMenu();
                if (opened.compareAndSet(false, true)) {
                    open();
                } else {
                    player.updateInventory();
                }
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Erreur lors du chargement des amis : " + throwable.getMessage());
            this.allFriends = Collections.emptyList();
            Bukkit.getScheduler().runTask(plugin, () -> {
                createInventory();
                setupMenu();
                if (opened.compareAndSet(false, true)) {
                    open();
                } else {
                    player.updateInventory();
                }
            });
            return null;
        });
    }

    private void createInventory() {
        final int totalPages = Math.max(1, (int) Math.ceil((double) allFriends.size() / ITEMS_PER_PAGE));
        inventoryTitle = "§8» §aListe des Amis (" + currentPage + "/" + totalPages + ")";
        inventory = Bukkit.createInventory(null, INVENTORY_SIZE, inventoryTitle);
    }

    private void setupMenu() {
        if (inventory == null) {
            return;
        }
        inventory.clear();

        final ItemStack greenGlass = createItem(Material.GREEN_STAINED_GLASS_PANE, " ");
        final int[] greenSlots = {0, 1, 2, 6, 7, 8, 9, 17, 45, 46, 52, 53};
        for (int slot : greenSlots) {
            inventory.setItem(slot, greenGlass);
        }

        displayFriends();
        setupNavigation();
        setupOptions();
    }

    private void displayFriends() {
        if (allFriends.isEmpty()) {
            final ItemStack noFriends = createItem(Material.PAPER, "§7§lAucun ami pour le moment");
            final ItemMeta meta = noFriends.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList(
                        "§7Vous n'avez pas encore d'amis",
                        "§7ajoutés à votre liste",
                        "",
                        "§e💡 Conseil:",
                        "§7Utilisez le menu §e'Ajouter un ami'",
                        "§7pour trouver et ajouter des amis !",
                        "",
                        "§8▸ §eCommande: §6/friends add <nom>",
                        "§8▸ §eMenu: §6Retour → Ajouter un ami"
                ));
                noFriends.setItemMeta(meta);
            }
            inventory.setItem(22, noFriends);
            return;
        }

        final int startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
        final int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allFriends.size());
        for (int i = 0; i < FRIEND_SLOTS.length && startIndex + i < endIndex; i++) {
            final FriendData friend = allFriends.get(startIndex + i);
            inventory.setItem(FRIEND_SLOTS[i], createFriendItem(friend));
        }
    }

    private ItemStack createFriendItem(final FriendData friend) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta itemMeta = head.getItemMeta();
        if (itemMeta instanceof SkullMeta skullMeta) {
            try {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(java.util.UUID.fromString(friend.getUuid())));
            } catch (IllegalArgumentException ignored) {
                // keep default skin if uuid invalid
            }
            skullMeta.setDisplayName(buildFriendDisplayName(friend));
            skullMeta.setLore(buildFriendLore(friend));
            if (friend.isFavorite()) {
                skullMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                skullMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private String buildFriendDisplayName(final FriendData friend) {
        final String baseName = friend.getPlayerName();
        final String indicator = friend.getStatusIndicator();
        if (friend.isOnline()) {
            return friend.isFavorite() ? "§e⭐ §a§l" + baseName + " " + indicator : "§a§l" + baseName + " " + indicator;
        }
        return friend.isFavorite() ? "§e⭐ §7§l" + baseName + " " + indicator : "§7§l" + baseName + " " + indicator;
    }

    private List<String> buildFriendLore(final FriendData friend) {
        final List<String> lore = new ArrayList<>();
        if (friend.isFavorite()) {
            lore.add("§6✨ AMI FAVORI ✨");
            lore.add("");
        }
        if (friend.isOnline()) {
            lore.add("§7Statut: §aEn ligne");
            lore.add("§7Serveur: §eLobby");
        } else {
            lore.add("§7Statut: §cHors ligne");
            lore.add("§7Dernière connexion: §e" + friend.getRelativeLastInteraction());
        }
        lore.add("§7Ami depuis: §b" + friend.getFormattedFriendshipDate());
        lore.add("");
        lore.add("§3📊 Statistiques d'amitié:");
        lore.add("§8▸ §7Messages échangés: §b" + friend.getMessagesExchanged());
        lore.add("§8▸ §7Temps joué ensemble: §b" + friend.getFormattedTimeTogether());
        lore.add("§8▸ §7Dernière interaction: §b" + friend.getRelativeLastInteraction());
        if (friend.isFavorite()) {
            lore.add("§8▸ §6Statut: §eAmi favori ⭐");
        }
        lore.add("");
        if (friend.isOnline()) {
            lore.add("§8▸ §aClique gauche §8: §7Se téléporter");
            lore.add("§8▸ §eClique milieu §8: §7Envoyer un message");
        } else {
            lore.add("§8▸ §eClique milieu §8: §7Message hors-ligne");
        }
        lore.add("§8▸ §cClique droit §8: §7Plus d'options");
        lore.add(friend.isFavorite()
                ? "§8▸ §7Shift+Clic §8: §7Retirer des favoris"
                : "§8▸ §7Shift+Clic §8: §6Ajouter aux favoris");
        return lore;
    }

    private void setupNavigation() {
        final int totalPages = Math.max(1, (int) Math.ceil((double) allFriends.size() / ITEMS_PER_PAGE));
        if (currentPage > 1) {
            final ItemStack previous = createItem(Material.ARROW, "§c◀ Page Précédente", Arrays.asList(
                    "§7Aller à la page " + (currentPage - 1),
                    "",
                    "§8» §cCliquez pour revenir"
            ));
            inventory.setItem(47, previous);
        }

        final int onlineCount = (int) allFriends.stream().filter(FriendData::isOnline).count();
        final int favoriteCount = (int) allFriends.stream().filter(FriendData::isFavorite).count();
        final ItemStack back = createItem(Material.PLAYER_HEAD, "§e🏠 Retour Menu Principal", Arrays.asList(
                "§7Revenir au menu principal",
                "§7des amis",
                "",
                "§e▸ Total d'amis: §6" + allFriends.size(),
                "§e▸ En ligne: §6" + onlineCount,
                "§e▸ Favoris: §6" + favoriteCount,
                "",
                "§8» §eCliquez pour retourner"
        ));
        inventory.setItem(49, back);

        if (currentPage < totalPages) {
            final ItemStack next = createItem(Material.ARROW, "§a▶ Page Suivante", Arrays.asList(
                    "§7Aller à la page " + (currentPage + 1),
                    "",
                    "§8» §aCliquez pour continuer"
            ));
            inventory.setItem(51, next);
        }
    }

    private void setupOptions() {
        final ItemStack sort = createItem(Material.HOPPER, "§6📋 Tri: En ligne d'abord", Arrays.asList(
                "§7Trier la liste des amis",
                "",
                "§6▸ Tri actuel: §eEn ligne d'abord",
                "§7▸ Total d'amis: §8" + allFriends.size(),
                "",
                "§7Options de tri:",
                "§8▸ §aEn ligne d'abord",
                "§8▸ §7Alphabétique (A-Z)",
                "§8▸ §bDate d'amitié",
                "§8▸ §6Favoris d'abord",
                "",
                "§8» §6Cliquez pour changer"
        ));
        inventory.setItem(48, sort);

        final ItemStack refresh = createItem(Material.CLOCK, "§b🔄 Actualiser", Arrays.asList(
                "§7Actualiser la liste des amis",
                "§7et les statuts en ligne",
                "",
                "§b▸ Dernière MAJ: §3À l'instant",
                "",
                "§8» §bCliquez pour actualiser"
        ));
        inventory.setItem(50, refresh);
    }

    private ItemStack createItem(final Material material, final String name) {
        return createItem(material, name, new ArrayList<>());
    }

    private ItemStack createItem(final Material material, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open() {
        if (inventory == null) {
            Bukkit.getScheduler().runTaskLater(plugin, this::open, 2L);
            return;
        }
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
        if (inventory == null || inventoryTitle == null || !event.getView().getTitle().equals(inventoryTitle)) {
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
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                final FriendsMenuController controller = plugin.getFriendsMenuController();
                if (controller != null) {
                    controller.openMainMenu(player);
                }
            }, 1L);
            return;
        }
        final int totalPages = Math.max(1, (int) Math.ceil((double) allFriends.size() / ITEMS_PER_PAGE));
        if (slot == 51 && currentPage < totalPages) {
            currentPage++;
            setupMenu();
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            return;
        }
        if (slot == 48) {
            player.sendMessage("§6🔄 Tri des amis en cours de développement !");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }
        if (slot == 50) {
            player.sendMessage("§b🔄 Actualisation de la liste des amis...");
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            loadFriendsAndCreateMenu();
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
            handleFriendInteraction(allFriends.get(friendIndex), event.getClick());
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
        if (inventory != null && inventoryTitle != null && event.getView().getTitle().equals(inventoryTitle)) {
            HandlerList.unregisterAll(this);
        }
    }

    private void handleFriendInteraction(final FriendData friend, final ClickType clickType) {
        switch (clickType) {
            case LEFT -> handleTeleport(friend);
            case MIDDLE -> handleMessage(friend);
            case RIGHT -> openFriendOptions(friend);
            case SHIFT_LEFT, SHIFT_RIGHT -> toggleFavorite(friend);
            default -> {
            }
        }
    }

    private void handleTeleport(final FriendData friend) {
        final Player target = friend.getPlayer();
        if (target == null || !target.isOnline()) {
            player.sendMessage("§c" + friend.getPlayerName() + " n'est pas en ligne !");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            return;
        }
        player.closeInventory();
        player.teleport(target.getLocation());
        player.sendMessage("§aVous avez été téléporté chez " + friend.getPlayerName() + " !");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private void handleMessage(final FriendData friend) {
        player.closeInventory();
        player.sendMessage("§e💬 Tapez votre message pour " + friend.getPlayerName() + " dans le chat :");
        player.sendMessage("§7(ou tapez 'cancel' pour annuler)");
        // future integration with ChatPromptManager
    }

    private void openFriendOptions(final FriendData friend) {
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> new FriendOptionsMenu(plugin, friendsManager, player, friend).open(), 1L);
    }

    private void toggleFavorite(final FriendData friend) {
        friendsManager.toggleFavorite(player, friend.getPlayerName()).thenAccept(success -> {
            if (!success) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, this::loadFriendsAndCreateMenu);
        });
    }
}
