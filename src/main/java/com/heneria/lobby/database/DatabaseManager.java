package com.heneria.lobby.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles the connection pool to the MySQL/MariaDB database.
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the connection pool and ensures required tables exist.
     *
     * @return {@code true} if the initialization succeeded, {@code false} otherwise
     */
    public boolean init() {
        FileConfiguration config = plugin.getConfig();

        HikariConfig hikariConfig = new HikariConfig();
        String jdbcUrl = "jdbc:mysql://" + config.getString("database.host") + ":" +
                config.getInt("database.port") + "/" + config.getString("database.name");
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getString("database.user"));
        hikariConfig.setPassword(config.getString("database.password"));
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setPoolName("HeneriaLobbyPool");

        if (config.getBoolean("debug")) {
            plugin.getLogger().info("Attempting database connection to " + jdbcUrl +
                    " as user '" + config.getString("database.user") + "'.");
        }

        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            createTables();
            return true;
        } catch (HikariPool.PoolInitializationException e) {
            plugin.getLogger().severe("[HeneriaLobby] ERREUR: Impossible de se connecter à la base de données !");
            plugin.getLogger().severe("[HeneriaLobby] Veuillez vérifier les points suivants :");
            plugin.getLogger().severe("[HeneriaLobby] 1. Les informations dans config.yml (host, port, database, user, password) sont-elles correctes ?");
            plugin.getLogger().severe("[HeneriaLobby] 2. Votre serveur MySQL/MariaDB est-il bien démarré ?");
            plugin.getLogger().severe("[HeneriaLobby] 3. Un pare-feu ne bloque-t-il pas la connexion ?");
            plugin.getLogger().severe("[HeneriaLobby] Le plugin va maintenant se désactiver.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error: " + e.getMessage());
        }
        return false;
    }

    private void createTables() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS players (" +
                            "uuid VARCHAR(36) PRIMARY KEY," +
                            "username VARCHAR(16)," +
                            "coins BIGINT DEFAULT 0," +
                            "first_join TIMESTAMP," +
                            "last_seen TIMESTAMP" +
                            ")"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_friends (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_uuid VARCHAR(36)," +
                            "friend_uuid VARCHAR(36)," +
                            "status ENUM('ACCEPTED','PENDING') NOT NULL," +
                            "created_at TIMESTAMP," +
                            "INDEX idx_player_uuid (player_uuid)," +
                            "INDEX idx_friend_uuid (friend_uuid)" +
                            ")"
            );
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
