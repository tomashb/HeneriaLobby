package com.lobby.commands;

import com.lobby.menus.MenuManager;
import com.lobby.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Dedicated command executor for the /amis command.
 */
public class FriendsCommand implements CommandExecutor {

    private final MenuManager menuManager;

    public FriendsCommand(final MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @Override
    public boolean onCommand(final CommandSender sender,
                             final Command command,
                             final String label,
                             final String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.sendConfigMessage(sender, "commands.player_only");
            return true;
        }

        final boolean opened = menuManager != null && menuManager.openFriendsMenu(player, 0);
        if (!opened) {
            MessageUtils.sendConfigMessage(player, "menus.not_found", Map.of("menu", "amis"));
        }
        return true;
    }
}
