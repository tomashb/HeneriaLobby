package com.lobby.social.menus;

import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.social.clans.ClanSummary;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ClanListMenu implements Menu, InventoryHolder {

    public static final int CLANS_PER_PAGE = 21;
    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &9Liste des Clans");
    private static final int SIZE = 54;
    private static final List<Integer> CLAN_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );

    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final List<ClanSummary> clans;
    private final int page;
    private final int totalClans;
    private final int pageSize;

    private final Map<Integer, ClanSummary> slotMapping = new HashMap<>();
    private Inventory inventory;

    public ClanListMenu(final MenuManager menuManager,
                        final AssetManager assetManager,
                        final List<ClanSummary> clans,
                        final int page,
                        final int totalClans,
                        final int pageSize) {
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.clans = clans == null ? List.of() : new ArrayList<>(clans);
        this.page = Math.max(0, page);
        this.totalClans = Math.max(0, totalClans);
        this.pageSize = Math.max(1, pageSize);
    }

    @Override
    public void open(final Player player) {
        inventory = Bukkit.createInventory(this, SIZE, TITLE);
        slotMapping.clear();

        placeBorders();
        placeClanEntries();
        placeNavigation();

        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getSlot();
        if (slot == 48 && page > 0) {
            SocialHeavyMenus.openClanListMenu(menuManager, player, page - 1);
            return;
        }
        if (slot == 52 && hasNextPage()) {
            SocialHeavyMenus.openClanListMenu(menuManager, player, page + 1);
            return;
        }
        if (slot == 50) {
            menuManager.openMenu(player, "clan_menu");
            return;
        }
        final ClanSummary summary = slotMapping.get(slot);
        if (summary == null) {
            return;
        }
        player.closeInventory();
        final String command = "clan info " + summary.name();
        player.performCommand(command.trim());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void placeClanEntries() {
        if (clans.isEmpty()) {
            final ItemStack empty = assetManager.getHead("hdb:1455");
            final ItemMeta meta = empty.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + "Aucun clan public");
                meta.setLore(List.of(
                        ChatColor.GRAY + "Les clans ouverts apparaîtront ici.",
                        ChatColor.GRAY + "Créez le vôtre ou revenez plus tard.",
                        "",
                        ChatColor.YELLOW + "▶ Lancez-vous dans l'aventure !"
                ));
                empty.setItemMeta(meta);
            }
            inventory.setItem(22, empty);
            return;
        }
        final int startIndex = page * pageSize;
        final int endIndex = Math.min(clans.size(), startIndex + CLAN_SLOTS.size());
        for (int index = startIndex, slotIndex = 0; index < endIndex && slotIndex < CLAN_SLOTS.size(); index++, slotIndex++) {
            final ClanSummary summary = clans.get(index);
            final int slot = CLAN_SLOTS.get(slotIndex);
            final ItemStack item = createClanItem(summary);
            inventory.setItem(slot, item);
            slotMapping.put(slot, summary);
        }
    }

    private ItemStack createClanItem(final ClanSummary summary) {
        final ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        final ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "[" + safeTag(summary.tag()) + "] " + ChatColor.WHITE + safeName(summary.name()));
            meta.setLore(buildLore(summary));
            book.setItemMeta(meta);
        }
        return book;
    }

    private List<String> buildLore(final ClanSummary summary) {
        final List<String> lore = new ArrayList<>();
        final String description = sanitizeDescription(summary.description());
        lore.add(ChatColor.RESET.toString());
        lore.add(ChatColor.GRAY + description);
        lore.add(ChatColor.RESET.toString());
        lore.add(ChatColor.AQUA.toString() + ChatColor.BOLD + "▪ " + ChatColor.AQUA + "Chef : "
                + ChatColor.WHITE + resolveLeaderName(summary.leaderUuid()));
        lore.add(ChatColor.AQUA.toString() + ChatColor.BOLD + "▪ " + ChatColor.AQUA + "Niveau : "
                + ChatColor.YELLOW + summary.level());
        lore.add(ChatColor.AQUA.toString() + ChatColor.BOLD + "▪ " + ChatColor.AQUA + "Membres : "
                + ChatColor.GREEN + summary.members() + ChatColor.GRAY + " / " + ChatColor.GREEN + summary.maxMembers());
        lore.add(ChatColor.RESET.toString());
        lore.add(ChatColor.YELLOW + "▶ Cliquez pour voir le profil du clan");
        return lore;
    }

    private void placeNavigation() {
        final ItemStack back = decorate(assetManager.getHead("hdb:9334"),
                ChatColor.RED.toString() + ChatColor.BOLD + "Retour",
                List.of(ChatColor.RESET.toString(), ChatColor.GRAY + "Revenir au menu des clans"));
        inventory.setItem(50, back);

        final ItemStack pageIndicator = new ItemStack(Material.PAPER);
        final ItemMeta meta = pageIndicator.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Page " + (page + 1) + ChatColor.GRAY + " / " + ChatColor.AQUA + getTotalPages());
            meta.setLore(List.of(ChatColor.GRAY + "Explorez les clans publics du serveur."));
            pageIndicator.setItemMeta(meta);
        }
        inventory.setItem(49, pageIndicator);

        if (page > 0) {
            final ItemStack previous = decorate(new ItemStack(Material.ARROW),
                    ChatColor.YELLOW + "Page précédente",
                    List.of(ChatColor.GRAY + "Retour à la page " + page));
            inventory.setItem(48, previous);
        }
        if (hasNextPage()) {
            final ItemStack next = decorate(new ItemStack(Material.ARROW),
                    ChatColor.YELLOW + "Page suivante",
                    List.of(ChatColor.GRAY + "Aller à la page " + (page + 2)));
            inventory.setItem(52, next);
        }
    }

    private void placeBorders() {
        final ItemStack border = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        final ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            border.setItemMeta(meta);
        }
        final int[] borderSlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53};
        for (int slot : borderSlots) {
            inventory.setItem(slot, border);
        }
    }

    private boolean hasNextPage() {
        return (page + 1) * pageSize < totalClans;
    }

    private int getTotalPages() {
        if (totalClans <= 0) {
            return 1;
        }
        return (int) Math.max(1, Math.ceil((double) totalClans / pageSize));
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

    private String resolveLeaderName(final UUID leaderUuid) {
        if (leaderUuid == null) {
            return ChatColor.RED + "Inconnu";
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(leaderUuid);
        final String name = offlinePlayer.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return leaderUuid.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String sanitizeDescription(final String description) {
        if (description == null || description.isBlank()) {
            return "Aucune description";
        }
        return description.length() > 60 ? description.substring(0, 57) + "..." : description;
    }

    private String safeName(final String name) {
        return (name == null || name.isBlank()) ? "Clan" : name;
    }

    private String safeTag(final String tag) {
        return (tag == null || tag.isBlank()) ? "???" : tag;
    }
}
