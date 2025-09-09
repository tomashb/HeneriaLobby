package com.heneria.lobby.commands;

import com.heneria.lobby.economy.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Displays the player's current coin balance.
 */
public class CoinsCommand implements CommandExecutor {

    private final EconomyManager economyManager;

    public CoinsCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command only available to players.");
            return true;
        }
        long coins = economyManager.getCoins(player.getUniqueId());
        player.sendMessage(ChatColor.GOLD + "Vous avez " + coins + " coins.");
        return true;
    }
}
