package com.lobby.commands;

import com.lobby.social.friends.FriendInfo;
import com.lobby.social.friends.FriendManager;
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
            completions.removeIf(value -> !value.startsWith(args[0].toLowerCase(Locale.ROOT)));
            return completions;
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
}
