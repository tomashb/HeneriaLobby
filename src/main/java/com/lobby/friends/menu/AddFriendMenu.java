package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.listeners.FriendAddChatListener;
import com.lobby.friends.manager.FriendCodeManager;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.utils.HeadManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Menu dedicated to the different options available when adding a friend. The
 * inventory layout is primarily configured through {@code friends_add_menu.yml}
 * and falls back to a sensible default when the configuration is missing.
 */
public class AddFriendMenu implements Listener {

    private static final String DEFAULT_TITLE = "§8» §a➕ Ajouter un Ami";
    private static final int INVENTORY_SIZE = 54;

    private static final int SEARCH_BY_NAME_SLOT = 19;
    private static final int FRIEND_CODE_SLOT = 20;
    private static final int RECENT_PLAYERS_SLOT = 21;
    private static final int SUGGESTED_FRIENDS_SLOT = 22;
    private static final int BACK_SLOT = 45;

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;
    private final MenuLoader menuLoader;
    private final HeadManager headManager;
    private final Inventory inventory;

    public AddFriendMenu(final LobbyPlugin plugin, final FriendsManager friendsManager, final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        this.menuLoader = plugin.getMenuLoader();
        this.headManager = plugin.getHeadManager();
        this.inventory = initialiseInventory();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        initialiseFriendCodeSection();
    }

    private Inventory initialiseInventory() {
        Inventory loaded = null;
        if (menuLoader != null) {
            loaded = menuLoader.loadMenuFromConfig("friends_add_menu.yml", player);
        }
        if (loaded != null) {
            return loaded;
        }
        return buildFallbackInventory();
    }

    private Inventory buildFallbackInventory() {
        final Inventory fallback = Bukkit.createInventory(null, INVENTORY_SIZE, DEFAULT_TITLE);

        final ItemStack glass = createItem(Material.LIME_STAINED_GLASS_PANE, "§7");
        final int[] glassSlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53};
        for (int slot : glassSlots) {
            fallback.setItem(slot, glass);
        }

        fallback.setItem(SEARCH_BY_NAME_SLOT, buildHeadItem("1420", "§e🔍 Rechercher par Nom", Arrays.asList(
                "§7Ajouter un ami par son pseudo exact",
                "",
                "§e▸ Tapez le nom complet du joueur",
                "§e▸ Sensible à la casse",
                "§e▸ 3-16 caractères requis",
                "",
                "§8» §eCliquez et tapez le nom en chat"
        ), Material.NAME_TAG));

        fallback.setItem(FRIEND_CODE_SLOT, buildHeadItem("4579", "§b🔑 Ajouter par Code d'Ami",
                createFriendCodeLore(null, "§7Chargement du code..."), Material.WRITABLE_BOOK));

        fallback.setItem(RECENT_PLAYERS_SLOT, buildHeadItem("3644", "§d⏰ Joueurs Récents", Arrays.asList(
                "§7Joueurs rencontrés récemment",
                "",
                "§d▸ Dernières 24h: §50",
                "§d▸ Cette semaine: §50",
                "",
                "§8» §dCliquez pour voir la liste"
        ), Material.CLOCK));

        fallback.setItem(SUGGESTED_FRIENDS_SLOT, buildHeadItem("2118", "§6💡 Suggestions Intelligentes", Arrays.asList(
                "§7Joueurs que vous pourriez connaître",
                "",
                "§6▸ Amis d'amis: §e0",
                "§6▸ Même serveur: §e0",
                "",
                "§8» §6Cliquez pour voir les suggestions"
        ), Material.COMPASS));

        fallback.setItem(40, buildHeadItem("160", "§f📖 Guide d'Ajout d'Amis", Arrays.asList(
                "§7Comment ajouter des amis:",
                "",
                "§8▸ §eNom: Pseudo exact du joueur",
                "§8▸ §bCode: Format XXXX-YYYY unique",
                "§8▸ §dRécents: Joueurs croisés",
                "§8▸ §6Suggestions: Algorithme intelligent",
                "",
                "§7⚠ Les demandes expirent après §c7 jours"
        ), Material.BOOK));

        final ItemStack back = createItem(Material.ARROW, "§c« Retour aux Amis", List.of(
                "§7Revenir au menu principal",
                "",
                "§8» §cCliquez pour retourner"
        ));
        fallback.setItem(BACK_SLOT, back);

