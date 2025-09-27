package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Interactive interface allowing players to review and manage their blocked
 * players list. The backend integration is not yet implemented, therefore the
 * menu currently relies on deterministic mock data while keeping the
 * presentation logic production-ready.
 */
public class BlockedPlayersMenu implements Listener {

    private static final String INVENTORY_TITLE = "§8» §cJoueurs Bloqués";
    private static final int INVENTORY_SIZE = 54;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
            .withZone(ZoneId.systemDefault());

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;
    private final Inventory inventory;
    private final List<BlockedPlayerData> blockedPlayers = new ArrayList<>();
    private final int[] blockedSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private boolean unregistered;

    public BlockedPlayersMenu(final LobbyPlugin plugin,
                              final FriendsManager friendsManager,
                              final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadBlockedPlayers();
        setupMenu();
    }

    private void loadBlockedPlayers() {
        blockedPlayers.clear();
        // TODO: Récupérer depuis la base de données.
        blockedPlayers.add(new BlockedPlayerData("SpammerBot", "Spam de messages", System.currentTimeMillis() - 86_400_000L));
        blockedPlayers.add(new BlockedPlayerData("ToxicPlayer", "Harcèlement", System.currentTimeMillis() - 172_800_000L));
        blockedPlayers.add(new BlockedPlayerData("Griefer", "Destruction de parcelles", System.currentTimeMillis() - 259_200_000L));
    }

    private void setupMenu() {
        inventory.clear();
        fillDecorations();
        displayBlockedPlayers();
        setupActions();
    }

    private void fillDecorations() {
        final ItemStack redGlass = createItem(Material.RED_STAINED_GLASS_PANE, " ");
        final int[] glassSlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53};
        for (int slot : glassSlots) {
            inventory.setItem(slot, redGlass);
        }
    }

    private void displayBlockedPlayers() {
        if (blockedPlayers.isEmpty()) {
            final ItemStack noBlocked = createItem(Material.PAPER, "§7§lAucun joueur bloqué");
            final ItemMeta meta = noBlocked.getItemMeta();
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
                noBlocked.setItemMeta(meta);
            }
            inventory.setItem(22, noBlocked);
            return;
        }

        for (int i = 0; i < blockedSlots.length && i < blockedPlayers.size(); i++) {
            final BlockedPlayerData data = blockedPlayers.get(i);
            inventory.setItem(blockedSlots[i], createBlockedPlayerItem(data));
        }
    }

    private ItemStack createBlockedPlayerItem(final BlockedPlayerData blockedData) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta baseMeta = head.getItemMeta();
        if (!(baseMeta instanceof SkullMeta meta)) {
            return head;
        }

        meta.setDisplayName("§8§l" + blockedData.getPlayerName() + " §c🚫");
        final List<String> lore = new ArrayList<>();
        lore.add("§7Joueur bloqué");
        lore.add("");
        lore.add("§7Informations du blocage:");
        lore.add("§8▸ §7Date: §c" + formatBlockDate(blockedData.getBlockDate()));
        lore.add("§8▸ §7Raison: §e" + blockedData.getReason());
        lore.add("§8▸ §7Temps écoulé: §8" + getTimeElapsed(blockedData.getBlockDate()));
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
            inventory.setItem(47, unblockAll);
        }

        final ItemStack refresh = createItem(Material.CLOCK, "§b🔄 Actualiser");
        final ItemMeta refreshMeta = refresh.getItemMeta();
        if (refreshMeta != null) {
            refreshMeta.setLore(Arrays.asList(
                    "§7Actualiser la liste des joueurs bloqués",
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

    private String formatBlockDate(final long timestamp) {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    private String getTimeElapsed(final long timestamp) {
        final Duration duration = Duration.ofMillis(System.currentTimeMillis() - timestamp);
        final long days = duration.toDays();
        if (days > 0) {
            return days + " jour(s)";
        }
        final long hours = duration.toHours();
        if (hours > 0) {
            return hours + " heure(s)";
        }
        final long minutes = Math.max(1, duration.toMinutes());
        return minutes + " minute(s)";
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
        playSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!INVENTORY_TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        final int slot = event.getSlot();
        if (slot == 47 && !blockedPlayers.isEmpty()) {
            handleUnblockAll();
            return;
        }
        if (slot == 48) {
            handleRefresh();
            return;
        }
        if (slot == 49) {
            handleBack();
            return;
        }

        for (int i = 0; i < blockedSlots.length; i++) {
            if (blockedSlots[i] == slot && i < blockedPlayers.size()) {
                handleBlockedPlayerClick(blockedPlayers.get(i), event);
                break;
            }
        }
    }

    private void handleUnblockAll() {
        final int count = blockedPlayers.size();
        if (count == 0) {
            return;
        }
        player.sendMessage("§a✅ Déblocage de tous les joueurs (" + count + ")...");
        playSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f);
        blockedPlayers.clear();
        setupMenu();
        player.updateInventory();
        player.sendMessage("§a✓ " + count + " joueur(s) débloqué(s) avec succès !");
    }

    private void handleBlockedPlayerClick(final BlockedPlayerData blockedData,
                                          final InventoryClickEvent event) {
        if (event.getClick().isLeftClick()) {
            player.sendMessage("§a✓ " + blockedData.getPlayerName() + " a été débloqué");
            playSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f);
            blockedPlayers.remove(blockedData);
            setupMenu();
            player.updateInventory();
            return;
        }

        if (event.getClick().isRightClick()) {
            player.closeInventory();
            player.sendMessage("§e✏️ Modification raison pour " + blockedData.getPlayerName());
            player.sendMessage("§7Tapez la nouvelle raison dans le chat:");
            playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.5f);
        }
    }

    private void handleRefresh() {
        player.sendMessage("§b🔄 Actualisation de la liste des bloqués...");
        playSound(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f);
        loadBlockedPlayers();
        setupMenu();
        player.updateInventory();
        player.sendMessage("§aListe des joueurs bloqués mise à jour !");
    }

    private void handleBack() {
        player.closeInventory();
        playSound(Sound.UI_BUTTON_CLICK, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final var controller = plugin.getFriendsMenuController();
            if (controller != null) {
                controller.openMainMenu(player);
            }
        }, 2L);
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)) {
            return;
        }
        if (!viewer.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        if (INVENTORY_TITLE.equals(event.getView().getTitle())) {
            unregister();
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            unregister();
        }
    }

    private void unregister() {
        if (unregistered) {
            return;
        }
        HandlerList.unregisterAll(this);
        unregistered = true;
    }

    private void playSound(final Sound sound, final float pitch) {
        if (sound == null || player == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }

    private static class BlockedPlayerData {
        private final String playerName;
        private String reason;
        private final long blockDate;

        BlockedPlayerData(final String playerName, final String reason, final long blockDate) {
            this.playerName = playerName;
            this.reason = reason;
            this.blockDate = blockDate;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getReason() {
            return reason;
        }

        public long getBlockDate() {
            return blockDate;
        }

        public void setReason(final String reason) {
            this.reason = reason;
        }
    }
}
