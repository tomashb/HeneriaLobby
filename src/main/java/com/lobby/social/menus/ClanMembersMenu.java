package com.lobby.social.menus;

import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.social.clans.Clan;
import com.lobby.social.clans.ClanMember;
import com.lobby.social.clans.ClanRank;
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
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ClanMembersMenu implements Menu, InventoryHolder {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &9Membres du Clan");
    private static final int SIZE = 54;
    private static final List<Integer> MEMBER_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withLocale(Locale.FRENCH)
            .withZone(ZoneId.systemDefault());

    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final Clan clan;
    private final UUID viewerUuid;
    private final List<ClanMember> members;

    private final Map<Integer, UUID> slotMapping = new HashMap<>();
    private Inventory inventory;

    public ClanMembersMenu(final MenuManager menuManager,
                           final AssetManager assetManager,
                           final Clan clan,
                           final UUID viewerUuid,
                           final List<ClanMember> members) {
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.clan = clan;
        this.viewerUuid = viewerUuid;
        this.members = members == null ? List.of() : new ArrayList<>(members);
    }

    @Override
    public void open(final Player player) {
        inventory = Bukkit.createInventory(this, SIZE, TITLE);
        slotMapping.clear();

        fillBackground();
        placeBorders();
        placeMembers();
        placeControls();

        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getSlot();
        if (slot == 50) {
            menuManager.openMenu(player, "clan_menu");
            return;
        }
        final UUID target = slotMapping.get(slot);
        if (target == null) {
            return;
        }
        player.performCommand("clan manage " + target.toString());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void placeMembers() {
        if (members.isEmpty()) {
            final ItemStack placeholder = assetManager.getHead("hdb:9723");
            final ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + "Aucun membre");
                meta.setLore(List.of(
                        ChatColor.GRAY + "Invitez des joueurs pour bâtir votre clan.",
                        ChatColor.RESET.toString(),
                        ChatColor.YELLOW + "▶ Utilisez /clan invite <pseudo>"
                ));
                placeholder.setItemMeta(meta);
            }
            inventory.setItem(22, placeholder);
            return;
        }

        members.sort(Comparator
                .comparing((ClanMember member) -> getRankPriority(member.getRankName())).reversed()
                .thenComparing(member -> clan.isLeader(member.getUuid()) ? 0 : 1)
                .thenComparing(member -> Bukkit.getPlayer(member.getUuid()) == null)
                .thenComparing(member -> resolveName(member.getUuid()), String.CASE_INSENSITIVE_ORDER));

        for (int index = 0; index < members.size() && index < MEMBER_SLOTS.size(); index++) {
            final ClanMember member = members.get(index);
            final int slot = MEMBER_SLOTS.get(index);
            inventory.setItem(slot, createMemberItem(member));
            slotMapping.put(slot, member.getUuid());
        }
    }

    private ItemStack createMemberItem(final ClanMember member) {
        final OfflinePlayer offline = Bukkit.getOfflinePlayer(member.getUuid());
        final boolean online = offline.isOnline();
        final String name = resolveName(member.getUuid());
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(offline);
            meta.setDisplayName((online ? ChatColor.GREEN : ChatColor.GRAY) + name);
            final String rankName = resolveRankName(member.getRankName());
            final String status = online ? ChatColor.GREEN + "En ligne" : ChatColor.GRAY + "Hors ligne";
            final String lastSeen = offline.getLastSeen() > 0
                    ? DATE_FORMATTER.format(Instant.ofEpochMilli(offline.getLastSeen()))
                    : "Inconnu";
            final List<String> lore = new ArrayList<>();
            lore.add(ChatColor.RESET.toString());
            lore.add(ChatColor.AQUA.toString() + ChatColor.BOLD + "▪ " + ChatColor.AQUA + "Rang : " + ChatColor.WHITE + rankName);
            lore.add(ChatColor.AQUA.toString() + ChatColor.BOLD + "▪ " + ChatColor.AQUA + "Statut : " + status);
            lore.add(ChatColor.AQUA.toString() + ChatColor.BOLD + "▪ " + ChatColor.AQUA + "Dernière connexion : " + ChatColor.WHITE + lastSeen);
            lore.add(ChatColor.RESET.toString());
            lore.add(ChatColor.GRAY + "(Actions contextuelles pour les officiers)");
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private void placeControls() {
        final ItemStack back = decorate(assetManager.getHead("hdb:9334"),
                ChatColor.RED.toString() + ChatColor.BOLD + "Retour",
                List.of(
                        ChatColor.RESET.toString(),
                        ChatColor.GRAY + "Revenir au panneau du clan.",
                        ChatColor.RESET.toString(),
                        ChatColor.YELLOW + "▶ Cliquez pour revenir"
                ));
        inventory.setItem(50, back);
    }

    private void fillBackground() {
        final ItemStack filler = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private void placeBorders() {
        final ItemStack primary = createGlass(Material.BLUE_STAINED_GLASS_PANE);
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

    private ItemStack createGlass(final Material material) {
        final ItemStack pane = new ItemStack(material);
        final ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
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

    private int getRankPriority(final String rankName) {
        final ClanRank rank = clan != null ? clan.getRank(rankName) : null;
        return rank != null ? rank.getPriority() : 0;
    }

    private String resolveRankName(final String rankName) {
        final ClanRank rank = clan != null ? clan.getRank(rankName) : null;
        if (rank != null) {
            return rank.getDisplayName();
        }
        return rankName == null ? "Membre" : rankName;
    }

    private String resolveName(final UUID uuid) {
        final OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        final String name = offline.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return uuid.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}

