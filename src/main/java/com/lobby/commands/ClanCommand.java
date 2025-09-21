package com.lobby.commands;

import com.lobby.LobbyPlugin;
import com.lobby.npcs.ActionProcessor;
import com.lobby.social.clans.Clan;
import com.lobby.social.clans.ClanManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private final ClanManager clanManager;

    public ClanCommand(final ClanManager clanManager) {
        this.clanManager = clanManager;
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
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " create <nom> <tag>");
                    return true;
                }
                clanManager.createClan(player, args[1], args[2]);
                return true;
            case "delete":
                handleDelete(player);
                return true;
            case "invite":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " invite <joueur> [message]");
                    return true;
                }
                final String message = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
                clanManager.inviteToClan(player, args[1], message);
                return true;
            case "accept":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " accept <clan>");
                    return true;
                }
                clanManager.acceptInvitation(player, args[1]);
                return true;
            case "deny":
            case "refuse":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /" + label + " deny <clan>");
                    return true;
                }
                clanManager.denyInvitation(player, args[1]);
                return true;
            default:
                sendUsage(player, label);
                return true;
        }
    }

    private void sendUsage(final Player player, final String label) {
        player.sendMessage(ChatColor.YELLOW + "Utilisation de /" + label + ":");
        player.sendMessage(ChatColor.GOLD + "/" + label + " create <nom> <tag> " + ChatColor.WHITE + "- Créer un clan");
        player.sendMessage(ChatColor.GOLD + "/" + label + " delete " + ChatColor.WHITE + "- Supprimer votre clan");
        player.sendMessage(ChatColor.GOLD + "/" + label + " invite <joueur> [message] " + ChatColor.WHITE + "- Inviter un joueur");
        player.sendMessage(ChatColor.GOLD + "/" + label + " accept <clan> " + ChatColor.WHITE + "- Accepter une invitation");
        player.sendMessage(ChatColor.GOLD + "/" + label + " deny <clan> " + ChatColor.WHITE + "- Refuser une invitation");
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 1) {
            final List<String> subCommands = new ArrayList<>();
            Collections.addAll(subCommands, "create", "delete", "invite", "accept", "deny");
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            subCommands.removeIf(value -> !value.startsWith(prefix));
            return subCommands;
        }
        return Collections.emptyList();
    }

    private void handleDelete(final Player player) {
        final Clan playerClan = clanManager.getPlayerClan(player.getUniqueId());
        if (playerClan == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucun clan!");
            return;
        }
        if (!playerClan.isLeader(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Seul le leader peut supprimer le clan!");
            return;
        }

        ActionProcessor.openClanDeleteConfirmation(player);
        if (LobbyPlugin.getInstance() == null || LobbyPlugin.getInstance().getNpcManager() == null
                || LobbyPlugin.getInstance().getNpcManager().getActionProcessor() == null) {
            player.sendMessage(ChatColor.RED + "Impossible d'afficher la confirmation de suppression pour le moment.");
        }
    }
}
