package com.lobby.commands;

import com.lobby.social.friends.AcceptMode;
import com.lobby.social.friends.FriendInfo;
import com.lobby.social.friends.FriendManager;
import com.lobby.social.friends.FriendSettings;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class FriendCommand implements CommandExecutor, TabCompleter {

    private final FriendManager friendManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");

    public FriendCommand(final FriendManager friendManager) {
        this.friendManager = friendManager;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }
        final Player player = (Player) sender;
        if (args.length == 0) {
            sendUsage(player, label);
            return true;
        }
        final String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "add":
            case "request":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " add <joueur>");
                    return true;
                }
                if (friendManager.sendFriendRequest(player, args[1])) {
                    player.sendMessage("§aDemande envoyée à §6" + args[1] + "§a !");
                }
                return true;
            case "settings":
                handleSettingsCommand(player, label, args);
                return true;
            case "accept":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " accept <joueur>");
                    return true;
                }
                friendManager.acceptFriendRequest(player, args[1]);
                return true;
            case "deny":
            case "refuse":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " deny <joueur>");
                    return true;
                }
                friendManager.denyFriendRequest(player, args[1]);
                return true;
            case "remove":
            case "delete":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " remove <joueur>");
                    return true;
                }
                friendManager.removeFriend(player, args[1]);
                return true;
            case "list":
                sendFriendList(player);
                return true;
            default:
                sendUsage(player, label);
                return true;
        }
    }

    private void sendFriendList(final Player player) {
        final List<FriendInfo> friends = friendManager.getFriendsList(player.getUniqueId());
        player.sendMessage(ChatColor.GRAY + "§m----------------------------------------");
        player.sendMessage(ChatColor.GREEN + "Amis en ligne: " + getOnlineCount(friends) + "/" + friends.size());
        for (final FriendInfo friend : friends) {
            final ChatColor statusColor = friend.isOnline() ? ChatColor.GREEN : ChatColor.RED;
            final String serverInfo = friend.isOnline() && friend.getServer() != null ? ChatColor.GRAY + " (" + friend.getServer() + ")" : "";
            final String since = dateFormat.format(new Date(friend.getFriendsSince()));
            player.sendMessage(statusColor + friend.getName() + serverInfo + ChatColor.YELLOW + " - Amis depuis " + since);
        }
        player.sendMessage(ChatColor.GRAY + "§m----------------------------------------");
    }

    private int getOnlineCount(final List<FriendInfo> friends) {
        int online = 0;
        for (final FriendInfo friend : friends) {
            if (friend.isOnline()) {
                online++;
            }
        }
        return online;
    }

    private void sendUsage(final Player player, final String label) {
        player.sendMessage(ChatColor.YELLOW + "Utilisation de /" + label + ":");
        player.sendMessage(ChatColor.GOLD + "/" + label + " add <joueur> " + ChatColor.WHITE + "- Envoyer une demande d'ami");
        player.sendMessage(ChatColor.GOLD + "/" + label + " accept <joueur> " + ChatColor.WHITE + "- Accepter une demande");
        player.sendMessage(ChatColor.GOLD + "/" + label + " deny <joueur> " + ChatColor.WHITE + "- Refuser une demande");
        player.sendMessage(ChatColor.GOLD + "/" + label + " remove <joueur> " + ChatColor.WHITE + "- Supprimer un ami");
        player.sendMessage(ChatColor.GOLD + "/" + label + " list " + ChatColor.WHITE + "- Liste des amis");
        player.sendMessage(ChatColor.GOLD + "/" + label + " settings toggle <option> " + ChatColor.WHITE + "- Configurer vos préférences");
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 1) {
            final List<String> completions = new ArrayList<>();
            completions.add("add");
            completions.add("accept");
            completions.add("deny");
            completions.add("remove");
            completions.add("list");
            completions.add("settings");
            completions.removeIf(value -> !value.startsWith(args[0].toLowerCase(Locale.ROOT)));
            return completions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("settings")) {
            return filterByPrefix(List.of("toggle"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("settings") && args[1].equalsIgnoreCase("toggle")) {
            return filterByPrefix(List.of("requests", "notifications", "visibility", "favorites"), args[2]);
        }
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        final Player player = (Player) sender;
        if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))) {
            final List<String> pendingNames = new ArrayList<>();
            for (final UUID requester : friendManager.getPendingRequests(player.getUniqueId())) {
                final Player requesterPlayer = player.getServer().getPlayer(requester);
                if (requesterPlayer != null) {
                    pendingNames.add(requesterPlayer.getName());
                }
            }
            return filterByPrefix(pendingNames, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filterByPrefix(final List<String> values, final String prefix) {
        final String lower = prefix.toLowerCase(Locale.ROOT);
        final List<String> filtered = new ArrayList<>();
        for (final String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                filtered.add(value);
            }
        }
        return filtered;
    }

    private void handleSettingsCommand(final Player player, final String label, final String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("toggle")) {
            sendSettingsUsage(player, label);
            return;
        }

        final FriendSettings current = friendManager.getFriendSettings(player.getUniqueId());
        final String option = args[2].toLowerCase(Locale.ROOT);
        FriendSettings updated;
        String feedback;
        switch (option) {
            case "requests":
                final AcceptMode nextMode = cycleAcceptMode(current.getAcceptRequests());
                updated = new FriendSettings(nextMode, current.isShowOnlineStatus(), current.isAllowNotifications(),
                        current.isAutoAcceptFavorites(), current.getMaxFriends());
                feedback = ChatColor.GREEN + "Demandes d'amis: " + describeAcceptMode(nextMode);
                break;
            case "notifications":
                final boolean allowNotifications = !current.isAllowNotifications();
                updated = new FriendSettings(current.getAcceptRequests(), current.isShowOnlineStatus(), allowNotifications,
                        current.isAutoAcceptFavorites(), current.getMaxFriends());
                feedback = ChatColor.GREEN + "Notifications: " + (allowNotifications ? "Activées" : "Désactivées");
                break;
            case "visibility":
                final boolean showStatus = !current.isShowOnlineStatus();
                updated = new FriendSettings(current.getAcceptRequests(), showStatus, current.isAllowNotifications(),
                        current.isAutoAcceptFavorites(), current.getMaxFriends());
                feedback = ChatColor.GREEN + "Visibilité: " + (showStatus ? "Visible" : "Caché");
                break;
            case "favorites":
                final boolean autoFavorites = !current.isAutoAcceptFavorites();
                updated = new FriendSettings(current.getAcceptRequests(), current.isShowOnlineStatus(),
                        current.isAllowNotifications(), autoFavorites, current.getMaxFriends());
                feedback = ChatColor.GREEN + "Acceptation auto favoris: " + (autoFavorites ? "Activée" : "Désactivée");
                break;
            default:
                sendSettingsUsage(player, label);
                return;
        }

        friendManager.updateSettings(player.getUniqueId(), updated);
        player.sendMessage(feedback);
    }

    private void sendSettingsUsage(final Player player, final String label) {
        player.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " settings toggle <requests|notifications|visibility|favorites>");
    }

    private AcceptMode cycleAcceptMode(final AcceptMode current) {
        return switch (current) {
            case ALL -> AcceptMode.FRIENDS_OF_FRIENDS;
            case FRIENDS_OF_FRIENDS -> AcceptMode.NONE;
            case NONE -> AcceptMode.ALL;
        };
    }

    private String describeAcceptMode(final AcceptMode mode) {
        return switch (mode) {
            case ALL -> "Tous";
            case FRIENDS_OF_FRIENDS -> "Amis d'amis";
            case NONE -> "Aucun";
        };
    }
}
