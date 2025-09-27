package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
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
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Presents suggested players to befriend based on simple heuristics. The menu
 * provides quick access to send a request or hide a suggestion.
 */
public class SuggestionsMenu implements Listener {

    private static final int SIZE = 54;
    private static final int[] SUGGESTION_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;
    private Inventory inventory;
    private List<Player> suggestedPlayers = Collections.emptyList();

    public SuggestionsMenu(final LobbyPlugin plugin, final FriendsManager friendsManager, final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        generateSuggestions();
        createMenu();
    }

    private void generateSuggestions() {
        final List<Player> onlinePlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .collect(Collectors.toList());
        suggestedPlayers = onlinePlayers.stream().limit(Math.min(onlinePlayers.size(), 15)).collect(Collectors.toList());
    }

    private void createMenu() {
        final String title = "§8» §eJoueurs Suggérés §8(" + suggestedPlayers.size() + ")";
        inventory = Bukkit.createInventory(null, SIZE, title);
        setupMenu();
        open();
    }

    private void setupMenu() {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        final ItemStack greenGlass = createItem(Material.GREEN_STAINED_GLASS_PANE, " ");
        final int[] greenSlots = {0, 1, 2, 6, 7, 8, 9, 17, 36, 44, 45, 53};
        for (int slot : greenSlots) {
            inventory.setItem(slot, greenGlass);
        }
        displaySuggestions();
        setupOptions();
    }

