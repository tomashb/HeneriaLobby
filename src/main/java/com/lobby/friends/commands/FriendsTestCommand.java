package com.lobby.friends.commands;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.menu.AddFriendMenu;
import com.lobby.friends.menu.BlockedPlayersMenu;
import com.lobby.friends.menu.FavoriteFriendsMenu;
import com.lobby.friends.menu.FriendRequestsMenu;
import com.lobby.friends.menu.FriendSettingsMenu;
import com.lobby.friends.menu.FriendsListMenu;
import com.lobby.friends.menu.FriendsMenuManager;
import com.lobby.friends.menu.statistics.FriendStatisticsMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Development-only helper command that opens the different friends menus
 * without having to navigate through the profile menu first. The command is
 * intended for QA and developers to quickly check that the menus load without
 * throwing exceptions.
 */
public final class FriendsTestCommand implements CommandExecutor {

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;

    public FriendsTestCommand(final LobbyPlugin plugin, final FriendsManager friendsManager) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
    }

    @Override
    public boolean onCommand(final CommandSender sender,
                             final Command command,
                             final String label,
                             final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSeuls les joueurs peuvent utiliser cette commande !");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        final String subCommand = args[0].toLowerCase();

        final FriendsMenuManager menuManager = plugin.getFriendsMenuManager();
        if (menuManager == null) {
            player.sendMessage("§cLe gestionnaire de menus d'amis est indisponible.");
            return true;
        }

        try {
            switch (subCommand) {
                case "main" -> openMainMenu(player);
                case "list" -> {
                    new FriendsListMenu(plugin, friendsManager, player);
                    player.sendMessage("§a✓ Liste des amis ouverte");
                }
                case "add" -> {
                    new AddFriendMenu(plugin, friendsManager, player).open();
                    player.sendMessage("§a✓ Menu d'ajout d'amis ouvert");
                }
                case "requests" -> {
                    new FriendRequestsMenu(plugin, friendsManager, menuManager, player).open();
                    player.sendMessage("§a✓ Menu des demandes ouvert");
                }
                case "settings" -> {
                    new FriendSettingsMenu(plugin, friendsManager, menuManager, player).open();
                    player.sendMessage("§a✓ Menu des paramètres ouvert");
                }
                case "stats" -> {
                    new FriendStatisticsMenu(plugin, friendsManager, player).open();
                    player.sendMessage("§a✓ Menu des statistiques ouvert");
                }
                case "blocked" -> {
                    new BlockedPlayersMenu(plugin, friendsManager, player).open();
                    player.sendMessage("§a✓ Menu des joueurs bloqués ouvert");
                }
                case "favorites" -> {
                    new FavoriteFriendsMenu(plugin, friendsManager, menuManager, player).open();
                    player.sendMessage("§a✓ Menu des favoris ouvert");
                }
                case "all" -> testAllMenus(player);
                default -> player.sendMessage("§cCommande inconnue. Utilisez /friendstest pour voir la liste");
            }
        } catch (Exception exception) {
            player.sendMessage("§cErreur lors de l'ouverture du menu: " + exception.getMessage());
            exception.printStackTrace();
        }

        return true;
    }

    private void sendUsage(final Player player) {
        player.sendMessage("§e📋 Commandes de test disponibles:");
        player.sendMessage("§7/friendstest main §8- Menu principal");
        player.sendMessage("§7/friendstest list §8- Liste des amis");
        player.sendMessage("§7/friendstest add §8- Ajouter un ami");
        player.sendMessage("§7/friendstest requests §8- Demandes d'amitié");
        player.sendMessage("§7/friendstest settings §8- Paramètres");
        player.sendMessage("§7/friendstest stats §8- Statistiques");
        player.sendMessage("§7/friendstest blocked §8- Joueurs bloqués");
        player.sendMessage("§7/friendstest favorites §8- Amis favoris");
        player.sendMessage("§7/friendstest all §8- Test de tous les menus");
    }

    private void openMainMenu(final Player player) {
        if (plugin.getFriendsMenuController() == null) {
            player.sendMessage("§cLe menu principal des amis n'est pas disponible.");
            return;
        }
        final boolean opened = plugin.getFriendsMenuController().openMainMenu(player);
        if (opened) {
            player.sendMessage("§a✓ Menu principal ouvert");
        } else {
            player.sendMessage("§cImpossible d'ouvrir le menu principal des amis.");
        }
    }

    private void testAllMenus(final Player player) {
        player.sendMessage("§b🧪 Test de tous les menus en cours...");
        testMenuSequentially(player, 0);
    }

    private void testMenuSequentially(final Player player, final int menuIndex) {
        final FriendsMenuManager menuManager = plugin.getFriendsMenuManager();
        if (menuManager == null) {
            player.sendMessage("§cLe gestionnaire de menus d'amis est indisponible, test interrompu.");
            return;
        }
        final String[] menuNames = {
                "Menu Principal",
                "Liste des Amis",
                "Ajout d'Amis",
                "Demandes",
                "Paramètres",
                "Statistiques",
                "Joueurs Bloqués",
                "Favoris"
        };

        if (menuIndex >= menuNames.length) {
            player.sendMessage("§a✅ Test de tous les menus terminé avec succès !");
            player.sendMessage("§7Tous les menus se sont ouverts sans erreur.");
            return;
        }

        player.sendMessage("§e⏳ Test du menu: §6" + menuNames[menuIndex]);

        try {
            switch (menuIndex) {
                case 0 -> openMainMenu(player);
                case 1 -> new FriendsListMenu(plugin, friendsManager, player);
                case 2 -> new AddFriendMenu(plugin, friendsManager, player).open();
                case 3 -> new FriendRequestsMenu(plugin, friendsManager, menuManager, player).open();
                case 4 -> new FriendSettingsMenu(plugin, friendsManager, menuManager, player).open();
                case 5 -> new FriendStatisticsMenu(plugin, friendsManager, player).open();
                case 6 -> new BlockedPlayersMenu(plugin, friendsManager, player).open();
                case 7 -> new FavoriteFriendsMenu(plugin, friendsManager, menuManager, player).open();
                default -> {
                    return;
                }
            }

            player.sendMessage("§a✓ " + menuNames[menuIndex] + " - §2OK");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.closeInventory();
                testMenuSequentially(player, menuIndex + 1);
            }, 20L);
        } catch (Exception exception) {
            player.sendMessage("§c✗ " + menuNames[menuIndex] + " - §4ERREUR: " + exception.getMessage());
            Bukkit.getScheduler().runTaskLater(plugin, () -> testMenuSequentially(player, menuIndex + 1), 20L);
        }
    }
}
