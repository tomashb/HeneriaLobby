package com.heneria.lobby.commands;

import com.heneria.lobby.HeneriaLobbyPlugin;
import com.heneria.lobby.friends.FriendManager;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Handles the /friends command and its subcommands.
 */
public class FriendsCommand implements CommandExecutor {

    private final FriendManager friendManager;
    private final HeneriaLobbyPlugin plugin;

    public FriendsCommand(HeneriaLobbyPlugin plugin, FriendManager friendManager) {
        this.plugin = plugin;
        this.friendManager = friendManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            listFriends(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "add" -> {
                if (args.length < 2) {
                    player.sendMessage("Usage: /friends add <player>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null || target.getUniqueId() == null) {
                    player.sendMessage(plugin.getMessages().getString("friend-not-found").replace("{player}", args[1]));
                    return true;
                }
                UUID targetId = target.getUniqueId();
                if (player.getUniqueId().equals(targetId)) {
                    player.sendMessage("You cannot add yourself.");
                    return true;
                }
                if (friendManager.areFriends(player.getUniqueId(), targetId)) {
                    player.sendMessage(plugin.getMessages().getString("already-friends").replace("{player}", target.getName()));
                    return true;
                }
                if (friendManager.hasPendingRequest(player.getUniqueId(), targetId)) {
                    player.sendMessage(plugin.getMessages().getString("request-already-sent").replace("{player}", target.getName()));
                    return true;
                }
                friendManager.sendRequest(player.getUniqueId(), targetId);
                player.sendMessage(plugin.getMessages().getString("friend-request-sent").replace("{player}", target.getName()));
                Player online = target.isOnline() ? target.getPlayer() : null;
                if (online != null) {
                    online.sendMessage(plugin.getMessages().getString("friend-request-received").replace("{player}", player.getName()));
                } else {
                    ByteArrayDataOutput out = ByteStreams.newDataOutput();
                    out.writeUTF("REQUEST");
                    out.writeUTF(player.getUniqueId().toString());
                    out.writeUTF(targetId.toString());
                    out.writeUTF(player.getName());
                    player.sendPluginMessage(plugin, "heneria:friends", out.toByteArray());
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage("Usage: /friends remove <player>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null || target.getUniqueId() == null) {
                    player.sendMessage(plugin.getMessages().getString("friend-not-found").replace("{player}", args[1]));
                    return true;
                }
                friendManager.removeFriend(player.getUniqueId(), target.getUniqueId());
                player.sendMessage(plugin.getMessages().getString("friend-removed").replace("{player}", target.getName()));
                Player online = target.isOnline() ? target.getPlayer() : null;
                if (online != null) {
                    online.sendMessage(plugin.getMessages().getString("friend-removed").replace("{player}", player.getName()));
                }
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage("Usage: /friends accept <player>");
                    return true;
                }
                OfflinePlayer requester = Bukkit.getOfflinePlayer(args[1]);
                if (requester == null || requester.getUniqueId() == null) {
                    player.sendMessage(plugin.getMessages().getString("friend-not-found").replace("{player}", args[1]));
                    return true;
                }
                if (!friendManager.hasIncomingRequest(player.getUniqueId(), requester.getUniqueId())) {
                    player.sendMessage(plugin.getMessages().getString("no-request").replace("{player}", requester.getName()));
                    return true;
                }
                friendManager.acceptRequest(player.getUniqueId(), requester.getUniqueId());
                player.sendMessage(plugin.getMessages().getString("friend-added").replace("{player}", requester.getName()));
                Player online = requester.isOnline() ? requester.getPlayer() : null;
                if (online != null) {
                    online.sendMessage(plugin.getMessages().getString("friend-added").replace("{player}", player.getName()));
                }
            }
            case "deny" -> {
                if (args.length < 2) {
                    player.sendMessage("Usage: /friends deny <player>");
                    return true;
                }
                OfflinePlayer requester = Bukkit.getOfflinePlayer(args[1]);
                if (requester == null || requester.getUniqueId() == null) {
                    player.sendMessage(plugin.getMessages().getString("friend-not-found").replace("{player}", args[1]));
                    return true;
                }
                if (!friendManager.hasIncomingRequest(player.getUniqueId(), requester.getUniqueId())) {
                    player.sendMessage(plugin.getMessages().getString("no-request").replace("{player}", requester.getName()));
                    return true;
                }
                friendManager.denyRequest(player.getUniqueId(), requester.getUniqueId());
                player.sendMessage(plugin.getMessages().getString("no-request").replace("{player}", requester.getName()));
            }
            case "list" -> listFriends(player);
            default -> player.sendMessage("Unknown subcommand.");
        }
        return true;
    }

    private void listFriends(Player player) {
        Set<UUID> friends = friendManager.getFriends(player.getUniqueId());
        if (friends.isEmpty()) {
            player.sendMessage(plugin.getMessages().getString("no-friends"));
            return;
        }
        player.sendMessage(plugin.getMessages().getString("friend-list-header"));
        friends.stream()
                .map(id -> {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                    return op.getName() != null ? op.getName() : id.toString();
                })
                .forEach(name -> player.sendMessage(plugin.getMessages().getString("friend-list-entry").replace("{player}", name)));
    }
}
