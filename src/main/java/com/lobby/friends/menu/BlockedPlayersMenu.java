package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BlockedPlayersMenu implements Listener {
    
    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;
    private final Inventory inventory;
    private List<BlockedPlayerData> blockedPlayers;
    
    // Slots pour joueurs bloqués
    private final int[] blockedSlots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    
    public BlockedPlayersMenu(LobbyPlugin plugin, FriendsManager friendsManager, Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 54, "§8» §cJoueurs Bloqués");
        this.blockedPlayers = new ArrayList<>();
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadBlockedPlayers();
        setupMenu();
    }
    
    private void loadBlockedPlayers() {
        // TODO: Charger depuis BDD - Pour l'instant simuler
        blockedPlayers.add(new BlockedPlayerData("SpammerBot", "Spam de messages", System.currentTimeMillis() - 86400000));
        blockedPlayers.add(new BlockedPlayerData("ToxicPlayer", "Harcèlement", System.currentTimeMillis() - 172800000));
    }
    
    private void setupMenu() {
        inventory.clear();
        
        // Vitres rouges
        ItemStack redGlass = createItem(Material.RED_STAINED_GLASS_PANE, " ");
        int[] redSlots = {0,1,2,6,7,8,9,17,36,44,45,53};
        for (int slot : redSlots) {
            inventory.setItem(slot, redGlass);
        }
        
        // Afficher joueurs bloqués
        displayBlockedPlayers();
        
        // Actions
        setupActions();
    }
    
    private void displayBlockedPlayers() {
        if (blockedPlayers.isEmpty()) {
            ItemStack noBlocked = createItem(Material.PAPER, "§7§lAucun joueur bloqué");
            ItemMeta meta = noBlocked.getItemMeta();
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
            inventory.setItem(22, noBlocked);
            return;
        }
        
        // Afficher les joueurs bloqués
        for (int i = 0; i < blockedSlots.length && i < blockedPlayers.size(); i++) {
            BlockedPlayerData blockedData = blockedPlayers.get(i);
            ItemStack blockedItem = createBlockedPlayerItem(blockedData);
            inventory.setItem(blockedSlots[i], blockedItem);
        }
    }
    
    private ItemStack createBlockedPlayerItem(BlockedPlayerData blockedData) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        meta.setDisplayName("§8§l" + blockedData.getPlayerName() + " §c🚫");
        
        List<String> lore = new ArrayList<>();
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
            // Débloquer tous
            ItemStack unblockAll = createItem(Material.EMERALD, "§a§l✓ Débloquer Tous");
            ItemMeta unblockMeta = unblockAll.getItemMeta();
            unblockMeta.setLore(Arrays.asList(
                "§7Débloquer tous les joueurs bloqués",
                "",
                "§a▸ Joueurs à débloquer: §2" + blockedPlayers.size(),
                "",
                "§8» §aCliquez pour débloquer tous"
            ));
            unblockAll.setItemMeta(unblockMeta);
            inventory.setItem(46, unblockAll);
        }
        
        // Retour
        ItemStack back = createItem(Material.BARRIER, "§e🏠 Retour Menu Principal");
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setLore(Arrays.asList(
            "§7Revenir au menu principal des amis",
            "",
            "§8» §eCliquez pour retourner"
        ));
        back.setItemMeta(backMeta);
        inventory.setItem(49, back);
    }
    
    private String formatBlockDate(long timestamp) {
        long days = (System.currentTimeMillis() - timestamp) / (24 * 60 * 60 * 1000);
        if (days == 0) return "Aujourd'hui";
        if (days == 1) return "Hier";
        return "Il y a " + days + " jour(s)";
    }
    
    private String getTimeElapsed(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long hours = diff / (60 * 60 * 1000);
        if (hours < 24) return hours + " heure(s)";
        long days = hours / 24;
        return days + " jour(s)";
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
    
    public void open() {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§8» §cJoueurs Bloqués")) {
            return;
        }
        
        // CRITICAL: Protection IMMÉDIATE - PREMIÈRE LIGNE ABSOLUE
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player clicker = (Player) event.getWhoClicked();
        
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;
        
        int slot = event.getSlot();
        
        if (slot == 46 && !blockedPlayers.isEmpty()) {
            // Débloquer tous
            handleUnblockAll();
        } else if (slot == 49) {
            // Retour
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new FriendsMainMenu(plugin, friendsManager).open(player);
            }, 3L);
        } else {
            // Vérifier si c'est un slot de joueur bloqué
            for (int i = 0; i < blockedSlots.length; i++) {
                if (blockedSlots[i] == slot && i < blockedPlayers.size()) {
                    handleBlockedPlayerClick(blockedPlayers.get(i), event);
                    break;
                }
            }
        }
    }
    
    private void handleUnblockAll() {
        player.sendMessage("§a✅ Déblocage de tous les joueurs (" + blockedPlayers.size() + ")...");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        
        int count = blockedPlayers.size();
        blockedPlayers.clear();
        setupMenu();
        
        player.sendMessage("§a✓ " + count + " joueur(s) débloqué(s) avec succès !");
    }
    
    private void handleBlockedPlayerClick(BlockedPlayerData blockedData, InventoryClickEvent event) {
        if (event.getClick().isLeftClick()) {
            // Débloquer
            player.sendMessage("§a✓ " + blockedData.getPlayerName() + " a été débloqué");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            
            blockedPlayers.remove(blockedData);
            setupMenu();
        } else if (event.getClick().isRightClick()) {
            // Modifier raison
            player.closeInventory();
            player.sendMessage("§e✏️ Modification raison pour " + blockedData.getPlayerName());
            player.sendMessage("§7Tapez la nouvelle raison dans le chat:");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        }
    }
    
    // Classe interne pour les données
    private static class BlockedPlayerData {
        private final String playerName;
        private String reason;
        private final long blockDate;
        
        public BlockedPlayerData(String playerName, String reason, long blockDate) {
            this.playerName = playerName;
            this.reason = reason;
            this.blockDate = blockDate;
        }
        
        public String getPlayerName() { return playerName; }
        public String getReason() { return reason; }
        public long getBlockDate() { return blockDate; }
        public void setReason(String reason) { this.reason = reason; }
    }
}

