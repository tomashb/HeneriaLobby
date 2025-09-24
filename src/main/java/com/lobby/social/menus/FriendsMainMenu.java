package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.social.ChatInputManager;
import com.lobby.social.friends.FriendInfo;
import com.lobby.social.friends.FriendManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
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
import java.util.UUID;

/**
 * Rich friend overview menu. The inventory is generated asynchronously and
 * provides pagination, quick actions and access to social utilities.
 */
public class FriendsMainMenu implements Menu, InventoryHolder {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &aMes Amis");
    private static final int SIZE = 54;
    private static final int FRIENDS_PER_PAGE = 21;
    private static final List<Integer> FRIEND_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withLocale(Locale.FRENCH);

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final FriendManager friendManager;
    private final List<FriendInfo> friends;
    private final int requestsCount;
    private final int page;

    private final Map<Integer, FriendInfo> slotMapping = new HashMap<>();
    private Inventory inventory;

    public FriendsMainMenu(final LobbyPlugin plugin,
                           final MenuManager menuManager,
                           final AssetManager assetManager,
                           final FriendManager friendManager,
                           final List<FriendInfo> friends,
                           final int requestsCount,
                           final int page) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.friendManager = friendManager;
        this.friends = friends == null ? List.of() : friends;
        this.requestsCount = Math.max(0, requestsCount);
        this.page = Math.max(0, page);
    }

    @Override
    public void open(final Player player) {
        inventory = Bukkit.createInventory(this, SIZE, TITLE);
        slotMapping.clear();

        fillBackground();
        placeDecorations();
        placeControls(player);
        placeFriendEntries(player);

        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getSlot();
        if (slot == 3) {
            ChatInputManager.startFriendAddFlow(player);
            return;
        }
        if (slot == 5) {
            player.closeInventory();
            player.performCommand("friends sort");
            return;
        }
        if (slot == 7) {
            player.closeInventory();
            FriendsMenus.openFriendRequestsMenu(player);
            return;
        }
        if (slot == 48 && page > 0) {
            SocialHeavyMenus.openFriendsMenu(menuManager, player, page - 1);
            return;
        }
        if (slot == 49) {
            if (!menuManager.openMenu(player, "friend_settings_menu")) {
                player.sendMessage("§cParamètres d'amis indisponibles pour le moment.");
            }
            return;
        }
        if (slot == 50) {
            menuManager.openMenu(player, "profil_menu");
            return;
        }
        if (slot == 52 && hasNextPage()) {
            SocialHeavyMenus.openFriendsMenu(menuManager, player, page + 1);
            return;
        }

        final FriendInfo friendInfo = slotMapping.get(slot);
        if (friendInfo == null) {
            return;
        }

        final ClickType click = event.getClick();
        if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            player.closeInventory();
            if (!friendManager.joinFriendServer(player, friendInfo)) {
                SocialHeavyMenus.openFriendsMenu(menuManager, player, page);
            }
            return;
        }
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            runAsync(() -> {
                friendManager.removeFriend(player, friendInfo.getName());
                reopen(player);
            });
            return;
        }
        if (click == ClickType.MIDDLE) {
            runAsync(() -> {
                final boolean favorite = friendManager.toggleFavorite(player.getUniqueId(), friendInfo.getUuid());
                player.sendMessage(favorite
                        ? "§6★ §aAjouté aux favoris"
                        : "§7☆ §cRetiré des favoris");
                reopen(player);
            });
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void reopen(final Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> SocialHeavyMenus.openFriendsMenu(menuManager, player, page));
    }

    private void runAsync(final Runnable runnable) {
        final BukkitScheduler scheduler = Bukkit.getScheduler();
        if (plugin == null) {
            runnable.run();
            return;
        }
        scheduler.runTaskAsynchronously(plugin, runnable);
    }

    private void fillBackground() {
        final ItemStack filler = createGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
    }

    private void placeDecorations() {
        final ItemStack lime = createGlass(Material.LIME_STAINED_GLASS_PANE, " ");
        final int[] limeSlots = {0, 1, 2, 6, 8, 9, 17, 18, 26, 27, 35, 45, 46, 52, 53};
        for (int slot : limeSlots) {
            inventory.setItem(slot, lime);
        }
        final ItemStack gray = createGlass(Material.GRAY_STAINED_GLASS_PANE, " ");
        final int[] graySlots = {36, 37, 38, 44};
        for (int slot : graySlots) {
            inventory.setItem(slot, gray);
        }
    }

    private void placeControls(final Player player) {
        inventory.setItem(3, createAddFriendItem());
        inventory.setItem(5, createSortItem());
        inventory.setItem(7, createRequestsItem());

        if (page > 0) {
            inventory.setItem(48, decorateButton(assetManager.getHead("hdb:31405"), "§ePage précédente"));
        }
        inventory.setItem(49, createSettingsItem());
        inventory.setItem(50, decorateButton(assetManager.getHead("hdb:9334"), "§cRetour au Profil"));
        if (hasNextPage()) {
            inventory.setItem(52, decorateButton(assetManager.getHead("hdb:31406"), "§ePage suivante"));
        }

        inventory.setItem(45, createGlass(Material.LIME_STAINED_GLASS_PANE, " "));
        inventory.setItem(51, createGlass(Material.BLACK_STAINED_GLASS_PANE, " "));
    }

    private void placeFriendEntries(final Player viewer) {
        if (friends.isEmpty()) {
            final ItemStack placeholder = assetManager.getHead("hdb:9945");
            final ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cAucun ami trouvé");
                meta.setLore(List.of(
                        "§7Ajoutez des amis pour jouer ensemble !",
                        "§r",
                        "§aCliquez sur 'Ajouter un Ami' pour commencer."
                ));
                placeholder.setItemMeta(meta);
            }
            inventory.setItem(22, placeholder);
            return;
        }

        final int startIndex = page * FRIENDS_PER_PAGE;
        final int endIndex = Math.min(friends.size(), startIndex + FRIENDS_PER_PAGE);
        final List<FriendInfo> pageEntries = friends.subList(startIndex, endIndex);

        for (int i = 0; i < pageEntries.size(); i++) {
            final int slot = FRIEND_SLOTS.get(i);
            final FriendInfo info = pageEntries.get(i);
            final ItemStack item = createFriendItem(info);
            inventory.setItem(slot, item);
            slotMapping.put(slot, info);
        }
    }

    private boolean hasNextPage() {
        return friends.size() > (page + 1) * FRIENDS_PER_PAGE;
    }

    private ItemStack createAddFriendItem() {
        final ItemStack item = assetManager.getHead("hdb:23533");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§lAjouter un Ami");
            meta.setLore(List.of(
                    "§7Utilisez §e/amis add <pseudo>",
                    "§7ou cliquez pour une saisie rapide."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSortItem() {
        final ItemStack item = new ItemStack(Material.HOPPER);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lTrier les Amis");
            meta.setLore(List.of("§7Trier par : §aStatut §7/ §bNom", "§7Utilise la commande &e/friends sort"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRequestsItem() {
        final ItemStack item = assetManager.getHead("hdb:23528");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lDemandes Reçues (§6" + requestsCount + "§e)");
            meta.setLore(List.of(
                    "§7Cliquez pour gérer vos demandes.",
                    "§r",
                    "§aEn attente: §f" + requestsCount
            ));
            if (requestsCount > 0) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSettingsItem() {
        final ItemStack item = new ItemStack(Material.REPEATER);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§lParamètres");
            meta.setLore(List.of(
                    "§7Gérez vos préférences d'amis",
                    "§7et vos notifications."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFriendItem(final FriendInfo info) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(info.getUuid()));
            final boolean online = info.isOnline();
            final boolean favorite = info.isFavorite();
            final String prefix = favorite ? "§6★ " : "";
            meta.setDisplayName(prefix + (online ? "§a" : "§7") + info.getName());
            meta.setLore(buildFriendLore(info));
            head.setItemMeta(meta);
        }
        return head;
    }

    private List<String> buildFriendLore(final FriendInfo info) {
        final List<String> lore = new ArrayList<>();
        lore.add("§7Statut: " + (info.isOnline() ? "§aEn ligne" : "§cHors ligne"));
        lore.add("§7Serveur: " + resolveServer(info));
        lore.add("§7Amis depuis: §e" + formatDate(info.getFriendsSince()));
        if (!info.isOnline()) {
            lore.add("§7Dernière connexion: §e" + formatDate(info.getLastSeen()));
        }
        lore.add("§r");
        lore.add("§aClic gauche: §7Rejoindre");
        lore.add("§cClic droit: §7Retirer de la liste");
        lore.add("§bClic molette: §7Basculer favori");
        return lore;
    }

    private String resolveServer(final FriendInfo info) {
        final String server = info.getServer();
        if (server == null || server.isBlank()) {
            return info.isOnline() ? "§eInconnu" : "§8Aucun";
        }
        return ChatColor.YELLOW + server;
    }

    private String formatDate(final long millis) {
        if (millis <= 0L) {
            return "N/A";
        }
        return DATE_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private ItemStack createGlass(final Material material, final String name) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
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
}
