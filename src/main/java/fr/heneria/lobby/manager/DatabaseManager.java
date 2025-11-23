package fr.heneria.lobby.manager;

import fr.heneria.lobby.HeneriaLobby;

public class DatabaseManager extends Manager {

    public DatabaseManager(HeneriaLobby plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        // Placeholder for MariaDB connection setup
        // "Prépare juste la structure"
    }

    @Override
    public void onDisable() {
        // Close connection
    }
}
