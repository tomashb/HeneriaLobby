package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Default implementation of {@link FriendsMenuActionHandler} used by the
 * friends main menu. The handler provides immediate player feedback for every
 * configured action to ensure that clicks never feel unresponsive, even while
 * the underlying feature set is still being developed.
 */
public class DefaultFriendsMenuActionHandler implements FriendsMenuActionHandler {

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    public DefaultFriendsMenuActionHandler(final LobbyPlugin plugin, final FriendsManager friendsManager) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
    }

    @Override
    public boolean handle(final Player player, final String action) {
        if (player == null || action == null || action.isBlank()) {
            return false;
        }
        final String normalized = action.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "open_friends_list" -> openFriendsList(player);
            case "open_add_friend" -> openAddFriend(player);
            case "open_requests" -> openFriendRequests(player);
            case "open_blocked" -> openBlockedList(player);
            case "open_settings" -> openSettings(player);
            case "open_favorites" -> openFavorites(player);
            case "open_statistics" -> openStatistics(player);
            case "back_to_profile" -> returnToProfile(player);
            default -> handleUnknownAction(player, action);
        };
    }

    private boolean openFriendsList(final Player player) {
        closeInventory(player);
        runLater(player, () -> new FriendsListMenu(plugin, friendsManager, player).open());
        return true;
    }

    private boolean openAddFriend(final Player player) {
        closeInventory(player);
        runLater(player, () -> new AddFriendMenu(plugin, friendsManager, player).open());
        return true;
    }

    private boolean openFriendRequests(final Player player) {
        closeInventory(player);
        runLater(player, () -> new FriendRequestsMenu(plugin, friendsManager, player));
        return true;
    }

    private boolean openBlockedList(final Player player) {
        closeInventory(player);
        runLater(player, () -> {
            playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f);
            player.sendMessage("§a✓ §7Menu joueurs bloqués ouvert !");
            player.sendMessage("§e⚠ §7En cours de développement - Configuration créée");
            player.sendMessage("§7Fichier: §bblocked.yml §7disponible");
        });
        return true;
    }

    private boolean openSettings(final Player player) {
        closeInventory(player);
        runLater(player, () -> {
            playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f);
            player.sendMessage("§a✓ §7Menu paramètres ouvert !");
            player.sendMessage("§e⚠ §7En cours de développement - Configuration créée");
            player.sendMessage("§7Fichier: §bsettings.yml §7disponible");
        });
        return true;
    }

    private boolean openFavorites(final Player player) {
        closeInventory(player);
        runLater(player, () -> {
            playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f);
            player.sendMessage("§a✓ §7Menu amis favoris ouvert !");
            player.sendMessage("§e⚠ §7En cours de développement - Configuration créée");
            player.sendMessage("§7Fichier: §bfavorites.yml §7disponible");
        });
        return true;
    }

    private boolean openStatistics(final Player player) {
        closeInventory(player);
        runLater(player, () -> {
            playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f);
            player.sendMessage("§a✓ §7Menu statistiques ouvert !");
            player.sendMessage("§e⚠ §7En cours de développement - Configuration créée");
            player.sendMessage("§7Fichier: §bstatistics.yml §7disponible");
        });
        return true;
    }

    private boolean returnToProfile(final Player player) {
        closeInventory(player);
        runLater(player, () -> {
            if (!plugin.getMenuManager().openMenu(player, "profil_menu")) {
                player.sendMessage("§cImpossible d'ouvrir le menu du profil pour le moment.");
            }
        });
        return true;
    }

    private boolean handleUnknownAction(final Player player, final String action) {
        closeInventory(player);
        runLater(player, () -> player.sendMessage("§cAction inconnue: " + action));
        return false;
    }

    private void closeInventory(final Player player) {
        if (player == null) {
            return;
        }
        player.closeInventory();
    }

    private void runLater(final Player player, final Runnable runnable) {
        if (plugin == null || player == null || runnable == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                runnable.run();
            }
        }, 1L);
    }

    private void playSound(final Player player, final Sound sound, final float pitch) {
        if (player == null || sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }
}

