package com.heneria.lobby;

import com.heneria.lobby.database.DatabaseManager;
import com.heneria.lobby.listeners.PlayerListener;
import com.heneria.lobby.player.PlayerDataManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class HeneriaLobbyPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.init();
            getLogger().info("Connected to the database successfully.");
        } catch (SQLException e) {
            getLogger().severe("Database connection failed: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        playerDataManager = new PlayerDataManager(this, databaseManager);
        getServer().getPluginManager().registerEvents(new PlayerListener(playerDataManager), this);
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
