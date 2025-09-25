package com.lobby.social.menus;

import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PartyInvitesMenu implements Menu, InventoryHolder {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &6Invitations de Groupe");
    private static final int SIZE = 54;
    private static final List<Integer> INVITE_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );

    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final List<PartyInviteEntry> invitations;
    private final int page;

    private final Map<Integer, PartyInviteEntry> slotMapping = new HashMap<>();
    private Inventory inventory;

    public PartyInvitesMenu(final MenuManager menuManager,
                            final AssetManager assetManager,
                            final List<PartyInviteEntry> invitations,
                            final int page) {
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.invitations = invitations == null ? List.of() : new ArrayList<>(invitations);
        this.page = Math.max(0, page);
    }

    @Override
    public void open(final Player player) {
        inventory = Bukkit.createInventory(this, SIZE, TITLE);
        slotMapping.clear();

        fillBackground();
        placeBorders();
        placeInvitations();
        placeControls();

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
            menuManager.openMenu(player, "groupe_menu");
            return;
        }
        final PartyInviteEntry entry = slotMapping.get(slot);
        if (entry == null) {
            return;
        }
        final ClickType click = event.getClick();
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            processInvite(player, entry.inviter(), false);
        } else {
            processInvite(player, entry.inviter(), true);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void processInvite(final Player player, final UUID inviter, final boolean accept) {
        execute(player, inviter, accept);
        reopen(player, page);
    }

    private void execute(final Player player, final UUID inviter, final boolean accept) {
        player.closeInventory();
        final String inviterName = resolveName(inviter);
        if (accept) {
            player.performCommand("party accept " + inviterName);
        } else {
            player.performCommand("party deny " + inviterName);
        }
    }

    private void reopen(final Player player, final int newPage) {
        SocialHeavyMenus.openPartyInvitesMenu(menuManager, player, newPage);
    }

    private void placeInvitations() {
        if (invitations.isEmpty()) {
            final ItemStack placeholder = assetManager.getHead("hdb:23528");
            final ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + "Aucune invitation");
                meta.setLore(List.of(
                        ChatColor.GRAY + "Vos invitations apparaîtront ici.",
                        ChatColor.RESET.toString(),
                        ChatColor.YELLOW + "▶ Attendez qu'un chef vous invite"
                ));
                placeholder.setItemMeta(meta);
            }
            inventory.setItem(22, placeholder);
            return;
        }
        final int startIndex = page * INVITE_SLOTS.size();
        final int endIndex = Math.min(invitations.size(), startIndex + INVITE_SLOTS.size());
        for (int index = startIndex, slotIndex = 0; index < endIndex && slotIndex < INVITE_SLOTS.size(); index++, slotIndex++) {
            final PartyInviteEntry entry = invitations.get(index);
            final int slot = INVITE_SLOTS.get(slotIndex);
            final ItemStack item = createInviteItem(entry);
            inventory.setItem(slot, item);
            slotMapping.put(slot, entry);
        }
    }

    private ItemStack createInviteItem(final PartyInviteEntry entry) {
        final ItemStack book = assetManager.getHead("hdb:23528");
        final ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Invitation de " + entry.leaderName());
            meta.setLore(List.of(
                    ChatColor.RESET.toString(),
                    ChatColor.GRAY + "Pour rejoindre leur groupe de",
                    ChatColor.WHITE + String.valueOf(entry.size()) + ChatColor.GRAY + " joueurs.",
                    ChatColor.RESET.toString(),
                    ChatColor.GREEN + "▶ Clic-gauche pour ACCEPTER",
                    ChatColor.RED + "▶ Clic-droit pour REFUSER"
            ));
            book.setItemMeta(meta);
        }
        return book;
    }

    private void placeControls() {
        final ItemStack back = decorate(assetManager.getHead("hdb:9334"),
                ChatColor.RED.toString() + ChatColor.BOLD + "Retour",
                List.of(
                        ChatColor.RESET.toString(),
                        ChatColor.GRAY + "Revenir au menu de groupe.",
                        ChatColor.RESET.toString(),
                        ChatColor.YELLOW + "▶ Cliquez pour revenir"
                ));
        inventory.setItem(50, back);

        final ItemStack indicator = new ItemStack(Material.PAPER);
        final ItemMeta indicatorMeta = indicator.getItemMeta();
        if (indicatorMeta != null) {
            indicatorMeta.setDisplayName(ChatColor.GOLD + "Page " + (page + 1) + ChatColor.GRAY + " / " + ChatColor.GOLD + getTotalPages());
            indicatorMeta.setLore(List.of(ChatColor.GRAY + "Gérez toutes vos invitations."));
            indicator.setItemMeta(indicatorMeta);
        }
        inventory.setItem(49, indicator);

        if (page > 0) {
            final ItemStack previous = decorate(new ItemStack(Material.ARROW),
                    ChatColor.YELLOW + "Page Précédente",
                    List.of(ChatColor.GRAY + "Retour à la page " + page));
            inventory.setItem(48, previous);
        }
        if (hasNextPage()) {
            final ItemStack next = decorate(new ItemStack(Material.ARROW),
                    ChatColor.YELLOW + "Page Suivante",
                    List.of(ChatColor.GRAY + "Aller à la page " + (page + 2)));
            inventory.setItem(52, next);
        }
    }

    private void fillBackground() {
        final ItemStack filler = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private void placeBorders() {
        final ItemStack primary = createGlass(Material.ORANGE_STAINED_GLASS_PANE);
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

    private boolean hasNextPage() {
        return (page + 1) * INVITE_SLOTS.size() < invitations.size();
    }

    private int getTotalPages() {
        if (invitations.isEmpty()) {
            return 1;
        }
        return (int) Math.ceil((double) invitations.size() / INVITE_SLOTS.size());
    }

    private String resolveName(final UUID uuid) {
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        final String name = offlinePlayer.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return uuid.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    public record PartyInviteEntry(UUID inviter, String leaderName, int size) {
    }
}

