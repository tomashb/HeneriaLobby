package com.lobby.core;

import com.lobby.LobbyPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;

public class DatabaseManager {

    private final LobbyPlugin plugin;
    private HikariDataSource dataSource;
    private DatabaseType databaseType = DatabaseType.SQLITE;

    public DatabaseManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        final FileConfiguration config = plugin.getConfig();
        final String configuredType = config.getString("database.type", "sqlite");
        databaseType = DatabaseType.from(configuredType);

        if (databaseType == DatabaseType.MYSQL) {
            if (setupMySql(config)) {
                plugin.getLogger().info("Connected to MySQL database.");
                return createTables();
            }
            plugin.getLogger().warning("MySQL connection failed, falling back to SQLite.");
        }

        databaseType = DatabaseType.SQLITE;
        if (setupSqlite()) {
            plugin.getLogger().info("Connected to SQLite database.");
            return createTables();
        }

        return false;
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database has not been initialized.");
        }
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    private boolean setupMySql(final FileConfiguration config) {
        final String host = Objects.requireNonNullElse(config.getString("database.host"), "localhost");
        final String database = Objects.requireNonNullElse(config.getString("database.database"), "lobby");
        final String username = Objects.requireNonNullElse(config.getString("database.username"), "root");
        final String password = Objects.requireNonNullElse(config.getString("database.password"), "");
        final int port = extractPort(host, 3306);
        final String hostname = extractHost(host);

        try {
            final HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:mysql://" + hostname + ":" + port + "/" + database + "?useSSL=false&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC");
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(Duration.ofSeconds(10).toMillis());
            hikariConfig.setMaxLifetime(Duration.ofMinutes(30).toMillis());
            hikariConfig.setPoolName("LobbyCore-MySQL");

            this.dataSource = new HikariDataSource(hikariConfig);
            this.databaseType = DatabaseType.MYSQL;
            return true;
        } catch (final Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to connect to MySQL database", exception);
            closeDataSource();
            return false;
        }
    }

    private boolean setupSqlite() {
        try {
            final File databaseFile = new File(plugin.getDataFolder(), "lobby.db");
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().severe("Failed to create plugin data folder for SQLite database.");
                return false;
            }

            final HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setMaximumPoolSize(1);
            hikariConfig.setMinimumIdle(1);
            hikariConfig.setConnectionTestQuery("SELECT 1");
            hikariConfig.setPoolName("LobbyCore-SQLite");

            this.dataSource = new HikariDataSource(hikariConfig);
            this.databaseType = DatabaseType.SQLITE;
            return true;
        } catch (final Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to set up SQLite database", exception);
            closeDataSource();
            return false;
        }
    }

    private boolean createTables() {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.addBatch("CREATE TABLE IF NOT EXISTS players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "username VARCHAR(16) NOT NULL, " +
                    "coins BIGINT DEFAULT 1000, " +
                    "tokens BIGINT DEFAULT 0, " +
                    "first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "total_playtime BIGINT DEFAULT 0, " +
                    "discord_id VARCHAR(20) NULL" +
                    ")");

            statement.addBatch("CREATE TABLE IF NOT EXISTS player_stats (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "stat_key VARCHAR(64) NOT NULL, " +
                    "stat_value BIGINT DEFAULT 0, " +
                    "PRIMARY KEY (uuid, stat_key)" +
                    ")");

            statement.addBatch("CREATE TABLE IF NOT EXISTS holograms (" +
                    "id VARCHAR(64) PRIMARY KEY, " +
                    "world VARCHAR(64) NOT NULL, " +
                    "x DOUBLE NOT NULL, " +
                    "y DOUBLE NOT NULL, " +
                    "z DOUBLE NOT NULL, " +
                    "lines TEXT NOT NULL" +
                    ")");

            statement.addBatch("CREATE TABLE IF NOT EXISTS npcs (" +
                    "id VARCHAR(64) PRIMARY KEY, " +
                    "world VARCHAR(64) NOT NULL, " +
                    "x DOUBLE NOT NULL, " +
                    "y DOUBLE NOT NULL, " +
                    "z DOUBLE NOT NULL, " +
                    "skin TEXT NULL" +
                    ")");

            statement.addBatch("CREATE TABLE IF NOT EXISTS shop_items (" +
                    "id VARCHAR(64) PRIMARY KEY, " +
                    "name VARCHAR(64) NOT NULL, " +
                    "price BIGINT NOT NULL, " +
                    "currency VARCHAR(16) NOT NULL, " +
                    "category VARCHAR(64) NOT NULL" +
                    ")");

            statement.addBatch("CREATE INDEX IF NOT EXISTS idx_players_coins_desc ON players(coins DESC)");
            statement.addBatch("CREATE INDEX IF NOT EXISTS idx_players_tokens_desc ON players(tokens DESC)");

            final String transactionsTable;
            if (databaseType == DatabaseType.MYSQL) {
                transactionsTable = "CREATE TABLE IF NOT EXISTS transactions (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "player_uuid VARCHAR(36) NOT NULL, " +
                        "transaction_type VARCHAR(32) NOT NULL, " +
                        "amount BIGINT NOT NULL, " +
                        "balance_after BIGINT NOT NULL, " +
                        "reason VARCHAR(255), " +
                        "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE" +
                        ")";
            } else {
                transactionsTable = "CREATE TABLE IF NOT EXISTS transactions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "player_uuid VARCHAR(36) NOT NULL, " +
                        "transaction_type TEXT NOT NULL, " +
                        "amount BIGINT NOT NULL, " +
                        "balance_after BIGINT NOT NULL, " +
                        "reason TEXT, " +
                        "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE" +
                        ")";
            }
            statement.addBatch(transactionsTable);
            statement.addBatch("CREATE INDEX IF NOT EXISTS idx_transactions_player_time ON transactions(player_uuid, timestamp)");

            statement.executeBatch();
            return true;
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", exception);
            return false;
        }
    }

    private void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private int extractPort(final String host, final int defaultPort) {
        if (host.contains(":")) {
            final String[] parts = host.split(":");
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                return defaultPort;
            }
        }
        return defaultPort;
    }

    private String extractHost(final String host) {
        if (host.contains(":")) {
            return host.split(":")[0];
        }
        return host;
    }

    public enum DatabaseType {
        MYSQL,
        SQLITE;

        public static DatabaseType from(final String value) {
            if (value == null) {
                return SQLITE;
            }
            try {
                return DatabaseType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return SQLITE;
            }
        }
    }
}
