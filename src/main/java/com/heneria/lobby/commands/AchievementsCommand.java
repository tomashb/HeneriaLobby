package com.heneria.lobby.commands;

import com.heneria.lobby.achievements.AchievementManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Opens the achievements menu for the player.
 */
public class AchievementsCommand implements CommandExecutor {

    private final AchievementManager achievementManager;

    public AchievementsCommand(AchievementManager achievementManager) {
        this.achievementManager = achievementManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            achievementManager.openMenu(player);
        } else {
            sender.sendMessage("Command only available to players.");
        }
        return true;
    }
}
