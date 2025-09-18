package com.lobby.core;

import com.lobby.LobbyPlugin;
import com.lobby.utils.LogUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
            createOrUpdateNPCsTable();

            executeSQL(getCreateStatsTableSQL());
            executeSQL(getCreateHologramsTableSQL());
            final String animationDefinition = databaseType == DatabaseType.MYSQL
                    ? "VARCHAR(32) DEFAULT 'NONE'"
                    : "TEXT DEFAULT 'NONE'";
            addColumnIfMissing("holograms", "animation", animationDefinition);
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
        final String createTableSql;
        if (databaseType == DatabaseType.MYSQL) {
            createTableSql = """
                    CREATE TABLE IF NOT EXISTS players (
                        uuid VARCHAR(36) PRIMARY KEY,
                        username VARCHAR(16) NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """;
        } else {
            createTableSql = """
                    CREATE TABLE IF NOT EXISTS players (
                        uuid VARCHAR(36) PRIMARY KEY,
                        username VARCHAR(16) NOT NULL
                    )
                    """;
        }

        executeSQL(createTableSql);
        plugin.getLogger().fine("Base players table created/verified");

        final boolean debugEnabled = plugin.getConfigManager() != null && plugin.getConfigManager().isDebugEnabled();
        if (debugEnabled) {
            debugTableStructure("players");
        }

        addColumnIfNotExists("players", "coins", "BIGINT DEFAULT 1000");
        addColumnIfNotExists("players", "tokens", "BIGINT DEFAULT 0");
        addColumnIfNotExists("players", "first_join", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        final String lastJoinDefinition = databaseType == DatabaseType.MYSQL
                ? "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                : "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
        addColumnIfNotExists("players", "last_join", lastJoinDefinition);
        addColumnIfNotExists("players", "total_playtime", "BIGINT DEFAULT 0");
        final String discordDefinition = databaseType == DatabaseType.MYSQL ? "VARCHAR(20) NULL" : "TEXT NULL";
        addColumnIfNotExists("players", "discord_id", discordDefinition);

        if (debugEnabled) {
            debugTableStructure("players");
        }

        createPlayerIndexes();
    }

    private void createOrUpdateNPCsTable() throws SQLException {
        if (databaseType != DatabaseType.MYSQL) {
            final String sqliteCreate = """
                    CREATE TABLE IF NOT EXISTS npcs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT UNIQUE,
                        display_name TEXT,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        yaw REAL DEFAULT 0,
                        pitch REAL DEFAULT 0,
                        head_texture TEXT,
                        armor_color TEXT,
                        actions TEXT,
                        visible INTEGER DEFAULT 1,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """;

            executeSQL(sqliteCreate);
            plugin.getLogger().fine("Created npcs table for SQLite");
            addColumnIfNotExists("npcs", "armor_color", "TEXT");
            createNPCIndexes();
            return;
        }

        try {
            executeSQL("DROP TABLE IF EXISTS npcs");
            LogUtils.info(plugin, "Dropped existing npcs table");
        } catch (final SQLException exception) {
            plugin.getLogger().fine("No existing npcs table to drop");
        }

        final String createTable = """
                CREATE TABLE npcs (
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
                    armor_color VARCHAR(7),
                    actions TEXT,
                    visible BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_world (world),
                    INDEX idx_visible (visible),
                    INDEX idx_name (name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=1
                """;

        executeSQL(createTable);
        LogUtils.info(plugin, "Created npcs table with proper AUTO_INCREMENT");
        addColumnIfNotExists("npcs", "armor_color", "VARCHAR(7)");
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

    private void addColumnIfNotExists(final String tableName, final String columnName, final String columnDefinition) throws SQLException {
        try {
            plugin.getLogger().fine("Checking column: " + tableName + '.' + columnName);

            boolean columnExists = false;
            try (Connection connection = getConnection()) {
                if (databaseType == DatabaseType.MYSQL) {
                    final String checkSql = """
                            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                            WHERE TABLE_SCHEMA = DATABASE()
                              AND TABLE_NAME = ?
                              AND COLUMN_NAME = ?
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(checkSql)) {
                        statement.setString(1, tableName);
                        statement.setString(2, columnName);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                columnExists = resultSet.getInt(1) > 0;
                            }
                        }
                    }
                } else {
                    final String pragmaSql = "PRAGMA table_info(" + tableName + ")";
                    try (Statement statement = connection.createStatement();
                         ResultSet resultSet = statement.executeQuery(pragmaSql)) {
                        while (resultSet.next()) {
                            final String existingColumn = resultSet.getString("name");
                            if (existingColumn != null && existingColumn.equalsIgnoreCase(columnName)) {
                                columnExists = true;
                                break;
                            }
                        }
                    }
                }
            } catch (final SQLException checkException) {
                plugin.getLogger().log(Level.WARNING, "Could not check column existence for " + tableName + '.' + columnName
                        + ": " + checkException.getMessage(), checkException);
            }

            if (columnExists) {
                plugin.getLogger().fine("Column '" + columnName + "' already exists in " + tableName + " table");
                return;
            }

            final String alterSql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
            try {
                executeSQL(alterSql);
                plugin.getLogger().info("Successfully added column '" + columnName + "' to table '" + tableName + "'");
            } catch (final SQLException exception) {
                final String message = exception.getMessage();
                final String lowerCaseMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
                if (lowerCaseMessage.contains("duplicate column")
                        || lowerCaseMessage.contains("already exists")
                        || lowerCaseMessage.contains(columnName.toLowerCase(Locale.ROOT))) {
                    plugin.getLogger().fine("Column '" + columnName + "' already exists in " + tableName + " table (detected via ALTER error)");
                } else {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to add column '" + columnName + "' to '" + tableName + "': " + exception.getMessage(), exception);
                    throw exception;
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Critical error managing column '" + columnName + "': " + exception.getMessage(), exception);
            throw new RuntimeException("Database schema update failed", exception);
        }
    }

    private void debugTableStructure(final String tableName) {
        try (Connection connection = getConnection()) {
            plugin.getLogger().info("=== Structure of table " + tableName + " ===");
            if (databaseType == DatabaseType.MYSQL) {
                final String describeSql = "DESCRIBE " + tableName;
                try (PreparedStatement statement = connection.prepareStatement(describeSql);
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        final String columnName = resultSet.getString("Field");
                        final String columnType = resultSet.getString("Type");
                        final String nullable = resultSet.getString("Null");
                        final String defaultValue = resultSet.getString("Default");
                        plugin.getLogger().info("Column: " + columnName + " | Type: " + columnType
                                + " | Null: " + nullable + " | Default: " + defaultValue);
                    }
                }
            } else {
                final String pragmaSql = "PRAGMA table_info(" + tableName + ")";
                try (Statement statement = connection.createStatement();
                     ResultSet resultSet = statement.executeQuery(pragmaSql)) {
                    while (resultSet.next()) {
                        final String columnName = resultSet.getString("name");
                        final String columnType = resultSet.getString("type");
                        final String nullable = resultSet.getInt("notnull") == 0 ? "YES" : "NO";
                        final String defaultValue = resultSet.getString("dflt_value");
                        plugin.getLogger().info("Column: " + columnName + " | Type: " + columnType
                                + " | Null: " + nullable + " | Default: " + defaultValue);
                    }
                }
            }
            plugin.getLogger().info("=== End of structure ===");
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not debug table structure for '" + tableName + "': " + exception.getMessage(), exception);
        }
    }

    private boolean isDuplicateColumnError(final SQLException exception) {
        final String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        final String lowerCaseMessage = message.toLowerCase(Locale.ROOT);
        return lowerCaseMessage.contains("duplicate column name") || lowerCaseMessage.contains("already exists");
    }

    private void createPlayerIndexes() {
        final String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_players_username ON players(username)",
                "CREATE INDEX IF NOT EXISTS idx_players_coins_desc ON players(coins DESC)",
                "CREATE INDEX IF NOT EXISTS idx_players_tokens_desc ON players(tokens DESC)",
                "CREATE INDEX IF NOT EXISTS idx_players_last_join ON players(last_join)"
        };

        for (final String indexSql : indexes) {
            try {
                executeSQL(indexSql);
            } catch (final SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to create player index: " + exception.getMessage(), exception);
            }
        }
    }

    private void createNPCIndexes() {
        final String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_npc_world ON npcs(world)",
                "CREATE INDEX IF NOT EXISTS idx_npc_visible ON npcs(visible)",
                "CREATE INDEX IF NOT EXISTS idx_npc_name ON npcs(name)"
        };

        for (final String indexSql : indexes) {
            try {
                executeSQL(indexSql);
            } catch (final SQLException exception) {
                plugin.getLogger().log(Level.FINE, "NPC index creation note: " + exception.getMessage(), exception);
            }
        }
        plugin.getLogger().fine("NPC indexes created/verified");
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
