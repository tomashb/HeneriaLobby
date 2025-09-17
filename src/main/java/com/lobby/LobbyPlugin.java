package com.lobby;

import com.lobby.core.ConfigManager;
import com.lobby.core.DatabaseManager;
import com.lobby.core.PlayerDataManager;
import com.lobby.events.PlayerJoinLeaveEvent;
import com.lobby.utils.LogUtils;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyPlugin extends JavaPlugin {

    private static LobbyPlugin instance;
    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;

    public static LobbyPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            LogUtils.severe(this, "Database initialization failed. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        playerDataManager = new PlayerDataManager(this, databaseManager);

        getServer().getPluginManager().registerEvents(new PlayerJoinLeaveEvent(this, playerDataManager), this);

        LogUtils.info(this, "LobbyCore activé !");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        instance = null;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public void reloadLobbyConfig() {
        if (configManager != null) {
            configManager.reloadConfigs();
        }
        if (playerDataManager != null) {
            playerDataManager.reload();
        }
    }
}
