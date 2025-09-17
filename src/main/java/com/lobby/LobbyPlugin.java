package com.lobby;

import com.lobby.commands.AdminCommands;
import com.lobby.commands.EconomyCommands;
import com.lobby.core.ConfigManager;
import com.lobby.core.DatabaseManager;
import com.lobby.core.PlayerDataManager;
import com.lobby.economy.EconomyManager;
import com.lobby.events.PlayerJoinLeaveEvent;
import com.lobby.utils.LogUtils;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyPlugin extends JavaPlugin {

    private static LobbyPlugin instance;
    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private EconomyManager economyManager;

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
        economyManager = new EconomyManager(this);

        registerCommands();

        getServer().getPluginManager().registerEvents(new PlayerJoinLeaveEvent(this, playerDataManager, economyManager), this);

        LogUtils.info(this, "LobbyCore activé !");
    }

    @Override
    public void onDisable() {
        if (economyManager != null) {
            economyManager.shutdown();
        }
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

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public void reloadLobbyConfig() {
        if (configManager != null) {
            configManager.reloadConfigs();
        }
        if (playerDataManager != null) {
            playerDataManager.reload();
        }
        if (economyManager != null) {
            economyManager.reload();
        }
    }

    private void registerCommands() {
        final EconomyCommands economyCommands = new EconomyCommands(this, economyManager);
        if (getCommand("coins") != null) {
            getCommand("coins").setExecutor(economyCommands);
            getCommand("coins").setTabCompleter(economyCommands);
        }
        if (getCommand("tokens") != null) {
            getCommand("tokens").setExecutor(economyCommands);
            getCommand("tokens").setTabCompleter(economyCommands);
        }
        if (getCommand("pay") != null) {
            getCommand("pay").setExecutor(economyCommands);
            getCommand("pay").setTabCompleter(economyCommands);
        }
        if (getCommand("top") != null) {
            getCommand("top").setExecutor(economyCommands);
            getCommand("top").setTabCompleter(economyCommands);
        }

        final AdminCommands adminCommands = new AdminCommands(this, economyManager);
        if (getCommand("lobbyadmin") != null) {
            getCommand("lobbyadmin").setExecutor(adminCommands);
            getCommand("lobbyadmin").setTabCompleter(adminCommands);
        }
    }
}
