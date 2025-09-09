package com.heneria.lobby.commands;

import com.heneria.lobby.database.DatabaseManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.sql.Connection;

/**
 * Administrative command for various lobby diagnostics.
 */
public class LobbyAdminCommand implements CommandExecutor {

    private final DatabaseManager databaseManager;
    public LobbyAdminCommand(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("testdb")) {
            sender.sendMessage("§7[HeneriaLobby] Testing database connection...");
            try (Connection ignored = databaseManager.getConnection()) {
                sender.sendMessage("§aConnexion à la base de données réussie !");
            } catch (Exception e) {
                sender.sendMessage("§cÉchec de la connexion à la base de données : " + e.getMessage());
            }
            return true;
        }
        sender.sendMessage("§cUsage: /" + label + " testdb");
        return true;
    }
}

