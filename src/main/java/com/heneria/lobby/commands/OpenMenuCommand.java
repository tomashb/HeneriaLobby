package com.heneria.lobby.commands;

import com.heneria.lobby.menu.GUIManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Generic command to open a specific GUI menu.
 */
public class OpenMenuCommand implements CommandExecutor {

    private final GUIManager guiManager;
    private final String menu;

    public OpenMenuCommand(GUIManager guiManager, String menu) {
        this.guiManager = guiManager;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            guiManager.openMenu(player, menu);
        }
        return true;
    }
}
