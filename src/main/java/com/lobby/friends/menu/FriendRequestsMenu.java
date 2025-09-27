package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.data.FriendRequest;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.menu.FriendsMenuController;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
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

/**
 * Interactive menu displaying pending friend requests with support for
 * pagination and quick actions.
 */
public class FriendRequestsMenu implements Listener {

    private static final int SIZE = 54;
    private static final int ITEMS_PER_PAGE = 28;
    private static final int[] REQUEST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
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
            plugin.getLogger().severe("Erreur lors du chargement des demandes : " + throwable.getMessage());
            allRequests = Collections.emptyList();
            Bukkit.getScheduler().runTask(plugin, () -> {
                createMenu();
                open();
            });
            return null;
        });
    }

    private void createMenu() {
        final int totalPages = Math.max(1, (int) Math.ceil((double) allRequests.size() / ITEMS_PER_PAGE));
        final String title = "§8» §eDemandes d'Amitié §8(" + allRequests.size() + "/" + allRequests.size() + ")";
        inventory = Bukkit.createInventory(null, SIZE, title);
        setupMenu();
    }

    private void setupMenu() {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        final ItemStack greenGlass = createItem(Material.GREEN_STAINED_GLASS_PANE, " ");
        final int[] greenSlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 53};
        for (int slot : greenSlots) {
            inventory.setItem(slot, greenGlass);
        }
        displayRequests();
        setupGroupActions();
        setupNavigation();
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
                        "§a✓ Parfait !",
                        "§7Votre boîte de réception est vide",
                        "",
                        "§e💡 Info:",
                        "§7Les nouvelles demandes apparaîtront ici",
                        "§7automatiquement"
                ));
                noRequests.setItemMeta(meta);
            }
            inventory.setItem(22, noRequests);
            return;
        }

        final int startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
        final int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allRequests.size());
        for (int i = 0; i < REQUEST_SLOTS.length && startIndex + i < endIndex; i++) {
            final FriendRequest request = allRequests.get(startIndex + i);
            inventory.setItem(REQUEST_SLOTS[i], createRequestItem(request));
        }
    }

    private ItemStack createRequestItem(final FriendRequest request) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta itemMeta = head.getItemMeta();
        if (itemMeta instanceof SkullMeta skullMeta) {
            try {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(java.util.UUID.fromString(request.getSenderUuid())));
            } catch (IllegalArgumentException ignored) {
                // keep default skin
            }
            String name = "§e§l" + request.getSenderName();
            name += request.isSenderOnline() ? " §a●" : " §8●";
            skullMeta.setDisplayName(name);
            final List<String> lore = new ArrayList<>();
            lore.add("§7Demande d'amitié reçue");
            lore.add("§7Date: §e" + request.getRelativeDate());
            final String message = request.getDisplayMessage();
            if ("Aucun message".equals(message)) {
                lore.add("§7Message: §8Aucun message");
            } else {
                lore.add("§7Message: §f\"" + message + "\"");
            }
            lore.add("");
            lore.add("§7Informations sur le joueur:");
            lore.add("§8▸ §7Statut: " + (request.isSenderOnline() ? "§aEn ligne" : "§cHors ligne"));
            lore.add("§8▸ §7Niveau: §a?");
            lore.add("§8▸ §7Temps de jeu: §b?");
            lore.add("§8▸ §7Amis en commun: §d?");
            if (request.isSenderOnline()) {
                lore.add("§8▸ §7Serveur actuel: §eLobby");
            }
            lore.add("");
            lore.add("§7Analyse de compatibilité:");
            lore.add("§8▸ §7Score global: §a85%");
            lore.add("§8▸ §7Recommandation: §aRecommandé");
            lore.add("");
            lore.add("§8▸ §aClique gauche §8: §2✓ Accepter");
            lore.add("§8▸ §cClique droit §8: §4✗ Refuser");
            lore.add("§8▸ §7Shift-Clic §8: §8⚫ Bloquer & Refuser");
            lore.add("§8▸ §eClique milieu §8: §6👤 Voir profil complet");
            skullMeta.setLore(lore);
            skullMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            skullMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private void setupGroupActions() {
        if (!allRequests.isEmpty()) {
            final ItemStack acceptAll = createItem(Material.EMERALD, "§a§l✓ Accepter Toutes (" + allRequests.size() + ")");
            final ItemMeta acceptMeta = acceptAll.getItemMeta();
            if (acceptMeta != null) {
                acceptMeta.setLore(Arrays.asList(
                        "§7Accepter toutes les demandes",
                        "§7d'amitié en attente",
                        "",
                        "§a▸ Demandes à accepter: §2" + allRequests.size(),
                        "",
                        "§8» §aCliquez pour accepter toutes"
                ));
                acceptAll.setItemMeta(acceptMeta);
            }
            inventory.setItem(45, acceptAll);

            final ItemStack rejectAll = createItem(Material.REDSTONE, "§c§l✗ Refuser Toutes (" + allRequests.size() + ")");
            final ItemMeta rejectMeta = rejectAll.getItemMeta();
            if (rejectMeta != null) {
                rejectMeta.setLore(Arrays.asList(
                        "§7Refuser toutes les demandes",
                        "§7d'amitié en attente",
                        "",
                        "§c▸ Demandes à refuser: §4" + allRequests.size(),
                        "§c⚠ Action irréversible",
                        "",
                        "§8» §cCliquez pour refuser toutes"
                ));
                rejectAll.setItemMeta(rejectMeta);
            }
            inventory.setItem(46, rejectAll);
        }

        final ItemStack settings = createItem(Material.REDSTONE_TORCH, "§6⚙️ Paramètres des Demandes");
        final ItemMeta settingsMeta = settings.getItemMeta();
        if (settingsMeta != null) {
            settingsMeta.setLore(Arrays.asList(
                    "§7Configurez la gestion automatique",
                    "§7des demandes d'amitié",
                    "",
                    "§6▸ Auto-accepter amis mutuels: §eNon",
                    "§6▸ Auto-refuser spam: §eOui",
                    "§6▸ Notifications: §eActivées",
                    "",
                    "§8» §6Cliquez pour configurer"
            ));
            settings.setItemMeta(settingsMeta);
        }
        inventory.setItem(47, settings);
    }

    private void setupNavigation() {
        final ItemStack backButton = createItem(Material.PLAYER_HEAD, "§e🏠 Retour Menu Principal");
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
        inventory.setItem(49, backButton);

        final ItemStack refresh = createItem(Material.CLOCK, "§b🔄 Actualiser");
        final ItemMeta refreshMeta = refresh.getItemMeta();
        if (refreshMeta != null) {
            refreshMeta.setLore(Arrays.asList(
                    "§7Actualiser la liste des demandes",
                    "",
                    "§b▸ Dernière mise à jour: §3À l'instant",
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
        if (title == null || !title.contains("§8» §eDemandes d'Amitié")) {
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
        if (slot == 45 && !allRequests.isEmpty()) {
            handleAcceptAll();
            return;
        }
        if (slot == 46 && !allRequests.isEmpty()) {
            handleRejectAll();
            return;
        }
        if (slot == 47) {
            player.sendMessage("§6⚙️ Paramètres des demandes en développement !");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
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
            }, 2L);
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
        if (event.getView().getTitle() != null && event.getView().getTitle().contains("§8» §eDemandes d'Amitié")) {
            HandlerList.unregisterAll(this);
        }
    }

    private void handleRequestClick(final FriendRequest request, final org.bukkit.event.inventory.ClickType clickType) {
        switch (clickType) {
            case LEFT -> handleAcceptRequest(request);
            case RIGHT -> handleRejectRequest(request);
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                player.sendMessage("§8🚫 Système de blocage en développement !");
                handleRejectRequest(request);
            }
            case MIDDLE -> {
                player.sendMessage("§6👤 Profil détaillé en développement !");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            default -> {
            }
        }
    }

    private void handleAcceptRequest(final FriendRequest request) {
        player.sendMessage("§a🔄 Acceptation de la demande de " + request.getSenderName() + "...");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        friendsManager.acceptFriendRequest(player, request.getSenderName()).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, this::loadRequestsAndCreateMenu));
    }

    private void handleRejectRequest(final FriendRequest request) {
        player.sendMessage("§c❌ Refus de la demande de " + request.getSenderName() + "...");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
        friendsManager.rejectFriendRequest(player, request.getSenderName()).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, this::loadRequestsAndCreateMenu));
    }

    private void handleAcceptAll() {
        if (allRequests.isEmpty()) {
            return;
        }
        player.sendMessage("§a✅ Acceptation de toutes les demandes (" + allRequests.size() + ")...");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        allRequests.forEach(request -> friendsManager.acceptFriendRequest(player, request.getSenderName()));
        Bukkit.getScheduler().runTaskLater(plugin, this::loadRequestsAndCreateMenu, 20L);
    }

    private void handleRejectAll() {
        if (allRequests.isEmpty()) {
            return;
        }
        player.sendMessage("§c❌ Refus de toutes les demandes (" + allRequests.size() + ")...");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
        allRequests.forEach(request -> friendsManager.rejectFriendRequest(player, request.getSenderName()));
        Bukkit.getScheduler().runTaskLater(plugin, this::loadRequestsAndCreateMenu, 20L);
    }
}
