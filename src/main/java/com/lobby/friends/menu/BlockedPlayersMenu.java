package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simple showcase menu for the blocked players feature. The implementation is
 * still mostly cosmetic and uses simulated data while the backend is being
 * developed. The class follows the same conventions as the other temporary
 * menus used throughout the friends system so that it can be replaced later
 * without touching the caller side.
 */
public class BlockedPlayersMenu implements Listener {

    private static final String TITLE = "§8» §cJoueurs Bloqués";
    private static final int SIZE = 54;
    private static final int UNBLOCK_ALL_SLOT = 46;
    private static final int BACK_SLOT = 49;
    private static final int[] RED_GLASS_SLOTS = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 53};
    private static final int[] PLAYER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;
    private final Inventory inventory;
    private final List<BlockedPlayerData> blockedPlayers = new ArrayList<>();

    public BlockedPlayersMenu(final LobbyPlugin plugin, final FriendsManager friendsManager, final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, SIZE, TITLE);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadBlockedPlayers();
        setupMenu();
    }

    private void loadBlockedPlayers() {
        // TODO: replace with real data from FriendsManager once available.
        blockedPlayers.add(new BlockedPlayerData("SpammerBot", "Spam de messages", System.currentTimeMillis() - 86_400_000L));
        blockedPlayers.add(new BlockedPlayerData("ToxicPlayer", "Harcèlement", System.currentTimeMillis() - 172_800_000L));
    }

    private void setupMenu() {
        inventory.clear();

        final ItemStack redGlass = createItem(Material.RED_STAINED_GLASS_PANE, " ");
        for (int slot : RED_GLASS_SLOTS) {
            inventory.setItem(slot, redGlass);
        }

        displayBlockedPlayers();
        setupActions();
    }

    private void displayBlockedPlayers() {
        if (blockedPlayers.isEmpty()) {
            final ItemStack placeholder = createItem(Material.PAPER, "§7§lAucun joueur bloqué");
            final ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList(
                        "§7Vous n'avez bloqué aucun joueur",
                        "",
                        "§a✓ Parfait !",
                        "§7Votre liste de blocage est vide",
                        "",
                        "§7Les joueurs bloqués ne peuvent pas:",
                        "§8▸ §7Vous envoyer des messages",
                        "§8▸ §7Vous envoyer des demandes d'amitié",
                        "§8▸ §7Voir votre statut en ligne"
                ));
                placeholder.setItemMeta(meta);
            }
            inventory.setItem(22, placeholder);
            return;
        }

        for (int index = 0; index < PLAYER_SLOTS.length && index < blockedPlayers.size(); index++) {
            final BlockedPlayerData data = blockedPlayers.get(index);
            inventory.setItem(PLAYER_SLOTS[index], createBlockedPlayerItem(data));
        }
    }

    private ItemStack createBlockedPlayerItem(final BlockedPlayerData data) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta baseMeta = head.getItemMeta();
        if (!(baseMeta instanceof SkullMeta meta)) {
            return head;
        }

        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(data.playerName());
        meta.setOwningPlayer(offlinePlayer);
        meta.setDisplayName("§8§l" + data.playerName() + " §c🚫");

        final List<String> lore = new ArrayList<>();
        lore.add("§7Joueur bloqué");
        lore.add("");
        lore.add("§7Informations du blocage:");
        lore.add("§8▸ §7Date: §c" + formatBlockDate(data.blockDate()));
        lore.add("§8▸ §7Raison: §e" + data.reason());
        lore.add("§8▸ §7Temps écoulé: §8" + getTimeElapsed(data.blockDate()));
        lore.add("");
        lore.add("§7Restrictions actives:");
        lore.add("§8▸ §c✗ Messages privés");
        lore.add("§8▸ §c✗ Demandes d'amitié");
        lore.add("§8▸ §c✗ Visibilité de votre statut");
        lore.add("");
        lore.add("§8▸ §aClique gauche §8: §2Débloquer");
        lore.add("§8▸ §cClique droit §8: §4Modifier la raison");
        meta.setLore(lore);

        head.setItemMeta(meta);
        return head;
    }

    private void setupActions() {
        if (!blockedPlayers.isEmpty()) {
            final ItemStack unblockAll = createItem(Material.EMERALD, "§a§l✓ Débloquer Tous");
            final ItemMeta unblockMeta = unblockAll.getItemMeta();
            if (unblockMeta != null) {
                unblockMeta.setLore(Arrays.asList(
                        "§7Débloquer tous les joueurs bloqués",
                        "",
                        "§a▸ Joueurs à débloquer: §2" + blockedPlayers.size(),
                        "",
                        "§8» §aCliquez pour débloquer tous"
                ));
                unblockAll.setItemMeta(unblockMeta);
            }
            inventory.setItem(UNBLOCK_ALL_SLOT, unblockAll);
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
        inventory.setItem(BACK_SLOT, back);
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
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);
        final int slot = event.getSlot();

        if (slot == UNBLOCK_ALL_SLOT && !blockedPlayers.isEmpty()) {
            handleUnblockAll();
            return;
        }
        if (slot == BACK_SLOT) {
            handleBack();
            return;
        }

        for (int index = 0; index < PLAYER_SLOTS.length && index < blockedPlayers.size(); index++) {
            if (PLAYER_SLOTS[index] == slot) {
                handleBlockedPlayerClick(blockedPlayers.get(index), event);
                break;
            }
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
        if (TITLE.equals(event.getView().getTitle())) {
            HandlerList.unregisterAll(this);
        }
    }

    private void handleUnblockAll() {
        player.sendMessage("§a✅ Déblocage de tous les joueurs (" + blockedPlayers.size() + ")...");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        final int total = blockedPlayers.size();
        blockedPlayers.clear();
        setupMenu();
        player.updateInventory();

        player.sendMessage("§a✓ " + total + " joueur(s) débloqué(s) avec succès !");
    }

    private void handleBlockedPlayerClick(final BlockedPlayerData data, final InventoryClickEvent event) {
        if (event.isLeftClick()) {
            player.sendMessage("§a✓ " + data.playerName() + " a été débloqué");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            blockedPlayers.remove(data);
            setupMenu();
            player.updateInventory();
            return;
        }
        if (event.isRightClick()) {
            player.closeInventory();
            player.sendMessage("§e✏️ Modification raison pour " + data.playerName());
            player.sendMessage("§7Tapez la nouvelle raison dans le chat:");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        }
    }

    private void handleBack() {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final FriendsMenuController controller = plugin.getFriendsMenuController();
            if (controller != null) {
                controller.openMainMenu(player);
            }
        }, 3L);
    }

    private String formatBlockDate(final long timestamp) {
        final long days = (System.currentTimeMillis() - timestamp) / (24L * 60L * 60L * 1000L);
        if (days <= 0) {
            return "Aujourd'hui";
        }
        if (days == 1) {
            return "Hier";
        }
        return "Il y a " + days + " jour(s)";
    }

    private String getTimeElapsed(final long timestamp) {
        final long diff = System.currentTimeMillis() - timestamp;
        final long hours = diff / (60L * 60L * 1000L);
        if (hours < 24) {
            return hours + " heure(s)";
        }
        final long days = hours / 24L;
        return days + " jour(s)";
    }

    private record BlockedPlayerData(String playerName, String reason, long blockDate) {
    }
}

