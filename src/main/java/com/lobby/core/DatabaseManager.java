package com.lobby.core;

import com.lobby.LobbyPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
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
        final String host = config.getString("database.host", "localhost");
        final int port = config.getInt("database.port", 3306);
        final String database = config.getString("database.database", "lobby");
        final String username = config.getString("database.username", "root");
        final String password = config.getString("database.password", "");

        try {
            final HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true", host, port, database));
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(config.getInt("database.pool.maximum_size", 10));
            hikariConfig.setMinimumIdle(config.getInt("database.pool.minimum_idle", 3));
            hikariConfig.setConnectionTimeout(config.getLong("database.pool.connection_timeout", 30000L));
            hikariConfig.setIdleTimeout(config.getLong("database.pool.idle_timeout", 600000L));
            hikariConfig.setMaxLifetime(config.getLong("database.pool.max_lifetime", 1800000L));
            hikariConfig.setPoolName("LobbyCore-MySQL");
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

            this.dataSource = new HikariDataSource(hikariConfig);

            try (Connection connection = dataSource.getConnection()) {
                plugin.getLogger().info("MySQL connection test successful");
            }

            this.databaseType = DatabaseType.MYSQL;
            return true;
        } catch (final Exception exception) {
            plugin.getLogger().log(Level.WARNING, "MySQL connection failed: " + exception.getMessage(), exception);
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
        try {
            plugin.getLogger().info("Creating/updating database tables...");

            createOrUpdatePlayersTable();

            executeSQL(getCreateStatsTableSQL());
            executeSQL(getCreateHologramsTableSQL());
            final String animationDefinition = databaseType == DatabaseType.MYSQL
                    ? "VARCHAR(32) DEFAULT 'NONE'"
                    : "TEXT DEFAULT 'NONE'";
            addColumnIfMissing("holograms", "animation", animationDefinition);
            executeSQL(getCreateNPCsTableSQL());
            executeSQL(getCreateShopTableSQL());
            executeSQL(getCreateTransactionsTableSQL());

            createTransactionIndexes();

            plugin.getLogger().info("All database tables created/updated successfully");
            return true;
        } catch (final Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Error creating database tables: " + exception.getMessage(), exception);
            return false;
        }
    }

    private void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private void executeSQL(final String sql) throws SQLException {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void createOrUpdatePlayersTable() throws SQLException {
        final String playersTableSql;
        if (databaseType == DatabaseType.MYSQL) {
            playersTableSql = """
                    CREATE TABLE IF NOT EXISTS players (
                        uuid VARCHAR(36) PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        coins BIGINT DEFAULT 1000,
                        first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        total_playtime BIGINT DEFAULT 0
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """;
        } else {
            playersTableSql = """
                    CREATE TABLE IF NOT EXISTS players (
                        uuid VARCHAR(36) PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        coins BIGINT DEFAULT 1000,
                        first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        total_playtime BIGINT DEFAULT 0
                    )
                    """;
        }

        executeSQL(playersTableSql);

        addColumnIfMissing("players", "tokens", "BIGINT DEFAULT 0");
        addColumnIfMissing("players", "discord_id", "VARCHAR(20) NULL");

        createPlayerIndexes();
    }

    private void addColumnIfMissing(final String table, final String columnName, final String definition) throws SQLException {
        final String alterSql = "ALTER TABLE " + table + " ADD COLUMN " + columnName + " " + definition;
        try {
            executeSQL(alterSql);
            plugin.getLogger().info("Added '" + columnName + "' column to " + table + " table");
        } catch (final SQLException exception) {
            if (isDuplicateColumnError(exception)) {
                plugin.getLogger().fine("Column '" + columnName + "' already exists in " + table + " table");
            } else {
                throw exception;
            }
        }
    }

    private boolean isDuplicateColumnError(final SQLException exception) {
        final String message = exception.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("duplicate column name");
    }

    private void createPlayerIndexes() {
        final String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_players_username ON players(username)",
                "CREATE INDEX IF NOT EXISTS idx_players_coins_desc ON players(coins DESC)",
                "CREATE INDEX IF NOT EXISTS idx_players_tokens_desc ON players(tokens DESC)"
        };

        for (final String indexSql : indexes) {
            try {
                executeSQL(indexSql);
            } catch (final SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to create player index: " + exception.getMessage(), exception);
            }
        }
    }

    private void createTransactionIndexes() {
        try {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_transactions_player_time ON transactions(player_uuid, timestamp)");
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to create transactions index: " + exception.getMessage(), exception);
        }
    }

    private String getCreateStatsTableSQL() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                    CREATE TABLE IF NOT EXISTS player_stats (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        server_name VARCHAR(50) NOT NULL,
                        playtime BIGINT DEFAULT 0,
                        kills INT DEFAULT 0,
                        deaths INT DEFAULT 0,
                        wins INT DEFAULT 0,
                        INDEX idx_player_server (player_uuid, server_name)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """;
        }

        return """
                CREATE TABLE IF NOT EXISTS player_stats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    server_name TEXT NOT NULL,
                    playtime INTEGER DEFAULT 0,
                    kills INTEGER DEFAULT 0,
                    deaths INTEGER DEFAULT 0,
                    wins INTEGER DEFAULT 0
                )
                """;
    }

    private String getCreateHologramsTableSQL() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                    CREATE TABLE IF NOT EXISTS holograms (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(50) UNIQUE NOT NULL,
                        world VARCHAR(50) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        `lines` TEXT NOT NULL,
                        visible BOOLEAN DEFAULT TRUE,
                        animation VARCHAR(32) DEFAULT 'NONE',
                        INDEX idx_world (world),
                        INDEX idx_visible (visible)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """;
        }

        return """
                CREATE TABLE IF NOT EXISTS holograms (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL,
                    world TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    lines TEXT NOT NULL,
                    visible INTEGER DEFAULT 1,
                    animation TEXT DEFAULT 'NONE'
                )
                """;
    }

    private String getCreateNPCsTableSQL() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                    CREATE TABLE IF NOT EXISTS npcs (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(50) UNIQUE NOT NULL,
                        display_name VARCHAR(100),
                        world VARCHAR(50) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        yaw FLOAT DEFAULT 0,
                        pitch FLOAT DEFAULT 0,
                        head_texture TEXT,
                        `actions` TEXT,
                        visible BOOLEAN DEFAULT TRUE,
                        INDEX idx_world (world),
                        INDEX idx_visible (visible)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """;
        }

        return """
                CREATE TABLE IF NOT EXISTS npcs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL,
                    display_name TEXT,
                    world TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL DEFAULT 0,
                    pitch REAL DEFAULT 0,
                    head_texture TEXT,
                    actions TEXT,
                    visible INTEGER DEFAULT 1
                )
                """;
    }

    private String getCreateShopTableSQL() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                    CREATE TABLE IF NOT EXISTS shop_items (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        category VARCHAR(50) NOT NULL,
                        item_name VARCHAR(100) NOT NULL,
                        description TEXT,
                        price_coins BIGINT DEFAULT 0,
                        price_tokens BIGINT DEFAULT 0,
                        commands TEXT,
                        enabled BOOLEAN DEFAULT TRUE,
                        INDEX idx_category (category),
                        INDEX idx_enabled (enabled)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """;
        }

        return """
                CREATE TABLE IF NOT EXISTS shop_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    category TEXT NOT NULL,
                    item_name TEXT NOT NULL,
                    description TEXT,
                    price_coins INTEGER DEFAULT 0,
                    price_tokens INTEGER DEFAULT 0,
                    commands TEXT,
                    enabled INTEGER DEFAULT 1
                )
                """;
    }

    private String getCreateTransactionsTableSQL() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                    CREATE TABLE IF NOT EXISTS transactions (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        transaction_type VARCHAR(32) NOT NULL,
                        amount BIGINT NOT NULL,
                        balance_after BIGINT NOT NULL,
                        reason VARCHAR(255),
                        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                    )
                    """;
        }

        return """
                CREATE TABLE IF NOT EXISTS transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    transaction_type TEXT NOT NULL,
                    amount BIGINT NOT NULL,
                    balance_after BIGINT NOT NULL,
                    reason TEXT,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
                """;
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
