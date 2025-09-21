package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
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
        final FriendManager friendManager = plugin.getFriendManager();
        final List<UUID> onlineFriends = friendManager.getOnlineFriends(player.getUniqueId());

        final Inventory menu = Bukkit.createInventory(null, 54, FRIENDS_ONLINE_TITLE);
        setupGreenBorders(menu);

        if (onlineFriends.isEmpty()) {
            final ItemStack noFriends = new ItemStack(Material.BARRIER);
            final ItemMeta meta = noFriends.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cAucun ami en ligne");
                meta.setLore(Arrays.asList(
                        "§7Vos amis ne sont pas connectés",
                        "§r",
                        "§eInvitez d'autres joueurs !"
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

        addBackButton(menu, 49);
        player.openInventory(menu);
    }

    public static void openFriendRequestsMenu(final Player player) {
        if (player == null) {
            return;
        }
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        final FriendManager friendManager = plugin.getFriendManager();
        final List<FriendRequest> requests = new ArrayList<>(friendManager.getPendingRequestsDetailed(player.getUniqueId()));

        final Inventory menu = Bukkit.createInventory(null, 54, FRIEND_REQUESTS_TITLE);
        setupGreenBorders(menu);

        final Map<Integer, FriendRequest> slotMap = new HashMap<>();
        if (requests.isEmpty()) {
            final ItemStack none = new ItemStack(Material.BARRIER);
            final ItemMeta meta = none.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cAucune demande d'ami");
                meta.setLore(Collections.singletonList("§7Vous n'avez aucune demande en attente"));
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
        final ItemStack back = new ItemStack(Material.ARROW);
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
