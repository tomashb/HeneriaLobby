package fr.heneria.lobby.commands;

import fr.heneria.lobby.LobbyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ServersCommand implements CommandExecutor {

    private final LobbyPlugin plugin;

    public ServersCommand(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("lobby.command.servers")) {
            player.sendMessage(plugin.getMessageManager().get("no-permission"));
            return true;
        }
        plugin.getServerSelectorManager().open(player);
        return true;
    }
}
