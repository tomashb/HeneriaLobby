package com.lobby.social.menus;

import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.social.friends.FriendManager;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class FriendRequestsMenu implements Menu, InventoryHolder {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &eDemandes d'Amis");
    private static final int SIZE = 54;
    private static final List<Integer> REQUEST_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );

    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final FriendManager friendManager;
    private final List<FriendRequestEntry> requests;
    private final int page;

    private final Map<Integer, FriendRequestEntry> slotMapping = new HashMap<>();
    private Inventory inventory;

    public FriendRequestsMenu(final MenuManager menuManager,
                              final AssetManager assetManager,
                              final FriendManager friendManager,
                              final List<FriendRequestEntry> requests,
                              final int page) {
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.friendManager = friendManager;
        this.requests = requests == null ? List.of() : new ArrayList<>(requests);
        this.page = Math.max(0, page);
    }

    @Override
    public void open(final Player player) {
        inventory = Bukkit.createInventory(this, SIZE, TITLE);
        slotMapping.clear();

        placeBorders();
        placeRequests(player);
        placeNavigationControls();

        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getSlot();
        if (slot == 48 && page > 0) {
            reopen(player, page - 1);
            return;
        }
        if (slot == 52 && hasNextPage()) {
            reopen(player, page + 1);
            return;
        }
        if (slot == 50) {
            menuManager.openMenu(player, "amis_menu");
            return;
        }
        final FriendRequestEntry entry = slotMapping.get(slot);
        if (entry == null) {
            return;
        }
        final ClickType click = event.getClick();
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            processRequest(player, entry.senderUuid(), false);
        } else {
            processRequest(player, entry.senderUuid(), true);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void processRequest(final Player player, final UUID sender, final boolean accept) {
        player.closeInventory();
        if (accept) {
            friendManager.acceptFriendRequest(player, sender);
        } else {
            friendManager.denyFriendRequest(player, sender);
        }
        reopen(player, page);
    }

    private void reopen(final Player player, final int newPage) {
        SocialHeavyMenus.openFriendRequestsMenu(menuManager, player, newPage);
    }

    private void placeRequests(final Player player) {
        if (requests.isEmpty()) {
            final ItemStack placeholder = assetManager.getHead("hdb:1455");
            final ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + "Aucune demande d'ami");
                meta.setLore(List.of(
                        ChatColor.GRAY + "Vos demandes apparaîtront ici.",
                        ChatColor.GRAY + "Invitez des joueurs pour remplir la liste.",
                        "",
                        ChatColor.YELLOW + "▶ Invitez vos futurs alliés"
                ));
                placeholder.setItemMeta(meta);
            }
            inventory.setItem(22, placeholder);
            return;
        }
        final int startIndex = page * REQUEST_SLOTS.size();
        final int endIndex = Math.min(requests.size(), startIndex + REQUEST_SLOTS.size());
        for (int index = startIndex, slotIndex = 0; index < endIndex && slotIndex < REQUEST_SLOTS.size(); index++, slotIndex++) {
            final FriendRequestEntry entry = requests.get(index);
            final int slot = REQUEST_SLOTS.get(slotIndex);
            final ItemStack item = createRequestItem(entry);
            inventory.setItem(slot, item);
            slotMapping.put(slot, entry);
        }
    }

    private ItemStack createRequestItem(final FriendRequestEntry entry) {
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.senderUuid());
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName(ChatColor.YELLOW + entry.senderName());
            final List<String> lore = new ArrayList<>();
            lore.add(ChatColor.RESET.toString());
            lore.add(ChatColor.GRAY + "Demande reçue il y a " + ChatColor.WHITE + formatElapsed(entry.createdAt()) + ChatColor.GRAY + ".");
            lore.add(ChatColor.RESET.toString());
            lore.add(ChatColor.GREEN + "▶ Clic-gauche pour ACCEPTER");
            lore.add(ChatColor.RED + "▶ Clic-droit pour REFUSER");
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private void placeNavigationControls() {
        final ItemStack back = decorate(assetManager.getHead("hdb:9334"),
                ChatColor.RED.toString() + ChatColor.BOLD + "Retour",
                List.of(ChatColor.RESET.toString(), ChatColor.YELLOW + "▶ Revenir au carnet d'amis"));
        inventory.setItem(50, back);

        final ItemStack pageIndicator = new ItemStack(Material.PAPER);
        final ItemMeta pageMeta = pageIndicator.getItemMeta();
        if (pageMeta != null) {
            pageMeta.setDisplayName(ChatColor.AQUA + "Page " + (page + 1) + ChatColor.GRAY + " / " + ChatColor.AQUA + getTotalPages());
            pageMeta.setLore(List.of(ChatColor.GRAY + "Parcourez vos demandes en attente."));
            pageIndicator.setItemMeta(pageMeta);
        }
        inventory.setItem(49, pageIndicator);

        if (page > 0) {
            final ItemStack previous = decorate(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Page Précédente",
                    List.of(ChatColor.GRAY + "Retour à la page " + page));
            inventory.setItem(48, previous);
        }
        if (hasNextPage()) {
            final ItemStack next = decorate(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Page Suivante",
                    List.of(ChatColor.GRAY + "Aller à la page " + (page + 2)));
            inventory.setItem(52, next);
        }
    }

    private void placeBorders() {
        final ItemStack primary = createGlass(Material.LIME_STAINED_GLASS_PANE);
        final int[] primarySlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53};
        for (int slot : primarySlots) {
            inventory.setItem(slot, primary);
        }
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

    private ItemStack decorate(final ItemStack item, final String name, final List<String> lore) {
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean hasNextPage() {
        return (page + 1) * REQUEST_SLOTS.size() < requests.size();
    }

    private int getTotalPages() {
        if (requests.isEmpty()) {
            return 1;
        }
        return (int) Math.ceil((double) requests.size() / REQUEST_SLOTS.size());
    }

    private String formatElapsed(final long timestamp) {
        if (timestamp <= 0L) {
            return "quelques secondes";
        }
        final Duration duration = Duration.between(Instant.ofEpochMilli(timestamp), Instant.now()).abs();
        final long minutes = duration.toMinutes();
        if (minutes < 1) {
            final long seconds = duration.getSeconds();
            return seconds <= 1 ? "une seconde" : seconds + " secondes";
        }
        if (minutes < 60) {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        }
        final long hours = duration.toHours();
        if (hours < 24) {
            return hours + " heure" + (hours > 1 ? "s" : "");
        }
        final long days = duration.toDays();
        return days + " jour" + (days > 1 ? "s" : "");
    }

    public record FriendRequestEntry(UUID senderUuid, String senderName, long createdAt) {
        public FriendRequestEntry {
            if (senderName == null || senderName.isBlank()) {
                senderName = senderUuid.toString().substring(0, 8).toUpperCase(Locale.ROOT);
            }
        }
    }
}

