package fr.heneria.lobby;

import fr.heneria.lobby.listeners.PlayerJoinListener;
import fr.heneria.lobby.manager.DatabaseManager;
import fr.heneria.lobby.manager.ItemManager;
import org.bukkit.plugin.java.JavaPlugin;

public class HeneriaLobby extends JavaPlugin {

    private ItemManager itemManager;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        // Initialize Managers
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.onEnable();

        this.itemManager = new ItemManager(this);
        this.itemManager.onEnable();

        // Register Listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new fr.heneria.lobby.listeners.InventoryListener(this), this);

        getLogger().info("HeneriaLobby has been enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.onDisable();
        }
        if (itemManager != null) {
            itemManager.onDisable();
        }
        getLogger().info("HeneriaLobby has been disabled!");
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
