package com.heneria.lobby.commands;

import com.heneria.lobby.economy.EconomyManager;
import com.heneria.lobby.player.PlayerData;
import com.heneria.lobby.player.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Administrative economy command allowing coin management.
 */
public class EconomyAdminCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economyManager;
    private final PlayerDataManager dataManager;

    public EconomyAdminCommand(EconomyManager economyManager, PlayerDataManager dataManager) {
        this.economyManager = economyManager;
        this.dataManager = dataManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("heneria.lobby.admin.eco")) {
            sender.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /" + label + " <give|take|set|look> <joueur> [montant]");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String targetName = args[1];

        Player target = Bukkit.getPlayerExact(targetName);
        PlayerData data;
        if (target != null) {
            data = dataManager.getPlayerData(target.getUniqueId());
        } else {
            data = dataManager.loadByUsername(targetName);
        }
        if (data == null) {
            sender.sendMessage("§cJoueur introuvable.");
            return true;
        }
        UUID uuid = data.getUuid();
        long balance = data.getCoins();

        switch (sub) {
            case "look": {
                sender.sendMessage("§3ℹ §7Le solde de §e" + data.getUsername() + " §7est de §6" + balance + " Coins.");
                return true;
            }
            case "give":
            case "take":
            case "set":
                break;
            default:
                sender.sendMessage("§cSous-commande inconnue.");
                return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cMontant manquant.");
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cMontant invalide.");
            return true;
        }

        long newBalance = balance;
        if (sub.equals("give")) {
            newBalance = balance + amount;
            economyManager.addCoins(uuid, amount);
            sender.sendMessage("§a✔ §7Vous avez ajouté §6" + amount + " Coins §7à §e" + data.getUsername() + "§7. Nouveau solde : §6" + newBalance + "§7.");
        } else if (sub.equals("take")) {
            newBalance = Math.max(0, balance - amount);
            dataManager.setCoins(uuid, newBalance);
            sender.sendMessage("§c✔ §7Vous avez retiré §6" + amount + " Coins §7à §e" + data.getUsername() + "§7. Nouveau solde : §6" + newBalance + "§7.");
        } else { // set
            newBalance = amount;
            dataManager.setCoins(uuid, newBalance);
            sender.sendMessage("§e✔ §7Le solde de §e" + data.getUsername() + " §7a été défini à §6" + newBalance + " Coins.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("give", "take", "set", "look");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
