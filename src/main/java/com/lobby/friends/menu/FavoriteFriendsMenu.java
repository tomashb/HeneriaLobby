package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.data.FriendData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FavoriteFriendsMenu extends BaseFriendsMenu {

    private static final String BASE_TITLE = "§8» §eAmis Favoris";

    private Inventory inventory;
    private List<FriendData> favoriteFriends;
    private String currentTitle = BASE_TITLE;

    // Slots pour favoris
    private final int[] favoriteSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    
    public FavoriteFriendsMenu(final LobbyPlugin plugin,
                               final FriendsManager friendsManager,
                               final FriendsMenuManager menuManager,
                               final Player player) {
        super(plugin, friendsManager, menuManager, player);
        this.favoriteFriends = new ArrayList<>();
    }

    @Override
    protected void openMenu() {
        loadFavoritesAndCreateMenu();
    }

    private void loadFavoritesAndCreateMenu() {
        friendsManager.getFavorites(player).thenAccept(favorites -> {
            this.favoriteFriends = favorites != null ? favorites : new ArrayList<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                createMenu();
                final Player viewer = getPlayer();
                if (viewer != null && viewer.isOnline()) {
                    viewer.openInventory(inventory);
                    viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                }
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Erreur chargement favoris: " + throwable.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§cErreur lors du chargement des favoris");
                this.favoriteFriends = new ArrayList<>();
                createMenu();
                final Player viewer = getPlayer();
                if (viewer != null && viewer.isOnline()) {
                    viewer.openInventory(inventory);
                    viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                }
            });
            return null;
        });
    }
    
    private void createMenu() {
        int favoriteCount = favoriteFriends.size();
        int favoriteLimit = 5;
        
        currentTitle = BASE_TITLE + " (" + favoriteCount + "/" + favoriteLimit + ")";
        this.inventory = Bukkit.createInventory(null, 54, currentTitle);
        setupMenu();
    }
    
    private void setupMenu() {
        inventory.clear();

        // Vitres dorées
        ItemStack goldGlass = createItem(Material.YELLOW_STAINED_GLASS_PANE, " ");
        int[] goldSlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 46, 52, 53};
        for (int slot : goldSlots) {
            inventory.setItem(slot, goldGlass);
        }

        // Afficher favoris
        displayFavorites();

        // Actions et navigation
        setupActions();
    }
    
    private void displayFavorites() {
        if (favoriteFriends.isEmpty()) {
            ItemStack noFavorites = createItem(Material.NETHER_STAR, "§7§lAucun ami favori");
            ItemMeta meta = noFavorites.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList(
                        "§7Vous n'avez pas encore d'amis favoris",
                        "",
                        "§e💡 Comment ajouter des favoris ?",
                        "§8▸ §7Allez dans votre liste d'amis",
                        "§8▸ §7Shift+Clic sur un ami",
                        "§8▸ §7Maximum " + 5 + " favoris autorisés",
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
        
        // Afficher les favoris
        for (int i = 0; i < favoriteSlots.length && i < favoriteFriends.size(); i++) {
            FriendData favorite = favoriteFriends.get(i);
            ItemStack favoriteItem = createFavoriteItem(favorite);
            inventory.setItem(favoriteSlots[i], favoriteItem);
        }
    }
    
    private ItemStack createFavoriteItem(FriendData favorite) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }

        // Tête du joueur
        try {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(java.util.UUID.fromString(favorite.getUuid())));
        } catch (Exception e) {
            head.setType(Material.PLAYER_HEAD);
        }

        // Nom spécial pour favoris
        String name;
        if (favorite.isOnline()) {
            name = "§e⭐ §a§l" + favorite.getPlayerName() + " §2●§6⚡";
        } else {
            name = "§e⭐ §7§l" + favorite.getPlayerName() + " §8●";
        }
        meta.setDisplayName(name);

        // Description enrichie
        List<String> lore = new ArrayList<>();
        lore.add("§6✨ AMI FAVORI SPÉCIAL ✨");
        lore.add("");

        if (favorite.isOnline()) {
            lore.add("§7Statut: §aEn ligne §2⚡ Actif");
            Player friendPlayer = favorite.getPlayer();
            if (friendPlayer != null) {
                lore.add("§7Serveur: §eLobby");
            }
        } else {
            lore.add("§7Statut: §cHors ligne");
            lore.add("§7Dernière connexion: §e" + favorite.getRelativeLastInteraction());
        }

        lore.add("§7Ami depuis: §b" + favorite.getFormattedFriendshipDate());
        lore.add("");
        lore.add("§6🏆 Statistiques favoris:");
        lore.add("§8▸ §7Messages échangés: §6" + favorite.getMessagesExchanged());
        lore.add("§8▸ §7Temps ensemble: §6" + favorite.getFormattedTimeTogether());
        lore.add("");

        // Actions privilégiées
        if (favorite.isOnline()) {
            lore.add("§8▸ §aClique gauche §8: §6🚀 Téléportation VIP");
            lore.add("§8▸ §eClique milieu §8: §6💌 Message prioritaire");
        } else {
            lore.add("§8▸ §eClique milieu §8: §6💌 Message hors-ligne");
        }
        lore.add("§8▸ §cClique droit §8: §6⚙️ Actions spéciales");
        lore.add("§8▸ §7Shift-Clic §8: §c💔 Retirer des favoris");

        meta.setLore(lore);

        // Effet brillant
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        head.setItemMeta(meta);
        return head;
    }
    
    private void setupActions() {
        ItemStack refresh = createItem(Material.EMERALD, "§a⟲ Actualiser");
        ItemMeta refreshMeta = refresh.getItemMeta();
        if (refreshMeta != null) {
            refreshMeta.setLore(Arrays.asList(
                    "§7Recharge la liste des favoris",
                    "",
                    "§8» §aCliquez pour mettre à jour"
            ));
            refresh.setItemMeta(refreshMeta);
        }
        inventory.setItem(49, refresh);

        ItemStack back = createItem(Material.ARROW, "§c« Retour");
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setLore(Arrays.asList(
                    "§7Revenir au menu principal",
                    "",
                    "§8» §cCliquez pour retourner"
            ));
            back.setItemMeta(backMeta);
        }
        inventory.setItem(45, back);
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleMenuClick(final InventoryClickEvent event) {
        final String title = event.getView().getTitle();
        if (title == null || !title.contains(BASE_TITLE)) {
            return;
        }
        final Player clicker = getPlayer();
        if (clicker == null) {
            return;
        }

        final int slot = event.getSlot();
        if (slot == 45) {
            clicker.closeInventory();
            clicker.playSound(clicker.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> new FriendsMainMenu(plugin, friendsManager, menuManager, clicker).open(), 3L);
            return;
        }
        if (slot == 49) {
            clicker.playSound(clicker.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            safeRefresh();
            return;
        }

        for (int i = 0; i < favoriteSlots.length; i++) {
            if (favoriteSlots[i] == slot && i < favoriteFriends.size()) {
                handleFavoriteClick(favoriteFriends.get(i), event);
                break;
            }
        }
    }
    
    private void handleFavoriteClick(FriendData favorite, InventoryClickEvent event) {
        switch (event.getClick()) {
            case LEFT:
                // Téléportation VIP
                if (favorite.isOnline()) {
                    Player targetPlayer = favorite.getPlayer();
                    if (targetPlayer != null) {
                        player.closeInventory();
                        player.teleport(targetPlayer.getLocation());
                        player.sendMessage("§a🚀 Téléportation VIP vers " + favorite.getPlayerName() + " !");
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
                        
                        targetPlayer.sendMessage("§6⭐ " + player.getName() + " (ami favori) s'est téléporté vers vous !");
                    }
                } else {
                    player.sendMessage("§c" + favorite.getPlayerName() + " n'est pas en ligne !");
                }
                break;
                
            case MIDDLE:
                // Message prioritaire
                player.closeInventory();
                player.sendMessage("§e💌 Message prioritaire à " + favorite.getPlayerName() + ":");
                player.sendMessage("§7Tapez votre message dans le chat:");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
                break;
                
            case RIGHT:
                // Actions spéciales
                player.sendMessage("§6⚙️ Actions spéciales pour " + favorite.getPlayerName() + ":");
                player.sendMessage("§7- Voir l'historique détaillé");
                player.sendMessage("§7- Inviter à une activité");
                player.sendMessage("§7- Partager des ressources");
                player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
                break;
                
            case SHIFT_LEFT:
                // Retirer des favoris
                friendsManager.toggleFavorite(player, favorite.getPlayerName()).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§7💔 " + favorite.getPlayerName() + " retiré de vos favoris");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                        loadFavoritesAndCreateMenu();
                    });
                });
                break;
        }
    }

    @Override
    public void handleMenuClose(final InventoryCloseEvent event) {
        if (event.getView().getTitle() == null || !event.getView().getTitle().contains(BASE_TITLE)) {
            return;
        }
        inventory = null;
        super.handleMenuClose(event);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public String getTitle() {
        return currentTitle;
    }
}

