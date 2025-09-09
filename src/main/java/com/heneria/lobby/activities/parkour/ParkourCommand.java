package com.heneria.lobby.activities.parkour;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /parkour command allowing players to teleport and view leaderboards.
 */
public class ParkourCommand implements CommandExecutor {

    private final ParkourManager parkourManager;

    public ParkourCommand(ParkourManager parkourManager) {
        this.parkourManager = parkourManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("Usage: /parkour <spawn|checkpoint|top>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> parkourManager.teleportToStart(player);
            case "checkpoint" -> parkourManager.teleportToCheckpoint(player);
            case "top" -> {
                var top = parkourManager.getTopTimes(5);
                player.sendMessage("§6--- Top Parkour ---");
                int i = 1;
                for (String entry : top) {
                    player.sendMessage("§e" + i++ + ". §f" + entry);
                }
            }
            default -> player.sendMessage("Usage: /parkour <spawn|checkpoint|top>");
        }
        return true;
    }
}

