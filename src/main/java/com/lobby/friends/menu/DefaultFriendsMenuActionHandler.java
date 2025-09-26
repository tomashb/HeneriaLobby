package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import org.bukkit.Bukkit;
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

    public DefaultFriendsMenuActionHandler(final LobbyPlugin plugin) {
        this.plugin = plugin;
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
        runLater(player, () -> player.sendMessage("§a✓ Menu liste des amis (en cours de développement)"));
        return true;
    }

    private boolean openAddFriend(final Player player) {
        closeInventory(player);
        runLater(player, () -> player.sendMessage("§a✓ Menu ajout d'ami (en cours de développement)"));
        return true;
    }

    private boolean openFriendRequests(final Player player) {
        closeInventory(player);
        runLater(player, () -> player.sendMessage("§a✓ Menu demandes d'amitié (en cours de développement)"));
        return true;
    }

    private boolean openBlockedList(final Player player) {
        closeInventory(player);
        runLater(player, () -> player.sendMessage("§a✓ Menu joueurs bloqués (en cours de développement)"));
        return true;
    }

    private boolean openSettings(final Player player) {
        closeInventory(player);
        runLater(player, () -> player.sendMessage("§a✓ Menu paramètres d'amitié (en cours de développement)"));
        return true;
    }

    private boolean openFavorites(final Player player) {
        closeInventory(player);
        runLater(player, () -> player.sendMessage("§a✓ Menu amis favoris (en cours de développement)"));
        return true;
    }

    private boolean openStatistics(final Player player) {
        closeInventory(player);
        runLater(player, () -> player.sendMessage("§a✓ Menu statistiques d'amitié (en cours de développement)"));
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
}