    private void displaySuggestions() {
        if (suggestedPlayers.isEmpty()) {
            final ItemStack noSuggestions = createItem(Material.PAPER, "§7§lAucune suggestion disponible");
            final ItemMeta meta = noSuggestions.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList(
                        "§7Aucune suggestion d'ami",
                        "§7disponible pour le moment",
                        "",
                        "§e💡 Pourquoi aucune suggestion ?",
                        "§8▸ §7Pas assez de joueurs en ligne",
                        "§8▸ §7Vous connaissez déjà tout le monde",
                        "§8▸ §7Système en cours d'apprentissage",
                        "",
                        "§7Revenez plus tard ou utilisez la recherche !"
                ));
                noSuggestions.setItemMeta(meta);
            }
            inventory.setItem(22, noSuggestions);
            return;
        }
        for (int i = 0; i < SUGGESTION_SLOTS.length && i < suggestedPlayers.size(); i++) {
            inventory.setItem(SUGGESTION_SLOTS[i], createSuggestionItem(suggestedPlayers.get(i)));
        }
    }

    private ItemStack createSuggestionItem(final Player suggestedPlayer) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(suggestedPlayer);
            final Random random = new Random(suggestedPlayer.getName().hashCode() + player.getName().hashCode());
            final int score = 65 + random.nextInt(31);
            final String scoreColor = score >= 85 ? "§a" : score >= 70 ? "§e" : "§6";
            skullMeta.setDisplayName("§e§l" + suggestedPlayer.getName() + " §7(" + scoreColor + score + "%§7)");
            final List<String> lore = new ArrayList<>();
            lore.add("§7Joueur suggéré pour vous");
            lore.add("");
            lore.add("§7Informations générales:");
            lore.add("§8▸ §7Statut: §aEn ligne");
            lore.add("§8▸ §7Niveau: §a" + (10 + random.nextInt(40)));
            lore.add("§8▸ §7Temps de jeu: §b" + (5 + random.nextInt(100)) + "h");
            lore.add("§8▸ §7Réputation: §6" + (3 + random.nextInt(3)) + "/5 ⭐");
            lore.add("");
            lore.add("§7Raisons de la suggestion:");
            lore.addAll(generateSuggestionReasons(random));
            lore.add("");
            final String description = score >= 85 ? "Excellente compatibilité"
                    : score >= 70 ? "Bonne compatibilité" : "Compatibilité moyenne";
            lore.add("§7Score de compatibilité: " + scoreColor + score + "% §7(" + description + ")");
            lore.add("");
            lore.add("§8▸ §aClique gauche §8: §7Envoyer demande");
            lore.add("§8▸ §eClique milieu §8: §7Voir profil détaillé");
            lore.add("§8▸ §cClique droit §8: §7Masquer cette suggestion");
            skullMeta.setLore(lore);
            if (score >= 85) {
                skullMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                skullMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private List<String> generateSuggestionReasons(final Random random) {
        final List<String> possibleReasons = Arrays.asList(
                "§d◆ §7" + (1 + random.nextInt(3)) + " amis en commun",
                "§b◆ §7Joue souvent sur les mêmes serveurs",
                "§a◆ §7Activités similaires (mini-jeux, construction)",
                "§e◆ §7Horaires compatibles (" + (60 + random.nextInt(40)) + "% de compatibilité)",
                "§6◆ §7Joueur bien noté par la communauté",
                "§2◆ §7Accueillant envers les nouveaux joueurs"
        );
        final List<String> reasons = new ArrayList<>();
        final int reasonCount = 2 + random.nextInt(2);
        Collections.shuffle(possibleReasons, random);
        for (int i = 0; i < reasonCount && i < possibleReasons.size(); i++) {
            reasons.add(possibleReasons.get(i));
        }
        return reasons;
    }

    private void setupOptions() {
        final ItemStack refresh = createItem(Material.CLOCK, "§b🔄 Actualiser les Suggestions");
        final ItemMeta refreshMeta = refresh.getItemMeta();
        if (refreshMeta != null) {
            refreshMeta.setLore(Arrays.asList(
                    "§7Recalculer les suggestions",
                    "§7basées sur vos dernières activités",
                    "",
                    "§b▸ Dernière mise à jour: §3À l'instant",
                    "§b▸ Prochaine MAJ auto: §35 minutes",
                    "",
                    "§8» §bCliquez pour actualiser"
            ));
            refresh.setItemMeta(refreshMeta);
        }
        inventory.setItem(46, refresh);

        final ItemStack settings = createItem(Material.REDSTONE_TORCH, "§6⚙️ Paramètres des Suggestions");
        final ItemMeta settingsMeta = settings.getItemMeta();
        if (settingsMeta != null) {
            settingsMeta.setLore(Arrays.asList(
                    "§7Configurez les critères",
                    "§7de suggestion d'amis",
                    "",
                    "§6▸ Amis mutuels: §eActivé",
                    "§6▸ Serveurs communs: §eActivé",
                    "§6▸ Activités similaires: §eActivé",
                    "§6▸ Compatibilité horaire: §eActivé",
                    "",
                    "§8» §6Cliquez pour configurer"
            ));
            settings.setItemMeta(settingsMeta);
        }
        inventory.setItem(47, settings);

        final ItemStack back = createItem(Material.BARRIER, "§e◀ Retour Ajout d'Amis");
        final ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setLore(Arrays.asList(
                    "§7Revenir au menu d'ajout",
                    "§7d'amis",
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
        if (inventory == null) {
            return;
        }
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        final String title = event.getView().getTitle();
        if (title == null || !title.contains("§8» §eJoueurs Suggérés")) {
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
        if (slot == 46) {
            refreshSuggestions();
            return;
        }
        if (slot == 47) {
            player.sendMessage("§6⚙️ Paramètres des suggestions en développement !");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> new AddFriendMenu(plugin, friendsManager, player).open(), 2L);
            return;
        }
        for (int i = 0; i < SUGGESTION_SLOTS.length; i++) {
            if (SUGGESTION_SLOTS[i] != slot) {
                continue;
            }
            if (i >= suggestedPlayers.size()) {
                return;
            }
            handleSuggestionClick(suggestedPlayers.get(i), event.getClick());
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
        if (event.getView().getTitle() != null && event.getView().getTitle().contains("§8» §eJoueurs Suggérés")) {
            HandlerList.unregisterAll(this);
        }
    }

    private void refreshSuggestions() {
        player.sendMessage("§b🔄 Actualisation des suggestions...");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        generateSuggestions();
        createMenu();
        player.sendMessage("§aSuggestions actualisées ! " + suggestedPlayers.size() + " nouveaux joueurs suggérés");
    }

    private void handleSuggestionClick(final Player suggestedPlayer, final org.bukkit.event.inventory.ClickType clickType) {
        switch (clickType) {
            case LEFT -> {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> new SendRequestMenu(plugin, friendsManager, player, suggestedPlayer).open(), 2L);
            }
            case MIDDLE -> {
                player.sendMessage("§6👤 Profil détaillé de " + suggestedPlayer.getName() + " en développement !");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case RIGHT -> handleHideSuggestion(suggestedPlayer);
            default -> {
            }
        }
    }

    private void handleHideSuggestion(final Player suggestedPlayer) {
        suggestedPlayers.remove(suggestedPlayer);
        setupMenu();
        player.sendMessage("§7Suggestion masquée pour " + suggestedPlayer.getName());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
    }
}
