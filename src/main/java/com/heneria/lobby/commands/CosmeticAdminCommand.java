package com.heneria.lobby.commands;

import com.heneria.lobby.cosmetics.CosmeticsManager;
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
 * Administrative command to manage player cosmetics.
 */
public class CosmeticAdminCommand implements CommandExecutor, TabCompleter {

    private final CosmeticsManager cosmeticsManager;
    private final PlayerDataManager dataManager;

    public CosmeticAdminCommand(CosmeticsManager cosmeticsManager, PlayerDataManager dataManager) {
        this.cosmeticsManager = cosmeticsManager;
        this.dataManager = dataManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("heneria.lobby.admin.cosmetics")) {
            sender.sendMessage("§cVous n'avez pas la permission.");
            return true;
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("clear")) {
            sender.sendMessage("§cUsage: /" + label + " clear <joueur>");
            return true;
        }
        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        UUID uuid;
        if (target != null) {
            uuid = target.getUniqueId();
            cosmeticsManager.clearCosmetics(target);
        } else {
            PlayerData data = dataManager.loadByUsername(targetName);
            if (data == null) {
                sender.sendMessage("§cJoueur introuvable.");
                return true;
            }
            uuid = data.getUuid();
            cosmeticsManager.clearCosmetics(uuid);
        }
        sender.sendMessage("§c✔ §7Tous les cosmétiques de §e" + targetName + " §7ont été réinitialisés.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("clear").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
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
