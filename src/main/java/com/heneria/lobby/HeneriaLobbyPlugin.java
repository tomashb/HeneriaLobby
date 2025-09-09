package com.heneria.lobby;

import com.heneria.lobby.commands.LobbyAdminCommand;
import com.heneria.lobby.database.DatabaseManager;
import com.heneria.lobby.listeners.PlayerListener;
import com.heneria.lobby.player.PlayerDataManager;
import org.bukkit.plugin.java.JavaPlugin;

public class HeneriaLobbyPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.init()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Connected to the database successfully.");

        playerDataManager = new PlayerDataManager(this, databaseManager);
        getServer().getPluginManager().registerEvents(new PlayerListener(playerDataManager), this);
        getCommand("lobbyadmin").setExecutor(new LobbyAdminCommand(databaseManager));
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
}
