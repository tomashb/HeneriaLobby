package fr.heneria.lobby.commands;

import fr.heneria.lobby.LobbyPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final LobbyPlugin plugin;

    public SpawnCommand(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("lobby.command.spawn")) {
            player.sendMessage(plugin.getMessageManager().get("no-permission"));
            return true;
        }
        Location spawn = plugin.getSpawnManager().getSpawn();
        if (spawn == null) {
            player.sendMessage(plugin.getMessageManager().get("spawn-not-set"));
            return true;
        }
        player.teleport(spawn);
        player.sendMessage(plugin.getMessageManager().get("spawn-teleport"));
        return true;
    }
}
