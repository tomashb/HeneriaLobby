package com.lobby.commands;

import com.lobby.social.groups.GroupManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GroupCommand implements CommandExecutor, TabCompleter {

    private final GroupManager groupManager;

    public GroupCommand(final GroupManager groupManager) {
        this.groupManager = groupManager;
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
        final String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create":
                groupManager.createGroup(player);
                return true;
            case "invite":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " invite <joueur>");
                    return true;
                }
                groupManager.inviteToGroup(player, args[1]);
                return true;
            case "accept":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " accept <joueur>");
                    return true;
                }
                groupManager.acceptInvitation(player, args[1]);
                return true;
            case "deny":
            case "refuse":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " deny <joueur>");
                    return true;
                }
                groupManager.denyInvitation(player, args[1]);
                return true;
            case "leave":
                groupManager.leaveGroup(player);
                return true;
            case "disband":
                groupManager.disbandGroup(player);
                return true;
            default:
                sendUsage(player, label);
                return true;
        }
    }

    private void sendUsage(final Player player, final String label) {
        player.sendMessage(ChatColor.YELLOW + "Utilisation de /" + label + ":");
        player.sendMessage(ChatColor.GOLD + "/" + label + " create " + ChatColor.WHITE + "- Créer un groupe");
        player.sendMessage(ChatColor.GOLD + "/" + label + " invite <joueur> " + ChatColor.WHITE + "- Inviter un joueur");
        player.sendMessage(ChatColor.GOLD + "/" + label + " accept <joueur> " + ChatColor.WHITE + "- Accepter une invitation");
        player.sendMessage(ChatColor.GOLD + "/" + label + " deny <joueur> " + ChatColor.WHITE + "- Refuser une invitation");
        player.sendMessage(ChatColor.GOLD + "/" + label + " leave " + ChatColor.WHITE + "- Quitter votre groupe");
        player.sendMessage(ChatColor.GOLD + "/" + label + " disband " + ChatColor.WHITE + "- Dissoudre votre groupe");
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 1) {
            final List<String> subCommands = new ArrayList<>();
            Collections.addAll(subCommands, "create", "invite", "accept", "deny", "leave", "disband");
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            subCommands.removeIf(value -> !value.startsWith(prefix));
            return subCommands;
        }
        return Collections.emptyList();
    }
}
