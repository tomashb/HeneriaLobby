package com.heneria.lobby.commands;

import com.heneria.lobby.menu.GUIManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to open the main navigation menu.
 */
public class MenuCommand implements CommandExecutor {

    private final GUIManager guiManager;

    public MenuCommand(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            guiManager.openMenu(player, "main");
        }
        return true;
    }
}

