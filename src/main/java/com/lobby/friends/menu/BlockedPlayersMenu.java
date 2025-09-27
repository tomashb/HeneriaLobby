package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.friends.listeners.FriendAddChatListener;
import com.lobby.friends.manager.BlockedPlayersManager;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BlockedPlayersMenu implements Listener {

    private static final String INVENTORY_TITLE = "§8» §cJoueurs Bloqués";
    private static final String OPTIONS_TITLE_PREFIX = "§8» §4Options pour ";
    private static final int INVENTORY_SIZE = 54;
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm", Locale.FRENCH)
                    .withZone(ZoneId.systemDefault());
    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final BlockedPlayersManager blockedPlayersManager;
    private final DatabaseManager databaseManager;
    private final Player player;
    private final Inventory inventory;
    private final int[] blockedSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final Map<Integer, UUID> slotToBlocked = new HashMap<>();
    private final Map<UUID, BlockedEntry> blockedEntries = new LinkedHashMap<>();

    private UUID optionsTarget;
    private boolean unregistered;

    public BlockedPlayersMenu(final LobbyPlugin plugin,
                              final FriendsManager friendsManager,
                              final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        this.blockedPlayersManager = plugin.getBlockedPlayersManager();
        this.databaseManager = plugin.getDatabaseManager();
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadBlockedPlayers();
        setupMenu();
    }

    private void loadBlockedPlayers() {
        blockedEntries.clear();
        slotToBlocked.clear();

        if (blockedPlayersManager == null || player == null) {
            return;
        }

        final Set<UUID> blockedPlayers = blockedPlayersManager.getBlockedPlayers(player.getUniqueId());
        if (blockedPlayers == null || blockedPlayers.isEmpty()) {
            return;
        }

        final List<BlockedEntry> entries = new ArrayList<>();
        for (final UUID blockedUUID : blockedPlayers) {
            final BlockedPlayersManager.BlockInfo info =
                    blockedPlayersManager.getBlockInfo(player.getUniqueId(), blockedUUID);
            final String reason = info != null && info.getReason() != null && !info.getReason().isBlank()
                    ? info.getReason()
                    : "Aucune raison spécifiée";
            final Timestamp blockedAt = info != null ? info.getBlockedAt() : null;
            final Timestamp updatedAt = info != null ? info.getUpdatedAt() : null;
            entries.add(new BlockedEntry(blockedUUID, resolvePlayerName(blockedUUID), reason, blockedAt, updatedAt));
        }

        entries.sort((first, second) -> Long.compare(getSortTimestamp(second), getSortTimestamp(first)));

        for (final BlockedEntry entry : entries) {
            blockedEntries.put(entry.uuid(), entry);
        }
    }

    private long getSortTimestamp(final BlockedEntry entry) {
        final Timestamp updated = entry.updatedAt();
        if (updated != null) {
            return updated.getTime();
        }
        final Timestamp blockedAt = entry.blockedAt();
        return blockedAt != null ? blockedAt.getTime() : 0L;
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
        for (final int slot : glassSlots) {
            inventory.setItem(slot, redGlass);
        }
    }

    private void displayBlockedPlayers() {
        slotToBlocked.clear();

        if (blockedEntries.isEmpty()) {
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

        int index = 0;
        for (final BlockedEntry entry : blockedEntries.values()) {
            if (index >= blockedSlots.length) {
                break;
            }
            final int slot = blockedSlots[index++];
            slotToBlocked.put(slot, entry.uuid());
            inventory.setItem(slot, createBlockedPlayerItem(entry));
        }
    }

    private ItemStack createBlockedPlayerItem(final BlockedEntry entry) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta baseMeta = head.getItemMeta();
        if (!(baseMeta instanceof SkullMeta meta)) {
            return head;
        }

        meta.setDisplayName("§c🚫 " + entry.name());

        final List<String> lore = new ArrayList<>();
        lore.add("§7Joueur bloqué");
        lore.add("");
        lore.add("§8▸ §7Raison: §f\"" + entry.reason() + "\"");
        lore.add("§8▸ §7Bloqué le: §f" + formatDate(entry.blockedAt()));
        lore.add("§8▸ §7Dernière modification: §f" + formatDate(entry.updatedAt()));
        lore.add("");
        lore.add("§7Actions rapides:");
        lore.add("§8• §eClic gauche §7→ Voir détails");
        lore.add("§8• §eClic droit §7→ Modifier raison");
        lore.add("§8• §eClic milieu §7→ Débloquer");
        lore.add("§8• §eShift+Clic droit §7→ Options avancées");
        lore.add("");
        lore.add("§8UUID:" + entry.uuid());

        meta.setLore(lore);
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.uuid()));
        head.setItemMeta(meta);
        return head;
    }

    private void setupActions() {
        if (!blockedEntries.isEmpty()) {
            final ItemStack unblockAll = createItem(Material.EMERALD, "§a§l✓ Débloquer Tous");
            final ItemMeta unblockMeta = unblockAll.getItemMeta();
            if (unblockMeta != null) {
                unblockMeta.setLore(Arrays.asList(
                        "§7Débloquer tous les joueurs bloqués",
                        "",
                        "§a▸ Joueurs à débloquer: §2" + blockedEntries.size(),
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
        if (player == null || !player.isOnline()) {
            return;
        }
        player.openInventory(inventory);
        playSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final String title = event.getView().getTitle();
        if (title == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }

        if (title.equals(INVENTORY_TITLE)) {
            handleMainMenuClick(event, clicker);
            return;
        }

        if (title.startsWith(OPTIONS_TITLE_PREFIX)) {
            handleOptionsMenuClick(event, clicker);
        }
    }

    private void handleMainMenuClick(final InventoryClickEvent event, final Player clicker) {
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        event.setResult(Event.Result.DENY);

        final int slot = event.getSlot();
        if (slot == 47 && !blockedEntries.isEmpty()) {
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

        final ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) {
            return;
        }

        final UUID blockedUUID = slotToBlocked.getOrDefault(slot, extractBlockedPlayerUUID(clickedItem));
        if (blockedUUID == null) {
            plugin.getLogger().warning("Impossible d'extraire UUID du joueur bloqué depuis l'item");
            return;
        }

        final ClickType clickType = event.getClick();
        handleBlockedPlayerInteraction(clicker, blockedUUID, clickType);
    }

    private void handleBlockedPlayerInteraction(final Player player,
                                                final UUID blockedUUID,
                                                final ClickType clickType) {
        final String blockedName = resolvePlayerName(blockedUUID);
        plugin.getLogger().info("Interaction blocage " + blockedName + " par " + player.getName()
                + " - Type: " + clickType);

        switch (clickType) {
            case LEFT -> showBlockDetails(player, blockedUUID);
            case RIGHT -> {
                player.closeInventory();
                player.sendMessage("§e⚙ Modification de la raison de blocage pour §6" + blockedName + "§e...");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    final FriendAddChatListener chatListener = plugin.getFriendAddChatListener();
                    if (chatListener != null) {
                        chatListener.activateBlockReasonMode(player, blockedUUID);
                    } else {
                        player.sendMessage("§c❌ Système de chat indisponible");
                    }
                }, 3L);
            }
            case MIDDLE -> {
                player.closeInventory();
                confirmUnblock(player, blockedUUID);
            }
            case SHIFT_RIGHT -> openBlockOptionsMenu(player, blockedUUID);
            default -> showBlockMenuHelp(player);
        }
    }

    private void handleOptionsMenuClick(final InventoryClickEvent event, final Player clicker) {
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        event.setResult(Event.Result.DENY);

        if (optionsTarget == null) {
            return;
        }

        final int slot = event.getSlot();
        final UUID blockedUUID = optionsTarget;
        final String blockedName = resolvePlayerName(blockedUUID);

        switch (slot) {
            case 11 -> {
                clicker.closeInventory();
                clicker.sendMessage("§e⚙ Modification de la raison de blocage pour §6" + blockedName + "§e...");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    final FriendAddChatListener chatListener = plugin.getFriendAddChatListener();
                    if (chatListener != null) {
                        chatListener.activateBlockReasonMode(clicker, blockedUUID);
                    } else {
                        clicker.sendMessage("§c❌ Système de chat indisponible");
                    }
                }, 3L);
            }
            case 13 -> showBlockDetails(clicker, blockedUUID);
            case 15 -> {
                clicker.closeInventory();
                unblockPlayer(clicker, blockedUUID, blockedName);
            }
            case 22 -> {
                clicker.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, this::reopenMainInventory, 2L);
            }
            default -> showBlockMenuHelp(clicker);
        }
    }

    private void showBlockDetails(final Player player, final UUID blockedUUID) {
        if (blockedPlayersManager == null) {
            player.sendMessage("§c❌ Informations de blocage indisponibles !");
            return;
        }

        final BlockedPlayersManager.BlockInfo info =
                blockedPlayersManager.getBlockInfo(player.getUniqueId(), blockedUUID);
        if (info == null) {
            player.sendMessage("§c❌ Informations de blocage introuvables !");
            return;
        }

        final String blockedName = resolvePlayerName(blockedUUID);
        player.sendMessage("§c🚫 §4=== DÉTAILS DU BLOCAGE ===");
        player.sendMessage("§8▸ §7Joueur bloqué: §c" + blockedName);
        player.sendMessage("§8▸ §7Raison actuelle: §f\"" + info.getReason() + "\"");
        player.sendMessage("§8▸ §7Bloqué le: §f" + formatDate(info.getBlockedAt()));
        player.sendMessage("§8▸ §7Dernière modification: §f" + formatDate(info.getUpdatedAt()));
        player.sendMessage("§c==============================");
        player.sendMessage("");
        player.sendMessage("§7Actions disponibles:");
        player.sendMessage("§8• §eClic droit §7→ Modifier la raison");
        player.sendMessage("§8• §eClic milieu §7→ Débloquer ce joueur");
        player.sendMessage("§8• §eShift+Clic droit §7→ Options avancées");
        player.sendMessage("");
    }

    private void confirmUnblock(final Player player, final UUID blockedUUID) {
        final String blockedName = resolvePlayerName(blockedUUID);
        player.sendMessage("§e⚠ §6Confirmation de déblocage");
        player.sendMessage("§7Joueur: §c" + blockedName);
        player.sendMessage("§7Êtes-vous sûr de vouloir débloquer ce joueur ?");
        player.sendMessage("");
        player.sendMessage("§7Tapez §a/friends unblock " + blockedName + "§7 pour confirmer");
        player.sendMessage("§7Ou §cCliquez ailleurs§7 pour annuler");
    }

    private void openBlockOptionsMenu(final Player player, final UUID blockedUUID) {
        final String blockedName = resolvePlayerName(blockedUUID);
        final Inventory optionsMenu = Bukkit.createInventory(null, 27, OPTIONS_TITLE_PREFIX + blockedName);

        final ItemStack glass = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        final ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName("§7");
            glass.setItemMeta(glassMeta);
        }
        final int[] borders = {0, 1, 2, 6, 7, 8, 9, 17, 18, 19, 25, 26};
        for (final int slot : borders) {
            optionsMenu.setItem(slot, glass);
        }

        optionsMenu.setItem(11, createOptionItem(Material.WRITABLE_BOOK, "§e📝 Modifier la raison", List.of(
                "§7Changer la raison du blocage",
                "",
                "§e▸ Raison actuelle visible dans les détails",
                "§e▸ Nouvelle raison via chat",
                "§e▸ Historique conservé",
                "",
                "§8» §eCliquez pour modifier"
        )));

        optionsMenu.setItem(13, createOptionItem(Material.BOOK, "§b📊 Voir les détails", List.of(
                "§7Afficher toutes les informations de blocage",
                "",
                "§b▸ Raison complète",
                "§b▸ Date et heure",
                "§b▸ Historique des modifications",
                "",
                "§8» §bCliquez pour voir"
        )));

        optionsMenu.setItem(15, createOptionItem(Material.EMERALD, "§a✅ Débloquer", List.of(
                "§7Débloquer " + blockedName,
                "",
                "§a▸ Le joueur pourra à nouveau vous contacter",
                "§a▸ Les restrictions seront levées",
                "§a▸ Action immédiate",
                "",
                "§8» §aCliquez pour débloquer"
        )));

        optionsMenu.setItem(22, createOptionItem(Material.ARROW, "§c« Retour aux joueurs bloqués", List.of(
                "§7Revenir à la liste principale",
                "",
                "§8» §cCliquez pour revenir"
        )));

        optionsTarget = blockedUUID;
        player.openInventory(optionsMenu);
    }

    private ItemStack createOptionItem(final Material material, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleUnblockAll() {
        if (blockedEntries.isEmpty() || blockedPlayersManager == null) {
            return;
        }

        final List<UUID> targets = new ArrayList<>(blockedEntries.keySet());
        player.sendMessage("§e⏳ Déblocage de " + targets.size() + " joueur(s) en cours...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int success = 0;
            for (final UUID target : targets) {
                if (blockedPlayersManager.unblockPlayer(player.getUniqueId(), target)) {
                    success++;
                }
            }

            final int finalSuccess = success;
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§a✅ " + finalSuccess + " joueur(s) débloqué(s) avec succès !");
                loadBlockedPlayers();
                setupMenu();
                player.updateInventory();
            });
        });
    }

    private void unblockPlayer(final Player player, final UUID blockedUUID, final String blockedName) {
        if (blockedPlayersManager == null) {
            player.sendMessage("§c❌ Système de blocage indisponible !");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final boolean success = blockedPlayersManager.unblockPlayer(player.getUniqueId(), blockedUUID);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§a✅ §2" + blockedName + " débloqué avec succès !");
                    loadBlockedPlayers();
                    setupMenu();
                    player.updateInventory();
                } else {
                    player.sendMessage("§c❌ Impossible de débloquer " + blockedName + "");
                }
            });
        });
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

        final String title = event.getView().getTitle();
        if (title != null && (title.equals(INVENTORY_TITLE) || title.startsWith(OPTIONS_TITLE_PREFIX))) {
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

    private void reopenMainInventory() {
        loadBlockedPlayers();
        setupMenu();
        open();
    }

    private void showBlockMenuHelp(final Player player) {
        player.sendMessage("§e📖 §6Aide du menu Joueurs Bloqués");
        player.sendMessage("");
        player.sendMessage("§7Types de clics disponibles:");
        player.sendMessage("§8• §eClic gauche §7→ Voir les détails du blocage");
        player.sendMessage("§8• §eClic droit §7→ Modifier la raison du blocage");
        player.sendMessage("§8• §eClic milieu §7→ Débloquer le joueur");
        player.sendMessage("§8• §eShift+Clic droit §7→ Menu d'options avancées");
        player.sendMessage("");
    }

    private void playSound(final Sound sound, final float pitch) {
        if (sound == null || player == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }

    private String resolvePlayerName(final UUID playerUUID) {
        if (playerUUID == null) {
            return "Joueur-inconnu";
        }

        final Player online = Bukkit.getPlayer(playerUUID);
        if (online != null) {
            return online.getName();
        }

        if (databaseManager != null) {
            try {
                final String dbName = databaseManager.getPlayerName(playerUUID);
                if (dbName != null && !dbName.isBlank()) {
                    return dbName;
                }
            } catch (final Exception exception) {
                plugin.getLogger().warning("Erreur récupération nom pour " + playerUUID + ": "
                        + exception.getMessage());
            }
        }

        try {
            final String offline = Bukkit.getOfflinePlayer(playerUUID).getName();
            if (offline != null && !offline.isBlank()) {
                return offline;
            }
        } catch (final Exception exception) {
            plugin.getLogger().warning("Erreur OfflinePlayer pour " + playerUUID + ": " + exception.getMessage());
        }

        return "Joueur-" + playerUUID.toString().substring(0, 8);
    }

    private String formatDate(final Timestamp timestamp) {
        if (timestamp == null) {
            return "Inconnue";
        }
        final LocalDateTime dateTime = timestamp.toLocalDateTime();
        return DISPLAY_DATE_FORMATTER.format(dateTime);
    }

    private UUID extractBlockedPlayerUUID(final ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return null;
        }

        for (final String loreLine : meta.getLore()) {
            if (loreLine.contains("UUID:")) {
                final String clean = loreLine.replaceAll("§[0-9a-fk-or]", "");
                if (clean.startsWith("UUID:")) {
                    final String rawUuid = clean.substring(5).trim();
                    try {
                        return UUID.fromString(rawUuid);
                    } catch (final IllegalArgumentException exception) {
                        plugin.getLogger().warning("UUID invalide dans lore: " + rawUuid);
                    }
                }
            }
        }
        return null;
    }

    private record BlockedEntry(UUID uuid, String name, String reason, Timestamp blockedAt, Timestamp updatedAt) {
    }
}
