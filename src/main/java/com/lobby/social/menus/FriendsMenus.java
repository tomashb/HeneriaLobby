package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
import com.lobby.heads.HeadDatabaseManager;
import com.lobby.social.friends.FriendManager;
import com.lobby.social.friends.FriendRequest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FriendsMenus {

    private static final Map<UUID, Map<Integer, FriendRequest>> REQUEST_SLOTS = new ConcurrentHashMap<>();
    public static final String FRIENDS_ONLINE_TITLE = "§8» §aAmis En Ligne";
    public static final String FRIEND_REQUESTS_TITLE = "§8» §eDemandes d'Amis";

    private FriendsMenus() {
    }

    public static void openFriendsOnlineMenu(final Player player) {
        if (player == null) {
            return;
        }
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        final FriendManager friendManager = plugin.getFriendManager();
        if (friendManager == null) {
            return;
        }
        final List<UUID> onlineFriends = friendManager.getOnlineFriends(player.getUniqueId());

        final Inventory menu = Bukkit.createInventory(null, 54, FRIENDS_ONLINE_TITLE);
        setupGreenBorders(menu);

        if (onlineFriends.isEmpty()) {
            final ItemStack noFriends = createHeadItem("hdb:9945", Material.PLAYER_HEAD);
            final ItemMeta meta = noFriends.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cAucun ami en ligne");
                meta.setLore(Arrays.asList(
                        "§7Vos amis ne sont pas connectés",
                        "§7pour le moment.",
                        "§r",
                        "§eInvitez d'autres joueurs à jouer !"
                ));
                noFriends.setItemMeta(meta);
            }
            menu.setItem(22, noFriends);
        } else {
            int slot = 10;
            for (UUID friendUuid : onlineFriends) {
                if (slot >= 44) {
                    break;
                }
                final ItemStack friendItem = createOnlineFriendItem(friendUuid);
                menu.setItem(slot, friendItem);
                slot = nextContentSlot(slot);
            }
        }

        menu.setItem(46, createRefreshItem());
        addBackButton(menu, 49);
        player.openInventory(menu);
    }

    public static void openFriendRequestsMenu(final Player player) {
        if (player == null) {
            return;
        }
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        final FriendManager friendManager = plugin.getFriendManager();
        if (friendManager == null) {
            return;
        }
        final List<FriendRequest> requests = new ArrayList<>(friendManager.getPendingRequestsDetailed(player.getUniqueId()));

        final Inventory menu = Bukkit.createInventory(null, 54, FRIEND_REQUESTS_TITLE);
        setupGreenBorders(menu);

        final Map<Integer, FriendRequest> slotMap = new HashMap<>();
        if (requests.isEmpty()) {
            final ItemStack none = createHeadItem("hdb:1455", Material.PLAYER_HEAD);
            final ItemMeta meta = none.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cAucune demande d'ami");
                meta.setLore(Arrays.asList(
                        "§7Vous n'avez aucune demande",
                        "§7d'ami en attente.",
                        "§r",
                        "§ePartagez votre pseudo pour",
                        "§erecevoir des demandes !"
                ));
                none.setItemMeta(meta);
            }
            menu.setItem(22, none);
        } else {
            int slot = 10;
            for (FriendRequest request : requests) {
                if (slot >= 44) {
                    break;
                }
                final ItemStack requestItem = createFriendRequestItem(request);
                menu.setItem(slot, requestItem);
                slotMap.put(slot, request);
                slot = nextContentSlot(slot);
            }
        }
        REQUEST_SLOTS.put(player.getUniqueId(), slotMap);

        addBackButton(menu, 49);
        player.openInventory(menu);
    }

    public static FriendRequest getRequestAt(final Player player, final int slot) {
        final Map<Integer, FriendRequest> map = REQUEST_SLOTS.get(player.getUniqueId());
        if (map == null) {
            return null;
        }
        return map.get(slot);
    }

    public static void clearRequestCache(final UUID playerUuid) {
        if (playerUuid != null) {
            REQUEST_SLOTS.remove(playerUuid);
        }
    }

    private static ItemStack createOnlineFriendItem(final UUID friendUuid) {
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(friendUuid);
        final String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Joueur";
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName("§a" + name);
            meta.setLore(Arrays.asList(
                    "§7Statut: §aEn ligne",
                    "§7Bon jeu ensemble !"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createFriendRequestItem(final FriendRequest request) {
        final OfflinePlayer sender = Bukkit.getOfflinePlayer(request.getSender());
        final String senderName = sender.getName() != null ? sender.getName() : "Inconnu";
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(sender);
            meta.setDisplayName("§eDemande de §6" + senderName);
            final List<String> lore = new ArrayList<>();
            lore.add("§7Clique gauche: §aAccepter");
            lore.add("§7Clique droit: §cRefuser");
            lore.add("§r");
            lore.add("§7Reçue il y a §f" + formatElapsed(request.getTimestamp()));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void addBackButton(final Inventory menu, final int slot) {
        final ItemStack back = createHeadItem("hdb:9334", Material.ARROW);
        final ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lRetour");
            meta.setLore(Collections.singletonList("§7Retourner au menu précédent"));
            back.setItemMeta(meta);
        }
        menu.setItem(slot, back);
    }

    private static void setupGreenBorders(final Inventory inventory) {
        final int[] borderSlots = {0, 1, 2, 6, 7, 8, 9, 17, 45, 53};
        final ItemStack pane = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        final ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7");
            pane.setItemMeta(meta);
        }
        for (int slot : borderSlots) {
            inventory.setItem(slot, pane.clone());
        }
    }

    private static ItemStack createRefreshItem() {
        final ItemStack refresh = createHeadItem("hdb:38878", Material.PLAYER_HEAD);
        final ItemMeta meta = refresh.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lActualiser");
            meta.setLore(Arrays.asList(
                    "§7Actualiser la liste des",
                    "§7amis en ligne.",
                    "§r",
                    "§b▶ Cliquez pour actualiser!"
            ));
            refresh.setItemMeta(meta);
        }
        return refresh;
    }

    private static ItemStack createHeadItem(final String headId, final Material fallback) {
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        if (plugin == null) {
            return new ItemStack(fallback);
        }
        final HeadDatabaseManager manager = plugin.getHeadDatabaseManager();
        if (manager == null) {
            return new ItemStack(fallback);
        }
        return manager.getHead(headId, fallback);
    }

    private static int nextContentSlot(final int currentSlot) {
        int slot = currentSlot + 1;
        if ((slot + 1) % 9 == 0) {
            slot += 2;
        }
        return slot;
    }

    private static String formatElapsed(final long timestamp) {
        final long elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - timestamp) / 1000L);
        final long minutes = elapsedSeconds / 60L;
        if (minutes <= 0) {
            return "quelques secondes";
        }
        if (minutes < 60) {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        }
        final long hours = minutes / 60;
        if (hours < 24) {
            return hours + " heure" + (hours > 1 ? "s" : "");
        }
        final long days = hours / 24;
        return days + " jour" + (days > 1 ? "s" : "");
    }
}
