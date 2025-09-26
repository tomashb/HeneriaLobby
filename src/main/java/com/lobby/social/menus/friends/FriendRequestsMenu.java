package com.lobby.social.menus.friends;

import com.lobby.LobbyPlugin;
import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.social.friends.FriendManager;
import com.lobby.social.friends.FriendRequest;
import com.lobby.social.menus.SocialHeavyMenus;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitScheduler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class FriendRequestsMenu implements Menu, InventoryHolder {

    private static final int INVENTORY_SIZE = 54;
    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &eDemandes d'Amis");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withLocale(Locale.FRENCH);
    private static final List<Integer> REQUEST_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final FriendManager friendManager;
    private final List<FriendRequestEntry> requests;
    private final int page;

    private final Map<Integer, FriendRequestEntry> slotMapping = new HashMap<>();
    private Inventory inventory;

    public FriendRequestsMenu(final LobbyPlugin plugin,
                               final MenuManager menuManager,
                               final AssetManager assetManager,
                               final FriendManager friendManager,
                               final List<FriendRequestEntry> requests,
                               final int page) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.friendManager = friendManager;
        this.requests = new ArrayList<>(requests == null ? List.of() : requests);
        this.page = Math.max(0, page);
    }

    @Override
    public void open(final Player player) {
        inventory = Bukkit.createInventory(this, INVENTORY_SIZE, TITLE);
        slotMapping.clear();

        fillBackground();
        placeBorders();
        placeNavigation();
        placeRequests();

        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!Objects.equals(event.getClickedInventory(), inventory)) {
            return;
        }
        event.setCancelled(true);
        final int slot = event.getSlot();
        final ClickType click = event.getClick();

        if (slot == 45) {
            SocialHeavyMenus.openFriendsMenu(menuManager, player, 0, null);
            return;
        }
        if (slot == 48 && page > 0) {
            SocialHeavyMenus.openFriendRequestsMenu(menuManager, player, page - 1);
            return;
        }
        if (slot == 51 && hasNextPage()) {
            SocialHeavyMenus.openFriendRequestsMenu(menuManager, player, page + 1);
            return;
        }

        final FriendRequestEntry entry = slotMapping.get(slot);
        if (entry == null) {
            return;
        }

        if (click == ClickType.RIGHT) {
            runAsync(() -> {
                friendManager.denyFriendRequest(player, entry.senderUuid());
                reopen(player);
            });
            return;
        }
        runAsync(() -> {
            friendManager.acceptFriendRequest(player, entry.senderUuid());
            reopen(player);
        });
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void fillBackground() {
        final ItemStack filler = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private void placeBorders() {
        final ItemStack primary = createGlass(Material.LIME_STAINED_GLASS_PANE);
        final int[] primarySlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53};
        for (int slot : primarySlots) {
            inventory.setItem(slot, primary);
        }
        final ItemStack secondary = createGlass(Material.GRAY_STAINED_GLASS_PANE);
        final int[] secondarySlots = {39, 40, 41};
        for (int slot : secondarySlots) {
            inventory.setItem(slot, secondary);
        }
        inventory.setItem(45, decorateButton(assetManager.getHead("hdb:9334"), "§c§lRetour"));
    }

    private void placeNavigation() {
        if (page > 0) {
            inventory.setItem(48, decorateButton(assetManager.getHead("hdb:31405"), "§ePage précédente"));
        }
        if (hasNextPage()) {
            inventory.setItem(51, decorateButton(assetManager.getHead("hdb:31406"), "§ePage suivante"));
        }
    }

    private void placeRequests() {
        final int startIndex = page * REQUEST_SLOTS.size();
        final int endIndex = Math.min(requests.size(), startIndex + REQUEST_SLOTS.size());
        final List<FriendRequestEntry> pageEntries = requests.subList(startIndex, endIndex);
        if (pageEntries.isEmpty()) {
            final ItemStack placeholder = assetManager.getHead("hdb:1455");
            final ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cAucune demande");
                meta.setLore(List.of(
                        "§7Vous n'avez aucune demande en attente.",
                        "§r",
                        "§ePartagez votre pseudo pour en recevoir !"
                ));
                placeholder.setItemMeta(meta);
            }
            inventory.setItem(22, placeholder);
            return;
        }
        for (int index = 0; index < pageEntries.size(); index++) {
            final int slot = REQUEST_SLOTS.get(index);
            final FriendRequestEntry entry = pageEntries.get(index);
            inventory.setItem(slot, createRequestItem(entry));
            slotMapping.put(slot, entry);
        }
    }

    private boolean hasNextPage() {
        return requests.size() > (page + 1) * REQUEST_SLOTS.size();
    }

    private ItemStack createRequestItem(final FriendRequestEntry entry) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta baseMeta = head.getItemMeta();
        if (!(baseMeta instanceof SkullMeta meta)) {
            return head;
        }
        final OfflinePlayer sender = Bukkit.getOfflinePlayer(entry.senderUuid());
        meta.setOwningPlayer(sender);
        meta.setDisplayName("§eDemande de §6" + entry.senderName());
        meta.setLore(List.of(
                "§7Reçue le §f" + formatDate(entry.createdAt()),
                "§r",
                "§aClic gauche: Accepter",
                "§cClic droit: Refuser"
        ));
        head.setItemMeta(meta);
        return head;
    }

    private String formatDate(final long millis) {
        if (millis <= 0L) {
            return "Inconnue";
        }
        return DATE_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private ItemStack createGlass(final Material material) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack decorateButton(final ItemStack base, final String name) {
        final ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            base.setItemMeta(meta);
        }
        return base;
    }

    private void runAsync(final Runnable runnable) {
        if (runnable == null) {
            return;
        }
        final BukkitScheduler scheduler = Bukkit.getScheduler();
        if (plugin == null) {
            runnable.run();
            return;
        }
        scheduler.runTaskAsynchronously(plugin, runnable);
    }

    private void reopen(final Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> SocialHeavyMenus.openFriendRequestsMenu(menuManager, player, page));
    }

    public record FriendRequestEntry(UUID senderUuid, String senderName, long createdAt) {
        public static FriendRequestEntry from(final FriendRequest request) {
            final UUID senderUuid = request.getSender();
            final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(senderUuid);
            final String name = offlinePlayer != null && offlinePlayer.getName() != null
                    ? offlinePlayer.getName()
                    : senderUuid.toString();
            return new FriendRequestEntry(senderUuid, name, request.getTimestamp());
        }
    }
}
