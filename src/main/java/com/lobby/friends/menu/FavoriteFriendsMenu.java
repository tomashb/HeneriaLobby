package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.data.FriendData;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Menu showcasing the player's favourite friends with rich lore and quick
 * actions. The menu mirrors the style of the other friends inventories while
 * providing additional flavour for favourite entries.
 */
public class FavoriteFriendsMenu implements Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int FAVORITE_LIMIT = 5; // TODO: expose through configuration
    private static final String TITLE_FORMAT = "§8» §eAmis Favoris (%d/%d)";

    private static final int QUICK_TELEPORT_SLOT = 46;
    private static final int GROUP_MESSAGE_SLOT = 47;
    private static final int MANAGE_SLOT = 48;
    private static final int BACK_SLOT = 49;

    private static final int[] FAVORITE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int[] GOLD_SLOTS = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 53};

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;

    private Inventory inventory;
    private String inventoryTitle;
    private List<FriendData> favoriteFriends = Collections.emptyList();

    public FavoriteFriendsMenu(final LobbyPlugin plugin,
                               final FriendsManager friendsManager,
                               final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadFavoritesAndCreateMenu();
    }

    private void loadFavoritesAndCreateMenu() {
        friendsManager.getFavorites(player).thenAccept(favorites -> {
            favoriteFriends = favorites != null ? new ArrayList<>(favorites) : Collections.emptyList();
            Bukkit.getScheduler().runTask(plugin, () -> {
                createMenu();
                open();
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Erreur chargement favoris: " + throwable.getMessage());
            favoriteFriends = Collections.emptyList();
            Bukkit.getScheduler().runTask(plugin, () -> {
                createMenu();
                open();
            });
            return null;
        });
    }

    private void createMenu() {
        inventoryTitle = String.format(TITLE_FORMAT, favoriteFriends.size(), FAVORITE_LIMIT);
        inventory = Bukkit.createInventory(null, INVENTORY_SIZE, inventoryTitle);
        setupMenu();
    }

    private void setupMenu() {
        if (inventory == null) {
            return;
        }
        inventory.clear();

        final ItemStack goldGlass = createItem(Material.YELLOW_STAINED_GLASS_PANE, " ");
        for (int slot : GOLD_SLOTS) {
            inventory.setItem(slot, goldGlass);
        }

        displayFavorites();
        setupSpecialActions();
        setupNavigation();
    }

    private void displayFavorites() {
        if (favoriteFriends.isEmpty()) {
            final ItemStack noFavorites = createItem(Material.NETHER_STAR, "§7§lAucun ami favori");
            final ItemMeta meta = noFavorites.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList(
                        "§7Vous n'avez pas encore d'amis favoris",
                        "",
                        "§e💡 Comment ajouter des favoris ?",
                        "§8▸ §7Allez dans votre liste d'amis",
                        "§8▸ §7Shift+Clic sur un ami",
                        "§8▸ §7Ou utilisez le menu d'options",
                        "",
                        "§6✨ Avantages des favoris:",
                        "§8▸ §7Accès prioritaire et rapide",
                        "§8▸ §7Notifications spéciales",
                        "§8▸ §7Téléportation privilégiée"
                ));
                noFavorites.setItemMeta(meta);
            }
            inventory.setItem(22, noFavorites);
            return;
        }

        for (int i = 0; i < FAVORITE_SLOTS.length && i < favoriteFriends.size(); i++) {
            final FriendData favorite = favoriteFriends.get(i);
            final ItemStack favoriteItem = createFavoriteItem(favorite);
            inventory.setItem(FAVORITE_SLOTS[i], favoriteItem);
        }
    }

    private ItemStack createFavoriteItem(final FriendData favorite) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = head.getItemMeta();
        if (!(meta instanceof SkullMeta skullMeta)) {
            return head;
        }

        try {
            final UUID friendUuid = UUID.fromString(favorite.getUuid());
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(friendUuid));
        } catch (IllegalArgumentException ignored) {
            // keep default skull when uuid cannot be parsed
        }

        final String name = favorite.isOnline()
                ? "§e⭐ §a§l" + favorite.getPlayerName() + " §2●§6⚡"
                : "§e⭐ §7§l" + favorite.getPlayerName() + " §8●";
        skullMeta.setDisplayName(name);

        final List<String> lore = new ArrayList<>();
        lore.add("§6✨ AMI FAVORI SPÉCIAL ✨");
        lore.add("");

        if (favorite.isOnline()) {
            lore.add("§7Statut: §aEn ligne §2⚡ Actif");
            final Player friendPlayer = favorite.getPlayer();
            if (friendPlayer != null) {
                lore.add("§7Serveur: §eLobby");
                lore.add("§7Activité: §6" + detectActivity(friendPlayer));
            }
        } else {
            lore.add("§7Statut: §cHors ligne");
            lore.add("§7Dernière connexion: §e" + favorite.getRelativeLastInteraction());
        }

        lore.add("§7Ami depuis: §b" + favorite.getFormattedFriendshipDate());
        lore.add("");
        lore.add("§6🏆 Statistiques de favori:");
        lore.add("§8▸ §7Messages échangés: §6" + favorite.getMessagesExchanged());
        lore.add("§8▸ §7Temps joué ensemble: §6" + favorite.getFormattedTimeTogether());
        lore.add("§8▸ §7Score d'affinité: §6" + generateAffinityScore(favorite) + "/10 ❤");
        lore.add("");

        if (favorite.isOnline()) {
            lore.add("§8▸ §aClique gauche §8: §6🚀 Téléportation VIP");
            lore.add("§8▸ §eClique milieu §8: §6💌 Message prioritaire");
        } else {
            lore.add("§8▸ §eClique milieu §8: §6💌 Message hors-ligne prioritaire");
        }
        lore.add("§8▸ §cClique droit §8: §6⚙️ Actions favoris spéciales");
        lore.add("§8▸ §7Shift-Clic §8: §c💔 Retirer des favoris");

        skullMeta.setLore(lore);
        skullMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        skullMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        head.setItemMeta(skullMeta);
        return head;
    }

    private String detectActivity(final Player friendPlayer) {
        if (friendPlayer.isSneaking()) {
            return "Mode discret";
        }
        if (friendPlayer.isSprinting()) {
            return "En déplacement rapide";
        }
        if (friendPlayer.getVelocity().length() > 0.1) {
            return "En mouvement";
        }
        return "Stationnaire";
    }

    private int generateAffinityScore(final FriendData favorite) {
        int score = 7;
        if (favorite.getMessagesExchanged() > 100) {
            score++;
        }
        if (favorite.getTimeTogether() > 3600) {
            score++;
        }
        if (favorite.isOnline()) {
            score++;
        }
        return Math.min(10, score);
    }

    private void setupSpecialActions() {
        final long onlineFavorites = favoriteFriends.stream().filter(FriendData::isOnline).count();

        final ItemStack quickTeleport = createItem(Material.ENDER_PEARL, "§6🚀 Téléportation Rapide");
        final ItemMeta tpMeta = quickTeleport.getItemMeta();
        if (tpMeta != null) {
            tpMeta.setLore(Arrays.asList(
                    "§7Téléportez-vous rapidement vers un favori",
                    "",
                    "§6▸ Favoris en ligne: §e" + onlineFavorites,
                    "",
                    "§8» §6Cliquez pour choisir"
            ));
            quickTeleport.setItemMeta(tpMeta);
        }
        inventory.setItem(QUICK_TELEPORT_SLOT, quickTeleport);

        final ItemStack groupMessage = createItem(Material.WRITABLE_BOOK, "§e💬 Message de Groupe");
        final ItemMeta msgMeta = groupMessage.getItemMeta();
        if (msgMeta != null) {
            msgMeta.setLore(Arrays.asList(
                    "§7Envoyez un message à tous vos favoris",
                    "",
                    "§e▸ Destinataires: §6" + favoriteFriends.size() + " favoris",
                    "§e▸ En ligne: §6" + onlineFavorites,
                    "",
                    "§8» §eCliquez pour écrire"
            ));
            groupMessage.setItemMeta(msgMeta);
        }
        inventory.setItem(GROUP_MESSAGE_SLOT, groupMessage);

        final ItemStack manageFavorites = createItem(Material.NETHER_STAR, "§6⚙️ Gérer les Favoris");
        final ItemMeta manageMeta = manageFavorites.getItemMeta();
        if (manageMeta != null) {
            manageMeta.setLore(Arrays.asList(
                    "§7Options de gestion des favoris",
                    "",
                    "§6▸ Limite actuelle: §e" + FAVORITE_LIMIT,
                    "§6▸ Favoris actuels: §e" + favoriteFriends.size(),
                    "§6▸ Places libres: §e" + Math.max(0, FAVORITE_LIMIT - favoriteFriends.size()),
                    "",
                    "§8» §6Cliquez pour gérer"
            ));
            manageFavorites.setItemMeta(manageMeta);
        }
        inventory.setItem(MANAGE_SLOT, manageFavorites);
    }

    private void setupNavigation() {
        final ItemStack back = createItem(Material.BARRIER, "§e🏠 Retour Menu Principal");
        final ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            final long onlineFavorites = favoriteFriends.stream().filter(FriendData::isOnline).count();
            backMeta.setLore(Arrays.asList(
                    "§7Revenir au menu principal des amis",
                    "",
                    "§e▸ Total favoris: §6" + favoriteFriends.size(),
                    "§e▸ En ligne: §6" + onlineFavorites,
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

    private void open() {
        if (inventory == null || player == null || !player.isOnline()) {
            return;
        }
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final String title = event.getView().getTitle();
        if (title == null || inventoryTitle == null || !title.equals(inventoryTitle)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getView().getTopInventory().equals(inventory)) {
            return;
        }

        event.setCancelled(true);

        final int slot = event.getSlot();
        if (slot == QUICK_TELEPORT_SLOT) {
            handleQuickTeleport();
            return;
        }
        if (slot == GROUP_MESSAGE_SLOT) {
            handleGroupMessage();
            return;
        }
        if (slot == MANAGE_SLOT) {
            handleManageFavorites();
            return;
        }
        if (slot == BACK_SLOT) {
            handleBack();
            return;
        }

        for (int i = 0; i < FAVORITE_SLOTS.length; i++) {
            if (FAVORITE_SLOTS[i] != slot || i >= favoriteFriends.size()) {
                continue;
            }
            handleFavoriteClick(favoriteFriends.get(i), event.getClick());
            break;
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
        if (inventoryTitle != null && inventoryTitle.equals(event.getView().getTitle())) {
            HandlerList.unregisterAll(this);
        }
    }

    private void handleQuickTeleport() {
        final List<FriendData> onlineFavorites = favoriteFriends.stream()
                .filter(FriendData::isOnline)
                .toList();

        if (onlineFavorites.isEmpty()) {
            player.sendMessage("§c🚀 Aucun ami favori en ligne pour la téléportation");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            return;
        }

        if (onlineFavorites.size() == 1) {
            final FriendData target = onlineFavorites.get(0);
            final Player targetPlayer = target.getPlayer();
            if (targetPlayer != null) {
                player.closeInventory();
                player.teleport(targetPlayer.getLocation());
                player.sendMessage("§a🚀 Téléportation VIP vers " + target.getPlayerName() + " !");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
                targetPlayer.sendMessage("§6⭐ " + player.getName() + " (ami favori) s'est téléporté vers vous !");
            }
            return;
        }

        player.sendMessage("§6🚀 Sélection de téléportation rapide:");
        for (FriendData favorite : onlineFavorites) {
            player.sendMessage("§8▸ §e" + favorite.getPlayerName() + " §7(§aEn ligne§7)");
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleGroupMessage() {
        if (favoriteFriends.isEmpty()) {
            player.sendMessage("§c💬 Aucun ami favori pour envoyer un message de groupe");
            return;
        }
        player.closeInventory();
        player.sendMessage("§e💬 Message de groupe aux favoris:");
        player.sendMessage("§7Tapez votre message dans le chat:");
        player.sendMessage("§7(ou tapez 'cancel' pour annuler)");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
    }

    private void handleManageFavorites() {
        player.sendMessage("§6⚙️ Gestion des favoris:");
        player.sendMessage("§7- Réorganisation des priorités");
        player.sendMessage("§7- Paramètres de notifications");
        player.sendMessage("§7- Limites et permissions");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleBack() {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final FriendsMenuController controller = plugin.getFriendsMenuController();
            if (controller != null) {
                controller.openMainMenu(player);
            }
        }, 2L);
    }

    private void handleFavoriteClick(final FriendData favorite, final ClickType clickType) {
        switch (clickType) {
            case LEFT -> handleTeleport(favorite);
            case MIDDLE -> handlePriorityMessage(favorite);
            case RIGHT -> handleSpecialActions(favorite);
            case SHIFT_LEFT, SHIFT_RIGHT -> removeFromFavorites(favorite);
            default -> {
            }
        }
    }

    private void handleTeleport(final FriendData favorite) {
        if (!favorite.isOnline()) {
            player.sendMessage("§c" + favorite.getPlayerName() + " n'est pas en ligne !");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            return;
        }
        final Player targetPlayer = favorite.getPlayer();
        if (targetPlayer == null) {
            player.sendMessage("§cImpossible de trouver le joueur " + favorite.getPlayerName());
            return;
        }
        player.closeInventory();
        player.teleport(targetPlayer.getLocation());
        player.sendMessage("§a🚀 Téléportation VIP vers " + favorite.getPlayerName() + " !");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
        targetPlayer.sendMessage("§6⭐ " + player.getName() + " (ami favori) s'est téléporté vers vous !");
    }

    private void handlePriorityMessage(final FriendData favorite) {
        player.closeInventory();
        player.sendMessage("§e💌 Message prioritaire à " + favorite.getPlayerName() + ":");
        player.sendMessage("§7Tapez votre message prioritaire dans le chat:");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
    }

    private void handleSpecialActions(final FriendData favorite) {
        player.sendMessage("§6⚙️ Actions spéciales pour " + favorite.getPlayerName() + ":");
        player.sendMessage("§7- Inviter à une activité spéciale");
        player.sendMessage("§7- Partager des ressources VIP");
        player.sendMessage("§7- Voir l'historique détaillé");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void removeFromFavorites(final FriendData favorite) {
        friendsManager.toggleFavorite(player, favorite.getPlayerName()).thenAccept(success -> {
            if (!success) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§7💔 " + favorite.getPlayerName() + " retiré de vos favoris");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                loadFavoritesAndCreateMenu();
            });
        });
    }
}
