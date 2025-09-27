package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.data.FriendRequest;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.menu.FriendsMainMenu;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Menu listing all pending friend requests with quick actions to accept or
 * reject them.
 */
public class FriendRequestsMenu implements Listener {

    private static final String TITLE_PREFIX = "§8» §6Demandes d'Amitié";
    private static final int SIZE = 54;
    private static final int ITEMS_PER_PAGE = 21;
    private static final int[] REQUEST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;

    private Inventory inventory;
    private List<FriendRequest> allRequests = Collections.emptyList();
    private int currentPage = 1;

    public FriendRequestsMenu(final LobbyPlugin plugin, final FriendsManager friendsManager, final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadRequestsAndCreateMenu();
    }

    private void loadRequestsAndCreateMenu() {
        friendsManager.getPendingRequests(player).thenAccept(requests -> {
            allRequests = requests != null ? requests : Collections.emptyList();
            Bukkit.getScheduler().runTask(plugin, () -> {
                createMenu();
                open();
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Erreur chargement demandes: " + throwable.getMessage());
            allRequests = Collections.emptyList();
            Bukkit.getScheduler().runTask(plugin, () -> {
                createMenu();
                open();
            });
            return null;
        });
    }

    private void createMenu() {
        final String title = TITLE_PREFIX + " (" + allRequests.size() + ")";
        inventory = Bukkit.createInventory(null, SIZE, title);
        setupMenu();
    }

    private void setupMenu() {
        if (inventory == null) {
            return;
        }
        inventory.clear();

        final ItemStack goldGlass = createItem(Material.YELLOW_STAINED_GLASS_PANE, " ");
        final int[] goldSlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 53};
        for (int slot : goldSlots) {
            inventory.setItem(slot, goldGlass);
        }

        displayRequests();
        setupActions();
    }

    private void displayRequests() {
        if (allRequests.isEmpty()) {
            final ItemStack noRequests = createItem(Material.PAPER, "§7§lAucune demande d'amitié");
            final ItemMeta meta = noRequests.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList(
                        "§7Vous n'avez aucune demande",
                        "§7d'amitié en attente",
                        "",
                        "§a✓ Votre boîte de réception est vide !",
                        "",
                        "§7Les nouvelles demandes apparaîtront ici",
                        "§7quand vous en recevrez",
                        "",
                        "§e💡 Partagez votre code d'ami:",
                        "§f#" + player.getName().toUpperCase().substring(0, Math.min(4, player.getName().length())) + "1234"
                ));
                noRequests.setItemMeta(meta);
            }
            inventory.setItem(22, noRequests);
            return;
        }

