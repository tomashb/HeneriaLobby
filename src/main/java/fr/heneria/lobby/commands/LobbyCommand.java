package fr.heneria.lobby.commands;

import fr.heneria.lobby.LobbyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LobbyCommand implements CommandExecutor {

    private final LobbyPlugin plugin;

    public LobbyCommand(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("setspawn")) {
            if (!player.hasPermission("lobby.admin.setspawn")) {
                player.sendMessage(plugin.getMessageManager().get("no-permission"));
                return true;
            }
            plugin.getSpawnManager().setSpawn(player.getLocation());
            player.sendMessage(plugin.getMessageManager().get("spawn-set"));
            return true;
        }

        player.sendMessage("Usage: /lobby setspawn");
        return true;
    }
}
