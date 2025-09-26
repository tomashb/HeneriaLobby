package com.lobby.social.menus.friends;

import com.lobby.LobbyPlugin;
import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.social.ChatInputManager;
import com.lobby.social.friends.FriendInfo;
import com.lobby.social.friends.FriendManager;
import com.lobby.social.menus.SocialHeavyMenus;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class FriendsMenu implements Menu, InventoryHolder {

    private static final int INVENTORY_SIZE = 54;
    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &aMes Amis");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withLocale(Locale.FRENCH);

    private static final List<Integer> FRIEND_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final FriendManager friendManager;
    private final List<FriendInfo> friends;
    private final int requestsCount;
    private final String sortMode;
    private final String searchTerm;
    private final int page;

    private final Map<Integer, FriendInfo> slotMapping = new HashMap<>();
    private Inventory inventory;

    public FriendsMenu(final LobbyPlugin plugin,
                       final MenuManager menuManager,
                       final AssetManager assetManager,
                       final FriendManager friendManager,
                       final List<FriendInfo> friends,
                       final int requestsCount,
                       final String sortMode,
                       final String searchTerm,
                       final int page) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.friendManager = friendManager;
        this.requestsCount = Math.max(0, requestsCount);
        this.sortMode = sortMode == null ? "§aPriorité intelligente" : sortMode;
        this.searchTerm = searchTerm;
        this.page = Math.max(0, page);
        this.friends = new ArrayList<>(friends == null ? List.of() : friends);
        this.friends.sort(friendComparator());
    }

    @Override
    public void open(final Player player) {
        inventory = Bukkit.createInventory(this, INVENTORY_SIZE, TITLE);
        slotMapping.clear();

        fillBackground();
        placeBorders();
        placeStaticItems(player);
        placeNavigation();
        placeFriendEntries(player);

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

        if (handleStaticAction(player, slot, click)) {
            return;
        }

        final FriendInfo info = slotMapping.get(slot);
        if (info == null) {
            return;
        }

        if (click == ClickType.LEFT) {
            player.closeInventory();
            if (!friendManager.joinFriendServer(player, info)) {
                reopen(player);
            }
            return;
        }
        if (click == ClickType.RIGHT) {
            runAsync(() -> {
                friendManager.removeFriend(player, info.getName());
                reopen(player);
            });
            return;
        }
        if (click == ClickType.MIDDLE) {
            final Map<String, String> placeholders = Map.of(
                    "%target_uuid%", info.getUuid().toString(),
                    "%target_name%", info.getName()
            );
            menuManager.openMenu(player, "amis_gift_menu", placeholders);
            return;
        }
        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            runAsync(() -> {
                final boolean toggled = friendManager.toggleFavorite(player.getUniqueId(), info.getUuid());
                if (toggled) {
                    final boolean nowFavorite = friendManager.isFavorite(player.getUniqueId(), info.getUuid());
                    player.sendMessage(nowFavorite
                            ? "§6★ §aAjouté aux favoris"
                            : "§7☆ §cRetiré des favoris");
                } else {
                    player.sendMessage("§cImpossible de mettre à jour le statut favori.");
                }
                reopen(player);
            });
        }
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
    }

    private void placeStaticItems(final Player player) {
        inventory.setItem(3, createAddFriendItem());
        inventory.setItem(5, createSortItem());
        inventory.setItem(7, createRequestsItem());
        inventory.setItem(49, createSettingsItem());
        inventory.setItem(50, createReturnItem());
    }

    private void placeNavigation() {
        if (page > 0) {
            inventory.setItem(48, decorateButton(assetManager.getHead("hdb:31405"), "§ePage précédente"));
        }
        if (hasNextPage()) {
            inventory.setItem(51, decorateButton(assetManager.getHead("hdb:31406"), "§ePage suivante"));
        }
    }

    private void placeFriendEntries(final Player viewer) {
        final int startIndex = page * FRIEND_SLOTS.size();
        final int endIndex = Math.min(friends.size(), startIndex + FRIEND_SLOTS.size());
        final List<FriendInfo> pageEntries = friends.subList(startIndex, endIndex);
        if (pageEntries.isEmpty()) {
            final ItemStack placeholder = assetManager.getHead("hdb:9945");
            final ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cAucun ami trouvé");
                final List<String> lore = new ArrayList<>();
                if (searchTerm != null && !searchTerm.isBlank()) {
                    lore.add("§7Aucun résultat pour: §f" + searchTerm);
                } else {
                    lore.add("§7Ajoutez des amis pour remplir cette liste.");
                }
                lore.add("§r");
                lore.add("§eUtilisez le bouton de recherche pour filtrer.");
                meta.setLore(lore);
                placeholder.setItemMeta(meta);
            }
            inventory.setItem(22, placeholder);
            return;
        }
        for (int index = 0; index < pageEntries.size(); index++) {
            final int slot = FRIEND_SLOTS.get(index);
            final FriendInfo info = pageEntries.get(index);
            inventory.setItem(slot, createFriendItem(info));
            slotMapping.put(slot, info);
        }
    }

    private boolean hasNextPage() {
        return friends.size() > (page + 1) * FRIEND_SLOTS.size();
    }

    private boolean handleStaticAction(final Player player, final int slot, final ClickType click) {
        if (slot == 3) {
            ChatInputManager.startFriendAddFlow(player);
            return true;
        }
        if (slot == 5) {
            if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                startSearchFlow(player);
                return true;
            }
            player.closeInventory();
            player.performCommand("friends sort");
            return true;
        }
        if (slot == 7) {
            SocialHeavyMenus.openFriendRequestsMenu(menuManager, player, 0);
            return true;
        }
        if (slot == 48 && page > 0) {
            SocialHeavyMenus.openFriendsMenu(menuManager, player, page - 1, searchTerm);
            return true;
        }
        if (slot == 49) {
            menuManager.openMenu(player, "amis_settings_menu");
            return true;
        }
        if (slot == 50) {
            menuManager.openMenu(player, "profil_menu");
            return true;
        }
        if (slot == 51 && hasNextPage()) {
            SocialHeavyMenus.openFriendsMenu(menuManager, player, page + 1, searchTerm);
            return true;
        }
        return false;
    }

    private void startSearchFlow(final Player player) {
        player.closeInventory();
        player.sendMessage("§e§l» Recherche d'amis");
        player.sendMessage("§7Tapez le nom à rechercher ou §ccancel §7pour annuler.");
        ChatInputManager.startInputFlow(player, input -> {
            final String value = input == null ? "" : input.trim();
            if (value.equalsIgnoreCase("cancel")) {
                player.sendMessage("§cRecherche annulée.");
                reopen(player);
                return;
            }
            SocialHeavyMenus.openFriendsMenu(menuManager, player, 0, value);
        }, () -> SocialHeavyMenus.openFriendsMenu(menuManager, player, page, searchTerm));
    }

    private ItemStack createAddFriendItem() {
        final ItemStack item = assetManager.getHead("hdb:23533");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§lAjouter un Ami");
            meta.setLore(List.of(
                    "§r",
                    "§7Utilisez §e/amis add <pseudo>",
                    "§7pour envoyer une demande.",
                    "§r",
                    "§e▶ Cliquez pour lancer l'invitation"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSortItem() {
        final ItemStack item = new ItemStack(Material.HOPPER);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lTrier & Rechercher");
            meta.setLore(List.of(
                    "§r",
                    "§7Organisez votre liste d'amis",
                    "§7ou trouvez quelqu'un rapidement.",
                    "§r",
                    "§b▸ Tri actuel: " + sortMode,
                    "§r",
                    "§e▶ Clic-gauche pour trier",
                    "§e▶ Clic-droit pour rechercher"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRequestsItem() {
        final ItemStack item = assetManager.getHead("hdb:23528");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lDemandes Reçues");
            final List<String> lore = new ArrayList<>();
            lore.add("§r");
            lore.add("§7Gérez les demandes que vous avez reçues.");
            lore.add("§r");
            lore.add("§b▸ En attente : §6" + requestsCount);
            lore.add("§r");
            lore.add("§e▶ Cliquez pour voir");
            meta.setLore(lore);
            if (requestsCount > 0) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSettingsItem() {
        final ItemStack item = assetManager.getHead("hdb:1218");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§lConfidentialité & Social");
            meta.setLore(List.of(
                    "§r",
                    "§7Gérez vos paramètres de confidentialité",
                    "§7et vos notifications.",
                    "§r",
                    "§e▶ Cliquez pour configurer"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createReturnItem() {
        final ItemStack item = assetManager.getHead("hdb:9334");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lRetour au Profil");
            meta.setLore(List.of("§e▶ Cliquez pour revenir"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFriendItem(final FriendInfo info) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta baseMeta = head.getItemMeta();
        if (!(baseMeta instanceof SkullMeta meta)) {
            return head;
        }
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(info.getUuid()));
        final boolean favorite = info.isFavorite();
        final boolean online = info.isOnline();
        final String prefix = favorite ? "§6★ " : "";
        meta.setDisplayName(prefix + (online ? "§a" : "§7") + info.getName());
        meta.setLore(buildFriendLore(info));
        if (favorite) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        head.setItemMeta(meta);
        return head;
    }

    private List<String> buildFriendLore(final FriendInfo info) {
        final List<String> lore = new ArrayList<>();
        lore.add("§7Statut: " + (info.isOnline() ? "§aEn ligne" : "§cHors ligne"));
        final String server = info.getServer();
        if (server != null && !server.isBlank()) {
            lore.add("§7Activité: §e" + server);
        } else if (info.isOnline()) {
            lore.add("§7Activité: §eInconnue");
        }
        lore.add("§7Amis depuis: §e" + formatDate(info.getFriendsSince()));
        if (!info.isOnline()) {
            lore.add("§7Dernière connexion: §e" + formatDate(info.getLastSeen()));
        }
        final String note = info.getNote();
        if (note != null && !note.isBlank()) {
            lore.add("§r");
            lore.add("§7Note:");
            wrap(note, 30).forEach(line -> lore.add("§f" + line));
        }
        lore.add("§r");
        lore.add("§aClic gauche: §7Rejoindre");
        lore.add("§cClic droit: §7Retirer");
        lore.add("§6Shift + Clic: §7Basculer favori");
        lore.add("§dClic molette: §7Envoyer un cadeau");
        return lore;
    }

    private List<String> wrap(final String text, final int maxLength) {
        final List<String> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        String current = "";
        for (String part : text.split("\\s+")) {
            if ((current + part).length() > maxLength) {
                if (!current.isEmpty()) {
                    lines.add(current.trim());
                }
                current = part + " ";
            } else {
                current += part + " ";
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.trim());
        }
        return lines;
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

    private Comparator<FriendInfo> friendComparator() {
        final Comparator<FriendInfo> byFavorite = Comparator.comparing(FriendInfo::isFavorite).reversed();
        final Comparator<FriendInfo> byOnline = Comparator.comparing(FriendInfo::isOnline).reversed();
        final Comparator<FriendInfo> byName = Comparator.comparing(FriendInfo::getName, String.CASE_INSENSITIVE_ORDER);
        return byFavorite.thenComparing(byOnline).thenComparing(byName);
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
        Bukkit.getScheduler().runTask(plugin, () -> SocialHeavyMenus.openFriendsMenu(menuManager, player, page, searchTerm));
    }
}
