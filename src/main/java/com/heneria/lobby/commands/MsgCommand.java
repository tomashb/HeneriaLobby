package com.heneria.lobby.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.heneria.lobby.HeneriaLobbyPlugin;
import com.heneria.lobby.friends.PrivateMessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;

/**
 * Implements /msg and /r commands for private messaging.
 */
public class MsgCommand implements CommandExecutor {

    private final HeneriaLobbyPlugin plugin;
    private final PrivateMessageManager messageManager;

    public MsgCommand(HeneriaLobbyPlugin plugin, PrivateMessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("msg")) {
            if (args.length < 2) {
                player.sendMessage("Usage: /msg <player> <message>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if (target != null) {
                sendLocalMessage(player, target, message);
            } else {
                sendProxyMessage(player, args[0], message);
                player.sendMessage(plugin.getMessages().getString("player-offline").replace("{player}", args[0]));
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("r")) {
            if (args.length < 1) {
                player.sendMessage("Usage: /r <message>");
                return true;
            }
            UUID last = messageManager.getLastMessaged(player.getUniqueId());
            if (last == null) {
                player.sendMessage(plugin.getMessages().getString("reply-no-target"));
                return true;
            }
            Player target = Bukkit.getPlayer(last);
            String message = String.join(" ", args);
            if (target != null) {
                sendLocalMessage(player, target, message);
            } else {
                sendProxyMessage(player, last.toString(), message);
                String name = Bukkit.getOfflinePlayer(last).getName();
                player.sendMessage(plugin.getMessages().getString("player-offline")
                        .replace("{player}", name != null ? name : last.toString()));
            }
            return true;
        }
        return false;
    }

    private void sendLocalMessage(Player from, Player to, String message) {
        String sender = plugin.getMessages().getString("message-format-sender")
                .replace("{to}", to.getName())
                .replace("{message}", message);
        String receiver = plugin.getMessages().getString("message-format-receiver")
                .replace("{from}", from.getName())
                .replace("{message}", message);
        from.sendMessage(sender);
        to.sendMessage(receiver);
        messageManager.setLastMessaged(from.getUniqueId(), to.getUniqueId());
        messageManager.setLastMessaged(to.getUniqueId(), from.getUniqueId());
    }

    private void sendProxyMessage(Player from, String target, String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("MSG");
        out.writeUTF(from.getUniqueId().toString());
        out.writeUTF(target);
        out.writeUTF(message);
        from.sendPluginMessage(plugin, "heneria:msg", out.toByteArray());
    }
}
