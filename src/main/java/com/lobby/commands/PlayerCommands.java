package com.lobby.commands;

import com.lobby.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlayerCommands implements CommandExecutor, TabExecutor {

    private static final Map<String, String> COMMAND_MESSAGES = Map.of(
            "lobby", "commands.lobby_unavailable",
            "shop", "commands.shop_unavailable",
            "serveurs", "commands.servers_unavailable",
            "profil", "commands.profile_unavailable",
            "discord", "commands.discord_unavailable"
    );

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.sendConfigMessage(sender, "commands.player_only");
            return true;
        }

        final String commandName = command.getName().toLowerCase(Locale.ROOT);
        final String messagePath = COMMAND_MESSAGES.getOrDefault(commandName, "commands.unavailable");
        MessageUtils.sendConfigMessage(sender, messagePath, Map.of("command", "/" + label));
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return Collections.emptyList();
    }
}
