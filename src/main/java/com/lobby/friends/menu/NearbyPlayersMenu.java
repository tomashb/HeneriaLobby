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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Displays players near the viewer with rich contextual information. Distance
 * filters are configurable within the menu itself.
 */
public class NearbyPlayersMenu implements Listener {

    private static final int SIZE = 54;
    private static final int[] PLAYER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Player player;
    private Inventory inventory;
    private List<Player> nearbyPlayers = Collections.emptyList();
    private int maxDistance = 100;

    public NearbyPlayersMenu(final LobbyPlugin plugin, final FriendsManager friendsManager, final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        findNearbyPlayers();
        createMenu();
    }

    private void findNearbyPlayers() {
        nearbyPlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .filter(p -> p.getWorld().equals(player.getWorld()))
                .filter(p -> p.getLocation().distance(player.getLocation()) <= maxDistance)
                .collect(Collectors.toList());
    }

    private void createMenu() {
        final String title = "§8» §bJoueurs Proches §8(" + nearbyPlayers.size() + ")";
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
        displayNearbyPlayers();
        setupFilters();
        setupNavigation();
    }

    private void displayNearbyPlayers() {
        if (nearbyPlayers.isEmpty()) {
            final ItemStack noPlayers = createItem(Material.PAPER, "§7§lAucun joueur à proximité");
            final ItemMeta meta = noPlayers.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList(
                        "§7Aucun joueur détecté dans",
                        "§7un rayon de " + maxDistance + " blocs",
                        "",
                        "§e💡 Suggestions:",
                        "§8▸ §7Déplacez-vous vers d'autres zones",
                        "§8▸ §7Augmentez la distance de détection",
                        "§8▸ §7Attendez que d'autres joueurs arrivent",
                        "",
                        "§8» §bUtilisez 'Changer distance'"
                ));
                noPlayers.setItemMeta(meta);
            }
            inventory.setItem(22, noPlayers);
            return;
        }
        for (int i = 0; i < PLAYER_SLOTS.length && i < nearbyPlayers.size(); i++) {
            final Player nearby = nearbyPlayers.get(i);
            inventory.setItem(PLAYER_SLOTS[i], createNearbyPlayerItem(nearby));
        }
    }

    private ItemStack createNearbyPlayerItem(final Player nearbyPlayer) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(nearbyPlayer);
            final double distance = player.getLocation().distance(nearbyPlayer.getLocation());
            final String distanceText = distance < 10 ? String.format("%.1fm", distance) : String.format("%.0fm", distance);
            final String proximityColor = distance < 10 ? "§a" : distance < 50 ? "§2" : "§e";
            skullMeta.setDisplayName(proximityColor + "§l" + nearbyPlayer.getName() + " §8(" + distanceText + ")");
            final List<String> lore = new ArrayList<>();
            lore.add("§7Joueur détecté à proximité");
            lore.add("");
            lore.add("§7Localisation:");
            lore.add("§8▸ §7Distance: §b" + distanceText);
            lore.add("§8▸ §7Direction: §b" + getDirection(player, nearbyPlayer));
            lore.add("§8▸ §7Monde: §e" + nearbyPlayer.getWorld().getName());
            lore.add("§8▸ §7Serveur: §eLobby");
            lore.add("");
            lore.add("§7Informations joueur:");
            lore.add("§8▸ §7Niveau: §a?");
            lore.add("§8▸ §7Temps de jeu: §b?");
            lore.add("§8▸ §7Amis en commun: §d?");
            lore.add("");
            lore.add("§7Activité actuelle: §6" + detectActivity(nearbyPlayer));
            lore.add("");
            lore.add("§8▸ §aClique gauche §8: §7Envoyer demande");
            lore.add("§8▸ §eClique milieu §8: §7Se téléporter");
            lore.add("§8▸ §cClique droit §8: §7Envoyer message");
            skullMeta.setLore(lore);
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private String getDirection(final Player from, final Player to) {
        final Vector difference = to.getLocation().toVector().subtract(from.getLocation().toVector());
        final double angle = Math.toDegrees(Math.atan2(difference.getZ(), difference.getX()));
        double normalized = (angle + 360) % 360;
        final String horizontal;
        if (normalized >= 337.5 || normalized < 22.5) {
            horizontal = "Est ➡";
        } else if (normalized < 67.5) {
            horizontal = "Sud-Est ↘";
        } else if (normalized < 112.5) {
            horizontal = "Sud ⬇";
        } else if (normalized < 157.5) {
            horizontal = "Sud-Ouest ↙";
        } else if (normalized < 202.5) {
            horizontal = "Ouest ⬅";
        } else if (normalized < 247.5) {
            horizontal = "Nord-Ouest ↖";
        } else if (normalized < 292.5) {
            horizontal = "Nord ⬆";
        } else {
            horizontal = "Nord-Est ↗";
        }
        final double dy = to.getLocation().getY() - from.getLocation().getY();
        if (Math.abs(dy) > 5) {
            return horizontal + (dy > 0 ? " (⬆" + String.format("%.0f", dy) + "m)" : " (⬇" + String.format("%.0f", -dy) + "m)");
        }
        return horizontal;
    }

    private String detectActivity(final Player target) {
        if (target.isSneaking()) {
            return "Discret";
        }
        if (target.isSprinting()) {
            return "En course";
        }
        if (target.isSwimming()) {
            return "Nage";
        }
        if (target.isGliding()) {
            return "Vol";
        }
        if (target.getVelocity().lengthSquared() > 0.01) {
            return "En mouvement";
        }
        return "Stationnaire";
    }

    private void setupFilters() {
        final ItemStack distanceFilter = createItem(Material.COMPASS, "§b📍 Filtre Distance: §3" + maxDistance + "m");
        final ItemMeta distanceMeta = distanceFilter.getItemMeta();
        if (distanceMeta != null) {
            distanceMeta.setLore(Arrays.asList(
                    "§7Ajustez la distance de détection",
                    "",
                    "§b▸ Distance actuelle: §3" + maxDistance + " blocs",
                    "§7▸ Joueurs détectés: §8" + nearbyPlayers.size(),
                    "",
                    "§7Options disponibles:",
                    "§8▸ §750 blocs (Très proche)",
                    "§8▸ §7100 blocs (Proche)",
                    "§8▸ §7200 blocs (Étendu)",
                    "§8▸ §7Monde entier",
                    "",
                    "§8» §bCliquez pour changer"
            ));
            distanceFilter.setItemMeta(distanceMeta);
        }
        inventory.setItem(46, distanceFilter);

        final ItemStack scopeFilter = createItem(Material.ENDER_EYE, "§6🌍 Portée: §eProximité");
        final ItemMeta scopeMeta = scopeFilter.getItemMeta();
        if (scopeMeta != null) {
            scopeMeta.setLore(Arrays.asList(
                    "§7Choisissez la portée de recherche",
                    "",
                    "§6▸ Portée actuelle: §eProximité (" + maxDistance + "m)",
                    "",
                    "§7Options:",
                    "§8▸ §7Même position (10m)",
                    "§8▸ §7Proximité (100m)",
                    "§8▸ §7Même monde",
                    "§8▸ §7Même serveur",
                    "§8▸ §7Tout le réseau",
                    "",
                    "§8» §6Cliquez pour changer"
            ));
            scopeFilter.setItemMeta(scopeMeta);
        }
        inventory.setItem(47, scopeFilter);

        final ItemStack refresh = createItem(Material.CLOCK, "§a🔄 Actualiser (Auto: 15s)");
        final ItemMeta refreshMeta = refresh.getItemMeta();
        if (refreshMeta != null) {
            refreshMeta.setLore(Arrays.asList(
                    "§7Actualiser la liste des",
                    "§7joueurs à proximité",
                    "",
                    "§a▸ Mise à jour auto: §215 secondes",
                    "§7▸ Dernière MAJ: §8À l'instant",
                    "",
                    "§8» §aCliquez pour actualiser maintenant"
            ));
            refresh.setItemMeta(refreshMeta);
        }
        inventory.setItem(48, refresh);
    }

    private void setupNavigation() {
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
        if (title == null || !title.contains("§8» §bJoueurs Proches")) {
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
            cycleDistance();
            return;
        }
        if (slot == 47) {
            player.sendMessage("§6🌍 Changement de portée en développement !");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }
        if (slot == 48) {
            refreshNearbyPlayers();
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> new AddFriendMenu(plugin, friendsManager, player).open(), 2L);
            return;
        }
        for (int i = 0; i < PLAYER_SLOTS.length; i++) {
            if (PLAYER_SLOTS[i] != slot) {
                continue;
            }
            if (i >= nearbyPlayers.size()) {
                return;
            }
            handleNearbyPlayerClick(nearbyPlayers.get(i), event.getClick());
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
        if (event.getView().getTitle() != null && event.getView().getTitle().contains("§8» §bJoueurs Proches")) {
            HandlerList.unregisterAll(this);
        }
    }

    private void cycleDistance() {
        switch (maxDistance) {
            case 50 -> maxDistance = 100;
            case 100 -> maxDistance = 200;
            case 200 -> maxDistance = 1000;
            default -> maxDistance = 50;
        }
        player.sendMessage("§b📍 Distance changée à " + maxDistance + " blocs");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        refreshNearbyPlayers();
    }

    private void refreshNearbyPlayers() {
        findNearbyPlayers();
        createMenu();
        player.sendMessage("§a🔄 Liste actualisée ! " + nearbyPlayers.size() + " joueur(s) détecté(s)");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
    }

    private void handleNearbyPlayerClick(final Player nearbyPlayer, final org.bukkit.event.inventory.ClickType clickType) {
        switch (clickType) {
            case LEFT -> {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> new SendRequestMenu(plugin, friendsManager, player, nearbyPlayer).open(), 2L);
            }
            case MIDDLE -> {
                player.closeInventory();
                player.teleport(nearbyPlayer.getLocation());
                player.sendMessage("§a🚀 Vous avez été téléporté vers " + nearbyPlayer.getName() + " !");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }
            case RIGHT -> {
                player.closeInventory();
                player.sendMessage("§e💬 Tapez votre message pour " + nearbyPlayer.getName() + " dans le chat:");
                player.sendMessage("§7(ou tapez 'cancel' pour annuler)");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            }
            default -> {
            }
        }
    }
}