        return fallback;
    }

    private void initialiseFriendCodeSection() {
        if (player == null) {
            return;
        }
        final FriendCodeManager codeManager = plugin.getFriendCodeManager();
        if (codeManager == null) {
            updateFriendCodeItem(null, "§cGestionnaire de codes indisponible.");
            return;
        }
        final String cached = codeManager.getCachedCode(player.getUniqueId());
        if (cached != null) {
            updateFriendCodeItem(cached, null);
        } else {
            updateFriendCodeItem(null, "§7Chargement du code...");
        }
        loadFriendCodeAsync(codeManager);
    }

    private void loadFriendCodeAsync(final FriendCodeManager codeManager) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String code = codeManager.getPlayerCode(player.getUniqueId());
            if (code == null) {
                code = codeManager.generateUniqueCode(player.getUniqueId());
            }
            final String resolved = code;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player == null || !player.isOnline()) {
                    return;
                }
                if (resolved != null) {
                    updateFriendCodeItem(resolved, null);
                } else {
                    updateFriendCodeItem(null, "§cImpossible de récupérer votre code.");
                }
            });
        });
    }

    private void updateFriendCodeItem(final String friendCode, final String statusLine) {
        final List<String> lore = createFriendCodeLore(friendCode, statusLine);
        final ItemStack item = buildHeadItem("4579", "§b🔑 Ajouter par Code d'Ami", lore, Material.WRITABLE_BOOK);
        inventory.setItem(FRIEND_CODE_SLOT, item);
    }

    private List<String> createFriendCodeLore(final String friendCode, final String statusLine) {
        final List<String> lore = new ArrayList<>();
        lore.add("§7Utilisez le code d'ami unique");
        lore.add("§7Format requis: XXXX-YYYY");
        lore.add("");
        lore.add("§b▸ Votre code personnel: §3" + (friendCode != null ? friendCode : "Non généré"));
        lore.add("§b▸ Code toujours unique");
        lore.add("§b▸ Partage sécurisé");
        lore.add("");
        if (statusLine != null && !statusLine.isEmpty()) {
            lore.add(statusLine);
        } else {
            lore.add("§8» §bCliquez et tapez le code");
        }
        return lore;
    }

    public void open() {
        if (player == null) {
            return;
        }
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        switch (event.getSlot()) {
            case SEARCH_BY_NAME_SLOT -> handleSearch();
            case FRIEND_CODE_SLOT -> handleFriendCode();
            case RECENT_PLAYERS_SLOT -> handleRecentPlayers();
            case SUGGESTED_FRIENDS_SLOT -> handleSuggestions();
            case BACK_SLOT -> handleBack();
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player viewer)) {
            return;
        }
        if (!viewer.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        HandlerList.unregisterAll(this);
    }

    private void handleSearch() {
        player.closeInventory();
        final FriendAddChatListener listener = plugin.getFriendAddChatListener();
        if (listener == null) {
            player.sendMessage("§cLe système d'ajout via le chat est indisponible.");
            return;
        }
        listener.enableAddMode(player, "search_player_name");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    private void handleFriendCode() {
        player.closeInventory();
        final FriendAddChatListener listener = plugin.getFriendAddChatListener();
        if (listener == null) {
            player.sendMessage("§cLe système de codes d'amis est indisponible.");
            return;
        }
        listener.enableFriendCodeMode(player);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    private void handleRecentPlayers() {
        player.sendMessage("§d⏰ Joueurs croisés récemment:");
        int shown = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (!online.getWorld().equals(player.getWorld())) {
                continue;
            }
            final double distance = online.getLocation().distance(player.getLocation());
            if (distance <= 100) {
                player.sendMessage("§8▸ §e" + online.getName() + " §7(§a" + String.format("%.1f", distance) + "m§7)");
                shown++;
            }
        }
        if (shown == 0) {
            player.sendMessage("§7Aucun joueur récent détecté autour de vous.");
        }
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
    }

    private void handleSuggestions() {
        player.sendMessage("§6💡 Suggestions intelligentes:");
        player.sendMessage("§7- Analyse des joueurs compatibles");
        player.sendMessage("§7- Basé sur vos activités communes");
        player.sendMessage("§7- Amis d'amis potentiels");
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
    }

    private void handleBack() {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
        final FriendsMenuManager menuManager = plugin.getFriendsMenuManager();
        if (menuManager == null) {
            player.sendMessage("§cLe gestionnaire de menus d'amis est indisponible.");
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                new FriendsMainMenu(plugin, friendsManager, menuManager, player).open(), 3L);
    }

    private ItemStack buildHeadItem(final String headId,
                                    final String name,
                                    final List<String> lore,
                                    final Material fallback) {
        if (headManager != null) {
            return headManager.createCustomHead(headId, name, lore);
        }
        return createItem(fallback, name, lore);
    }

    private ItemStack createItem(final Material material, final String name) {
        return createItem(material, name, List.of());
    }

    private ItemStack createItem(final Material material, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(new ArrayList<>(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}

