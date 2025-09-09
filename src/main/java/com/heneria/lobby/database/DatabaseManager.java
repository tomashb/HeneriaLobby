package com.heneria.lobby.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
     */
    public void init() throws SQLException {
        FileConfiguration config = plugin.getConfig();

        HikariConfig hikariConfig = new HikariConfig();
        String jdbcUrl = "jdbc:mysql://" + config.getString("database.host") + ":" +
                config.getInt("database.port") + "/" + config.getString("database.name");
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getString("database.user"));
        hikariConfig.setPassword(config.getString("database.password"));
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setPoolName("HeneriaLobbyPool");

        this.dataSource = new HikariDataSource(hikariConfig);

        createTables();
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
