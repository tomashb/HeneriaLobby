package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.data.FriendRequest;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
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

/**
 * Ultra-protected friend requests menu providing immediate feedback while
 * keeping the menu contents guarded against any interaction exploits.
 */
public class FriendRequestsMenu extends BaseFriendsMenu {

    private static final String TITLE_PREFIX = "§8» §6Demandes d'Amitié";
    private static final int INVENTORY_SIZE = 54;
    private static final int[] REQUEST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int[] GLASS_SLOTS = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53};

    private Inventory inventory;
    private String currentTitle = TITLE_PREFIX;
    private List<FriendRequest> allRequests = new ArrayList<>();

    public FriendRequestsMenu(final LobbyPlugin plugin,
                              final FriendsManager friendsManager,
                              final FriendsMenuManager menuManager,
                              final Player player) {
        super(plugin, friendsManager, menuManager, player);
    }

    @Override
    protected void openMenu() {
        loadRequestsAndCreateMenu();
    }

    private void loadRequestsAndCreateMenu() {
        friendsManager.getPendingRequests(player).thenAccept(requests -> {
            allRequests = requests != null ? new ArrayList<>(requests) : new ArrayList<>();
            Bukkit.getScheduler().runTask(plugin, this::createMenu);
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Erreur chargement demandes: " + throwable.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§cErreur lors du chargement des demandes");
                allRequests = new ArrayList<>();
                createMenu();
            });
            return null;
        });
    }

    private void createMenu() {
        currentTitle = TITLE_PREFIX + " (" + allRequests.size() + ")";
        inventory = Bukkit.createInventory(null, INVENTORY_SIZE, currentTitle);
        setupMenu();
        final Player viewer = getPlayer();
        if (viewer != null && viewer.isOnline()) {
            viewer.openInventory(inventory);
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        }
    }

    private void setupMenu() {
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, null);
        }

        final ItemStack glass = createItem(Material.YELLOW_STAINED_GLASS_PANE, " ");
        for (int slot : GLASS_SLOTS) {
            inventory.setItem(slot, glass);
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
                        "§7Vous n'avez aucune demande d'amitié en attente",
                        "",
                        "§a✓ Votre boîte de réception est vide !",
                        "",
                        "§7Les nouvelles demandes apparaîtront ici",
                        "§7quand vous en recevrez"
                ));
                noRequests.setItemMeta(meta);
            }
            inventory.setItem(22, noRequests);
            return;
        }

        for (int i = 0; i < REQUEST_SLOTS.length && i < allRequests.size(); i++) {
            final FriendRequest request = allRequests.get(i);
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
            // Fallback to default skin
        }

        final String senderName = request.getSenderName();
        final String message = request.getDisplayMessage();

        meta.setDisplayName("§6§l📨 " + senderName);
        final List<String> lore = new ArrayList<>();
        lore.add("§7Demande d'amitié reçue");
        lore.add("");
        lore.add("§7Expéditeur: §e" + senderName);
        lore.add("§7Date: §b" + request.getRelativeDate());
        lore.add("");
        lore.add("§7Message personnel:");
        lore.add("§f\"" + message + "\"");
        lore.add("");
        lore.add("§8▸ §aClique gauche §8: §2✓ Accepter");
        lore.add("§8▸ §cClique droit §8: §4✗ Refuser");

        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private void setupActions() {
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
        inventory.setItem(48, refresh);

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
    public void handleMenuClick(final InventoryClickEvent event) {
        final String title = event.getView().getTitle();
        if (title == null || !title.contains(TITLE_PREFIX)) {
            return;
        }
        final Player clicker = getPlayer();
        if (clicker == null) {
            return;
        }

        final int slot = event.getSlot();
        if (slot == 48) {
            clicker.playSound(clicker.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            safeRefresh();
            return;
        }
        if (slot == 49) {
            clicker.closeInventory();
            clicker.playSound(clicker.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> new FriendsMainMenu(plugin, friendsManager, menuManager, clicker).open(), 3L);
            return;
        }

        for (int i = 0; i < REQUEST_SLOTS.length; i++) {
            if (REQUEST_SLOTS[i] != slot) {
                continue;
            }
            if (i >= allRequests.size()) {
                return;
            }
            handleRequestClick(allRequests.get(i), event);
            break;
        }
    }

    private void handleRequestClick(final FriendRequest request, final InventoryClickEvent event) {
        switch (event.getClick()) {
            case LEFT -> acceptRequest(request);
            case RIGHT -> rejectRequest(request);
            default -> {
            }
        }
    }

    private void acceptRequest(final FriendRequest request) {
        friendsManager.acceptFriendRequest(player, request.getSenderName()).thenAccept(success ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.sendMessage("§a✓ Demande de " + request.getSenderName() + " acceptée !");
                        allRequests.remove(request);
                        setupMenu();
                        clickerUpdate();
                    } else {
                        player.sendMessage("§cErreur lors de l'acceptation");
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                }));
    }

    private void rejectRequest(final FriendRequest request) {
        friendsManager.rejectFriendRequest(player, request.getSenderName()).thenAccept(success ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.sendMessage("§c✗ Demande de " + request.getSenderName() + " refusée");
                        allRequests.remove(request);
                        setupMenu();
                        clickerUpdate();
                    } else {
                        player.sendMessage("§cErreur lors du refus de la demande");
                    }
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
                }));
    }

    private void clickerUpdate() {
        final Player viewer = getPlayer();
        if (viewer != null) {
            viewer.updateInventory();
        }
    }

    @Override
    public void handleMenuClose(final InventoryCloseEvent event) {
        if (event.getView().getTitle() == null || !event.getView().getTitle().contains(TITLE_PREFIX)) {
            return;
        }
        inventory = null;
        super.handleMenuClose(event);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public String getTitle() {
        return currentTitle;
    }
}