        final int startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
        final int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allRequests.size());
        for (int i = 0; i < REQUEST_SLOTS.length && (startIndex + i) < endIndex; i++) {
            final FriendRequest request = allRequests.get(startIndex + i);
            inventory.setItem(REQUEST_SLOTS[i], createRequestItem(request));
        }
    }

    private ItemStack createRequestItem(final FriendRequest request) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta baseMeta = head.getItemMeta();
        if (!(baseMeta instanceof SkullMeta meta)) {
            return head;
        }

        try {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(request.getSenderUuid())));
        } catch (IllegalArgumentException ignored) {
            // keep default skin
        }

        final String senderName = request.getSenderName();
        meta.setDisplayName("§6§l📨 " + senderName);

        final List<String> lore = new ArrayList<>();
        lore.add("§7Demande d'amitié reçue");
        lore.add("");
        lore.add("§7Expéditeur: §e" + senderName);
        lore.add("§7Date: §b" + request.getRelativeDate());
        lore.add("");
        lore.add("§7Message personnel:");
        lore.add("§f\"" + request.getDisplayMessage() + "\"");
        lore.add("");
        lore.add("§7Actions disponibles:");
        lore.add("§8▸ §aClique gauche §8: §2✓ Accepter");
        lore.add("§8▸ §cClique droit §8: §4✗ Refuser");
        lore.add("§8▸ §eClique milieu §8: §6👤 Voir le profil");

        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private void setupActions() {
        if (!allRequests.isEmpty()) {
            final ItemStack acceptAll = createItem(Material.EMERALD, "§a§l✓ Accepter Toutes");
            final ItemMeta acceptMeta = acceptAll.getItemMeta();
            if (acceptMeta != null) {
                acceptMeta.setLore(Arrays.asList(
                        "§7Accepter toutes les demandes en attente",
                        "",
                        "§a▸ Demandes à accepter: §2" + allRequests.size(),
                        "",
                        "§7Cette action ajoutera tous les",
                        "§7expéditeurs à votre liste d'amis",
                        "",
                        "§8» §aCliquez pour accepter toutes"
                ));
                acceptAll.setItemMeta(acceptMeta);
            }
            inventory.setItem(45, acceptAll);

            final ItemStack rejectAll = createItem(Material.REDSTONE, "§c§l✗ Refuser Toutes");
            final ItemMeta rejectMeta = rejectAll.getItemMeta();
            if (rejectMeta != null) {
                rejectMeta.setLore(Arrays.asList(
                        "§7Refuser toutes les demandes en attente",
                        "",
                        "§c▸ Demandes à refuser: §4" + allRequests.size(),
                        "",
                        "§c⚠ Cette action est irréversible !",
                        "",
                        "§8» §cCliquez pour refuser toutes"
                ));
                rejectAll.setItemMeta(rejectMeta);
            }
            inventory.setItem(46, rejectAll);
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
        final ItemMeta refreshMeta = refresh.getItemMeta();
        if (refreshMeta != null) {
            refreshMeta.setLore(Arrays.asList(
                    "§7Actualiser la liste des demandes",
                    "",
                    "§8» §bCliquez pour actualiser"
            ));
            refresh.setItemMeta(refreshMeta);
        }
        inventory.setItem(52, refresh);
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
        if (title == null || !title.contains(TITLE_PREFIX)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        final int slot = event.getSlot();
        if (slot == 45 && !allRequests.isEmpty()) {
            handleAcceptAll();
            return;
        }
        if (slot == 46 && !allRequests.isEmpty()) {
            handleRejectAll();
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> new FriendsMainMenu(plugin, friendsManager).open(player), 3L);
            return;
        }
        if (slot == 52) {
            player.sendMessage("§b🔄 Actualisation des demandes...");
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            loadRequestsAndCreateMenu();
            return;
        }

        for (int i = 0; i < REQUEST_SLOTS.length; i++) {
            if (REQUEST_SLOTS[i] != slot) {
                continue;
            }
            final int requestIndex = (currentPage - 1) * ITEMS_PER_PAGE + i;
            if (requestIndex >= allRequests.size()) {
                return;
            }
            handleRequestClick(allRequests.get(requestIndex), event.getClick());
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
        if (event.getView().getTitle() != null && event.getView().getTitle().contains(TITLE_PREFIX)) {
            HandlerList.unregisterAll(this);
        }
    }

    private void handleRequestClick(final FriendRequest request, final ClickType clickType) {
        switch (clickType) {
            case LEFT -> handleAcceptRequest(request);
            case RIGHT -> handleRejectRequest(request);
            case MIDDLE -> handleViewProfile(request);
            default -> {
            }
        }
    }

    private void handleAcceptRequest(final FriendRequest request) {
        player.sendMessage("§a✓ Demande de " + request.getSenderName() + " acceptée !");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        friendsManager.acceptFriendRequest(player, request.getSenderName()).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, this::loadRequestsAndCreateMenu));
    }

    private void handleRejectRequest(final FriendRequest request) {
        player.sendMessage("§c✗ Demande de " + request.getSenderName() + " refusée");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
        friendsManager.rejectFriendRequest(player, request.getSenderName()).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, this::loadRequestsAndCreateMenu));
    }

    private void handleViewProfile(final FriendRequest request) {
        player.sendMessage("§6👤 Profil de " + request.getSenderName() + ":");
        player.sendMessage("§7- Niveau: §aInconnu");
        player.sendMessage("§7- Temps de jeu: §bInconnu");
        player.sendMessage("§7- Dernière connexion: §e" + (request.isSenderOnline() ? "En ligne" : request.getRelativeDate()));
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
    }

    private void handleAcceptAll() {
        player.sendMessage("§a✅ Acceptation de toutes les demandes (" + allRequests.size() + ")...");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        allRequests.forEach(request -> friendsManager.acceptFriendRequest(player, request.getSenderName()));
        Bukkit.getScheduler().runTaskLater(plugin, this::loadRequestsAndCreateMenu, 20L);
    }

    private void handleRejectAll() {
        player.sendMessage("§c❌ Refus de toutes les demandes (" + allRequests.size() + ")...");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
        allRequests.forEach(request -> friendsManager.rejectFriendRequest(player, request.getSenderName()));
        Bukkit.getScheduler().runTaskLater(plugin, this::loadRequestsAndCreateMenu, 20L);
    }
}
