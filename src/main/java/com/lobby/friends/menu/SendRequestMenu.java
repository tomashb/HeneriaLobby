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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;

/**
 * Confirmation menu to send a friend request with optional pre-defined
 * messages or a custom message placeholder.
 */
public class SendRequestMenu implements Listener {

    private static final int SIZE = 27;
    private static final String TITLE_PREFIX = "§8» §aEnvoyer à §e";
    private static final int CANCEL_SLOT = 21;
    private static final int BACK_SLOT = 22;

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;
    private final Player targetPlayer;
    private final Inventory inventory;

    public SendRequestMenu(final LobbyPlugin plugin,
                           final FriendsManager friendsManager,
                           final Player player,
                           final Player targetPlayer) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        this.targetPlayer = targetPlayer;
        final String title = TITLE_PREFIX + targetPlayer.getName();
        this.inventory = Bukkit.createInventory(null, SIZE, title);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        setupMenu();
    }

    private void setupMenu() {
        final ItemStack greenGlass = createItem(Material.GREEN_STAINED_GLASS_PANE, " ");
        final int[] greenSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 19, 25, 26};
        for (int slot : greenSlots) {
            inventory.setItem(slot, greenGlass);
        }

        final ItemStack targetProfile = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta profileMeta = targetProfile.getItemMeta();
        if (profileMeta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(targetPlayer);
            skullMeta.setDisplayName("§e§l" + targetPlayer.getName());
            skullMeta.setLore(Arrays.asList(
                    "§7Profil du joueur:",
                    "§8▸ §7Statut: §aEn ligne",
                    "§8▸ §7Niveau: §a?",
                    "§8▸ §7Temps de jeu: §b?",
                    "§8▸ §7Amis en commun: §d?",
                    "§8▸ §7Réputation: §6 5/5 ⭐",
                    "",
                    "§7Compatibilité estimée: §a85%"
            ));
            targetProfile.setItemMeta(skullMeta);
        }
        inventory.setItem(4, targetProfile);

        setupPredefinedMessages();
        setupActions();
    }

    private void setupPredefinedMessages() {
        inventory.setItem(10, createMessageItem(Material.BOOK, "§a📝 Message Amical",
                "Salut ! J'aimerais t'ajouter en ami !"));
        inventory.setItem(11, createMessageItem(Material.BOOK, "§e📝 Message Décontracté",
                "Salut, on pourrait être amis ?"));
        inventory.setItem(12, createMessageItem(Material.BOOK, "§b📝 Message Contexte",
                "Je t'ai vu sur le serveur, tu as l'air sympa !"));

        final ItemStack customMsg = createItem(Material.WRITABLE_BOOK, "§6📝 Message Personnalisé");
        final ItemMeta customMeta = customMsg.getItemMeta();
        if (customMeta != null) {
            customMeta.setLore(Arrays.asList(
                    "§7Écrivez votre propre message",
                    "§7personnalisé",
                    "",
                    "§6▸ Longueur max: §e120 caractères",
                    "§6▸ Filtrage automatique activé",
                    "",
                    "§8» §6Cliquez pour écrire"
            ));
            customMsg.setItemMeta(customMeta);
        }
        inventory.setItem(15, customMsg);

        final ItemStack noMsg = createItem(Material.PAPER, "§7📝 Sans Message");
        final ItemMeta noMeta = noMsg.getItemMeta();
        if (noMeta != null) {
            noMeta.setLore(Arrays.asList(
                    "§7Envoyer une demande simple",
                    "§7sans message personnalisé",
                    "",
                    "§7Le joueur recevra une demande",
                    "§7avec le message par défaut",
                    "",
                    "§8» §7Cliquez pour envoyer"
            ));
            noMsg.setItemMeta(noMeta);
        }
        inventory.setItem(16, noMsg);
    }

    private ItemStack createMessageItem(final Material material, final String name, final String message) {
        final ItemStack item = createItem(material, name);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList(
                    "§7Message prédéfini:",
                    "§f\"" + message + "\"",
                    "",
                    "§8» §aCliquez pour envoyer"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void setupActions() {
        final ItemStack cancel = createItem(Material.BARRIER, "§c✗ Annuler");
        final ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setLore(Arrays.asList(
                    "§7Annuler l'envoi de la demande",
                    "§7et revenir au menu précédent",
                    "",
                    "§8» §cCliquez pour annuler"
            ));
            cancel.setItemMeta(cancelMeta);
        }
        inventory.setItem(CANCEL_SLOT, cancel);

        final ItemStack back = createItem(Material.ARROW, "§e◀ Retour");
        final ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setLore(Arrays.asList(
                    "§7Revenir au menu précédent",
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
        final String title = event.getView().getTitle();
        if (title == null || !title.contains(TITLE_PREFIX)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        final int slot = event.getSlot();
        switch (slot) {
            case 10 -> sendRequest("Salut ! J'aimerais t'ajouter en ami !");
            case 11 -> sendRequest("Salut, on pourrait être amis ?");
            case 12 -> sendRequest("Je t'ai vu sur le serveur, tu as l'air sympa !");
            case 15 -> handleCustomMessage();
            case 16 -> sendRequest(null);
            case CANCEL_SLOT, BACK_SLOT -> returnToSearch();
            default -> {
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
        if (event.getView().getTitle() != null && event.getView().getTitle().contains(TITLE_PREFIX)) {
            HandlerList.unregisterAll(this);
        }
    }

    private void handleCustomMessage() {
        player.closeInventory();
        player.sendMessage("§6📝 Tapez votre message personnalisé dans le chat:");
        player.sendMessage("§7(ou tapez 'cancel' pour annuler)");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        // Future chat prompt integration
    }

    private void returnToSearch() {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> new PlayerSearchMenu(plugin, friendsManager, player).open(), 2L);
    }

    private void sendRequest(final String message) {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        final String finalMessage = message == null || message.trim().isEmpty()
                ? "Salut ! J'aimerais t'ajouter en ami !" : message;
        friendsManager.sendFriendRequest(player, targetPlayer.getName(), finalMessage).thenAccept(success ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.sendMessage("§a✓ Demande d'amitié envoyée avec succès !");
                    } else {
                        player.sendMessage("§cImpossible d'envoyer la demande (déjà envoyée ou déjà amis)");
                    }
                }));
    }
}
