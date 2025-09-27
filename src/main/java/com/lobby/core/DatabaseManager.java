package com.lobby.core;

import com.lobby.LobbyPlugin;
import com.lobby.utils.LogUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final LobbyPlugin plugin;
    private HikariDataSource dataSource;
    private DatabaseType databaseType = DatabaseType.SQLITE;
    private boolean enableForeignKeys = false;
    private boolean createIndexes = true;
    private boolean verifyStructure = true;
    private boolean debugDatabase = false;

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

    public String getPlayerName(final UUID playerUUID) {
        if (playerUUID == null) {
            return null;
        }

        final String[] possibleQueries = {
                "SELECT username FROM players WHERE player_uuid = ? ORDER BY last_seen DESC LIMIT 1",
                "SELECT name FROM player_data WHERE uuid = ? ORDER BY last_login DESC LIMIT 1",
                "SELECT player_name FROM lobby_players WHERE player_id = ? ORDER BY join_date DESC LIMIT 1"
        };

        for (final String query : possibleQueries) {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerUUID.toString());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        final String value = resultSet.getString(1);
                        if (value != null && !value.trim().isEmpty()) {
                            return value.trim();
                        }
                    }
                }
            } catch (final SQLException exception) {
                plugin.getLogger().fine("Requête échouée: " + query + " - " + exception.getMessage());
            }
        }

        plugin.getLogger().warning("Impossible de récupérer le nom pour UUID: " + playerUUID);
        return null;
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public boolean isForeignKeysEnabled() {
        return enableForeignKeys;
    }

    public void setForeignKeysEnabled(final boolean enabled) {
        this.enableForeignKeys = enabled;
        plugin.getLogger().info("Foreign keys " + (enableForeignKeys ? "enabled" : "disabled"));

        if (enableForeignKeys && databaseType == DatabaseType.MYSQL) {
            addAllForeignKeys();
            return;
        }

        if (enableForeignKeys) {
            plugin.getLogger().info("Foreign keys are only supported on MySQL databases. Current type: " + databaseType);
        }
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
        plugin.getLogger().info("Starting database initialization...");

        final FileConfiguration config = plugin.getConfig();
        enableForeignKeys = config.getBoolean("database.foreign_keys_enabled", false);
        createIndexes = config.getBoolean("database.create_indexes", true);
        verifyStructure = config.getBoolean("database.verify_structure", true);
        debugDatabase = config.getBoolean("database.debug", false);

        plugin.getLogger().info("Foreign keys " + (enableForeignKeys ? "enabled" : "disabled") + " (configuration)");
        plugin.getLogger().info("Index creation " + (createIndexes ? "enabled" : "disabled") + " (configuration)");
        plugin.getLogger().info("Structure verification " + (verifyStructure ? "enabled" : "disabled") + " (configuration)");

        if (debugDatabase) {
            diagnosePlayersTable();
        }

        try {
            createCoreTablesWithoutFK();
            plugin.getLogger().info("\u2713 Phase 1: Core tables created successfully");
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Critical error during database initialization:");
            plugin.getLogger().severe("Error: " + exception.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Stack trace:", exception);
            throw new RuntimeException("Database initialization failed", exception);
        }

        if (enableForeignKeys && databaseType == DatabaseType.MYSQL) {
            addAllForeignKeys();
        } else {
            plugin.getLogger().info("\u2713 Phase 2: Foreign keys disabled (configured choice)");
        }

        try {
            verifyTableIntegrity();
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Critical database error: " + exception.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Stack trace:", exception);
            throw new RuntimeException("Database integrity verification failed", exception);
        }

        plugin.getLogger().info("Database initialization completed successfully!");
        return true;
    }

    public UUID getPlayerUUID(final String playerName) {
        final String query = "SELECT uuid FROM players WHERE username = ? ORDER BY last_seen DESC LIMIT 1";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return UUID.fromString(resultSet.getString("uuid"));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur lors de la recherche UUID pour " + playerName + ": " + exception.getMessage());
        }

        return null;
    }

    private void createCoreTablesWithoutFK() throws SQLException {
        plugin.getLogger().info("Creating/updating core tables (without foreign keys)...");

        createOrUpdatePlayersTable();
        createOrUpdateNPCsTable();

        createOrUpdateStatsTables();
        executeSQL(getCreateHologramsTableSQL());
        final String animationDefinition = databaseType == DatabaseType.MYSQL
                ? "VARCHAR(32) DEFAULT 'NONE'"
                : "TEXT DEFAULT 'NONE'";
        addColumnIfMissing("holograms", "animation", animationDefinition);
        executeSQL(getCreateShopCategoriesTableSQL());
        executeSQL(getCreateShopItemsTableSQL());
        ensureShopTables();
        executeSQL(getCreateTransactionsTableSQL());

        createTransactionIndexes();

        createSocialTablesWithoutFK();
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
                    animation TEXT,
                    actions TEXT,
                    main_hand_item TEXT,
                    off_hand_item TEXT,
                    visible INTEGER DEFAULT 1,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """;

            executeSQL(sqliteCreate);
            plugin.getLogger().fine("Created npcs table for SQLite");
            addColumnIfNotExists("npcs", "armor_color", "TEXT");
            addColumnIfNotExists("npcs", "animation", "TEXT");
            addColumnIfNotExists("npcs", "main_hand_item", "TEXT");
            addColumnIfNotExists("npcs", "off_hand_item", "TEXT");
            createNPCIndexes();
            return;
        }

        final String createTable = """
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
                    armor_color VARCHAR(7) NULL DEFAULT NULL,
                    animation VARCHAR(50) NULL DEFAULT NULL,
                    actions TEXT,
                    main_hand_item TEXT,
                    off_hand_item TEXT,
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
        addColumnIfNotExists("npcs", "animation", "VARCHAR(50)");
        addColumnIfNotExists("npcs", "main_hand_item", "TEXT");
        addColumnIfNotExists("npcs", "off_hand_item", "TEXT");
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
        if (!createIndexes) {
            plugin.getLogger().fine("Player index creation skipped by configuration");
            return;
        }

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
        if (!createIndexes) {
            plugin.getLogger().fine("NPC index creation skipped by configuration");
            return;
        }

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
        if (!createIndexes) {
            plugin.getLogger().fine("Transaction index creation skipped by configuration");
            return;
        }

        try {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_transactions_player_time ON transactions(player_uuid, timestamp)");
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to create transactions index: " + exception.getMessage(), exception);
        }
    }

    private void createOrUpdateStatsTables() throws SQLException {
        executeSQL(getCreatePlayerGameStatsTableSQL());
        executeSQL(getCreatePlayerSettingsTableSQL());

        if (!createIndexes) {
            return;
        }

        try {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_player_game_stats_player ON player_game_stats(player_uuid)");
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to create idx_player_game_stats_player index: " + exception.getMessage(), exception);
        }

        try {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_player_game_stats_game ON player_game_stats(game_type)");
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to create idx_player_game_stats_game index: " + exception.getMessage(), exception);
        }

        try {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_player_settings_language ON player_settings(language)");
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.FINE,
                    "Failed to create idx_player_settings_language index: " + exception.getMessage(), exception);
        }
    }

    private String getCreatePlayerGameStatsTableSQL() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                    CREATE TABLE IF NOT EXISTS player_game_stats (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        game_type VARCHAR(50) NOT NULL,
                        games_played INT DEFAULT 0,
                        wins INT DEFAULT 0,
                        losses INT DEFAULT 0,
                        kills INT DEFAULT 0,
                        deaths INT DEFAULT 0,
                        special_stat_1 INT DEFAULT 0,
                        special_stat_2 INT DEFAULT 0,
                        playtime_seconds BIGINT DEFAULT 0,
                        last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY unique_player_game (player_uuid, game_type),
                        INDEX idx_player_game_stats_player (player_uuid),
                        INDEX idx_player_game_stats_game (game_type),
                        CONSTRAINT fk_player_game_stats_player FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """;
        }

        return """
                CREATE TABLE IF NOT EXISTS player_game_stats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    game_type TEXT NOT NULL,
                    games_played INTEGER DEFAULT 0,
                    wins INTEGER DEFAULT 0,
                    losses INTEGER DEFAULT 0,
                    kills INTEGER DEFAULT 0,
                    deaths INTEGER DEFAULT 0,
                    special_stat_1 INTEGER DEFAULT 0,
                    special_stat_2 INTEGER DEFAULT 0,
                    playtime_seconds INTEGER DEFAULT 0,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(player_uuid, game_type)
                )
                """;
    }

    private String getCreatePlayerSettingsTableSQL() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                    CREATE TABLE IF NOT EXISTS player_settings (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        private_messages BOOLEAN DEFAULT TRUE,
                        group_requests VARCHAR(20) DEFAULT 'EVERYONE',
                        visibility VARCHAR(20) DEFAULT 'EVERYONE',
                        ui_sounds BOOLEAN DEFAULT TRUE,
                        particles BOOLEAN DEFAULT TRUE,
                        music BOOLEAN DEFAULT FALSE,
                        clan_notifications BOOLEAN DEFAULT TRUE,
                        system_notifications BOOLEAN DEFAULT TRUE,
                        language VARCHAR(5) DEFAULT 'fr',
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        CONSTRAINT fk_player_settings_player FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """;
        }

        return """
                CREATE TABLE IF NOT EXISTS player_settings (
                    player_uuid TEXT PRIMARY KEY,
                    private_messages INTEGER DEFAULT 1,
                    group_requests TEXT DEFAULT 'EVERYONE',
                    visibility TEXT DEFAULT 'EVERYONE',
                    ui_sounds INTEGER DEFAULT 1,
                    particles INTEGER DEFAULT 1,
                    music INTEGER DEFAULT 0,
                    clan_notifications INTEGER DEFAULT 1,
                    system_notifications INTEGER DEFAULT 1,
                    language TEXT DEFAULT 'fr',
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
                        armor_color VARCHAR(7),
                        animation VARCHAR(50),
                        `actions` TEXT,
                        main_hand_item TEXT,
                        off_hand_item TEXT,
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
                    armor_color TEXT,
                    animation TEXT,
                    actions TEXT,
                    main_hand_item TEXT,
                    off_hand_item TEXT,
                    visible INTEGER DEFAULT 1
                )
                """;
    }

    private String getCreateShopCategoriesTableSQL() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                    CREATE TABLE IF NOT EXISTS shop_categories (
                        id VARCHAR(50) PRIMARY KEY,
                        display_name VARCHAR(100) NOT NULL,
                        description TEXT,
                        icon_material VARCHAR(50) DEFAULT 'CHEST',
                        sort_order INT DEFAULT 0,
                        visible BOOLEAN DEFAULT TRUE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """;
        }

        return """
                CREATE TABLE IF NOT EXISTS shop_categories (
                    id TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    description TEXT,
                    icon_material TEXT DEFAULT 'CHEST',
                    sort_order INTEGER DEFAULT 0,
                    visible INTEGER DEFAULT 1
                )
                """;
    }

    private String getCreateShopItemsTableSQL() {
        if (databaseType == DatabaseType.MYSQL) {
            return """
                    CREATE TABLE IF NOT EXISTS shop_items (
                        id VARCHAR(50) PRIMARY KEY,
                        category_id VARCHAR(50) NOT NULL,
                        display_name VARCHAR(100) NOT NULL,
                        description TEXT,
                        icon_material VARCHAR(50) DEFAULT 'PLAYER_HEAD',
                        icon_head_texture VARCHAR(200) DEFAULT 'hdb:35472',
                    price_coins BIGINT DEFAULT 0,
                    price_tokens BIGINT DEFAULT 0,
                    commands TEXT,
                    confirm_required BOOLEAN DEFAULT FALSE,
                    enabled BOOLEAN DEFAULT TRUE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
        }

        return """
                CREATE TABLE IF NOT EXISTS shop_items (
                    id TEXT PRIMARY KEY,
                    category_id TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    description TEXT,
                    icon_material TEXT DEFAULT 'PLAYER_HEAD',
                    icon_head_texture TEXT DEFAULT 'hdb:35472',
                    price_coins BIGINT DEFAULT 0,
                    price_tokens BIGINT DEFAULT 0,
                    commands TEXT,
                    confirm_required INTEGER DEFAULT 0,
                    enabled INTEGER DEFAULT 1
                )
                """;
    }

    private void ensureShopTables() throws SQLException {
        if (databaseType == DatabaseType.MYSQL) {
            addColumnIfNotExists("shop_items", "category_id", "VARCHAR(50) NOT NULL DEFAULT ''");
            addColumnIfNotExists("shop_items", "display_name", "VARCHAR(100) NOT NULL DEFAULT ''");
            addColumnIfNotExists("shop_items", "description", "TEXT");
            addColumnIfNotExists("shop_items", "icon_material", "VARCHAR(50) DEFAULT 'PLAYER_HEAD'");
            addColumnIfNotExists("shop_items", "icon_head_texture", "VARCHAR(200) DEFAULT 'hdb:35472'");
            addColumnIfNotExists("shop_items", "price_coins", "BIGINT DEFAULT 0");
            addColumnIfNotExists("shop_items", "price_tokens", "BIGINT DEFAULT 0");
            addColumnIfNotExists("shop_items", "commands", "TEXT");
            addColumnIfNotExists("shop_items", "confirm_required", "BOOLEAN DEFAULT FALSE");
            addColumnIfNotExists("shop_items", "enabled", "BOOLEAN DEFAULT TRUE");
        } else {
            addColumnIfNotExists("shop_items", "category_id", "TEXT DEFAULT ''");
            addColumnIfNotExists("shop_items", "display_name", "TEXT DEFAULT ''");
            addColumnIfNotExists("shop_items", "description", "TEXT");
            addColumnIfNotExists("shop_items", "icon_material", "TEXT DEFAULT 'PLAYER_HEAD'");
            addColumnIfNotExists("shop_items", "icon_head_texture", "TEXT DEFAULT 'hdb:35472'");
            addColumnIfNotExists("shop_items", "price_coins", "BIGINT DEFAULT 0");
            addColumnIfNotExists("shop_items", "price_tokens", "BIGINT DEFAULT 0");
            addColumnIfNotExists("shop_items", "commands", "TEXT");
            addColumnIfNotExists("shop_items", "confirm_required", "INTEGER DEFAULT 0");
            addColumnIfNotExists("shop_items", "enabled", "INTEGER DEFAULT 1");
        }

        if (columnExists("shop_items", "category") && columnExists("shop_items", "category_id")) {
            final String migrateCategory = "UPDATE shop_items SET category_id = category WHERE (category_id IS NULL OR category_id = '')";
            executeSQL(migrateCategory);
        }
        if (columnExists("shop_items", "item_name") && columnExists("shop_items", "display_name")) {
            final String migrateName = "UPDATE shop_items SET display_name = item_name WHERE (display_name IS NULL OR display_name = '')";
            executeSQL(migrateName);
        }
    }

    private boolean columnExists(final String table, final String column) {
        try (Connection connection = getConnection()) {
            if (databaseType == DatabaseType.MYSQL) {
                final String checkSql = """
                        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                        """;
                try (PreparedStatement statement = connection.prepareStatement(checkSql)) {
                    statement.setString(1, table);
                    statement.setString(2, column);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return resultSet.getInt(1) > 0;
                        }
                    }
                }
            } else {
                final String pragmaSql = "PRAGMA table_info(" + table + ")";
                try (Statement statement = connection.createStatement();
                     ResultSet resultSet = statement.executeQuery(pragmaSql)) {
                    while (resultSet.next()) {
                        final String existing = resultSet.getString("name");
                        if (existing != null && existing.equalsIgnoreCase(column)) {
                            return true;
                        }
                    }
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to check column existence for " + table + '.' + column, exception);
        }
        return false;
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
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
    }

    private void createSocialTablesWithoutFK() throws SQLException {
        createGroupSettingsTable();
        createGroupsTable();
        createGroupMembersTable();
        createGroupInvitationsTable();
        createClansTable();
        createClanMembersTable();
        createClanMemberPermissionsTable();
        createClanRanksTable();
        createClanInvitationsTable();
        createClanTransactionsTable();
    }


    private void createGroupSettingsTable() throws SQLException {
        if (databaseType == DatabaseType.MYSQL) {
            final String sql = """
                    CREATE TABLE IF NOT EXISTS group_settings (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        auto_accept_invites BOOLEAN DEFAULT FALSE NOT NULL,
                        preferred_gamemode VARCHAR(50) DEFAULT 'ANY' NOT NULL,
                        group_visibility ENUM('PUBLIC','FRIENDS_ONLY','INVITE_ONLY') DEFAULT 'PUBLIC' NOT NULL,
                        max_invitations INT DEFAULT 5 NOT NULL,
                        allow_notifications BOOLEAN DEFAULT TRUE NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
                        INDEX idx_group_settings_player_uuid (player_uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """;
            executeSQL(sql);
            addColumnIfNotExists("group_settings", "auto_accept_invites", "BOOLEAN DEFAULT FALSE NOT NULL");
            addColumnIfNotExists("group_settings", "preferred_gamemode", "VARCHAR(50) DEFAULT 'ANY' NOT NULL");
            addColumnIfNotExists("group_settings", "group_visibility",
                    "ENUM('PUBLIC','FRIENDS_ONLY','INVITE_ONLY') DEFAULT 'PUBLIC' NOT NULL");
            addColumnIfNotExists("group_settings", "max_invitations", "INT DEFAULT 5 NOT NULL");
            addColumnIfNotExists("group_settings", "allow_notifications", "BOOLEAN DEFAULT TRUE NOT NULL");
            addColumnIfNotExists("group_settings", "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL");
            addColumnIfNotExists("group_settings", "updated_at",
                    "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL");
        } else {
            final String sql = """
                    CREATE TABLE IF NOT EXISTS group_settings (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        auto_accept_invites BOOLEAN DEFAULT 0,
                        preferred_gamemode TEXT DEFAULT 'ANY',
                        group_visibility TEXT DEFAULT 'PUBLIC',
                        max_invitations INT DEFAULT 5,
                        allow_notifications BOOLEAN DEFAULT 1,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """;
            executeSQL(sql);
            if (createIndexes) {
                executeSQL("CREATE INDEX IF NOT EXISTS idx_group_settings_player_uuid ON group_settings(player_uuid)");
            }
            addColumnIfNotExists("group_settings", "auto_accept_invites", "BOOLEAN DEFAULT 0");
            addColumnIfNotExists("group_settings", "preferred_gamemode", "TEXT DEFAULT 'ANY'");
            addColumnIfNotExists("group_settings", "group_visibility", "TEXT DEFAULT 'PUBLIC'");
            addColumnIfNotExists("group_settings", "max_invitations", "INT DEFAULT 5");
            addColumnIfNotExists("group_settings", "allow_notifications", "BOOLEAN DEFAULT 1");
            addColumnIfNotExists("group_settings", "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            addColumnIfNotExists("group_settings", "updated_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        }

        if (columnExists("group_settings", "auto_accept") && columnExists("group_settings", "auto_accept_invites")) {
            executeSQL("UPDATE group_settings SET auto_accept_invites = auto_accept");
        }
        if (columnExists("group_settings", "visibility") && columnExists("group_settings", "group_visibility")) {
            executeSQL("UPDATE group_settings SET group_visibility = visibility");
        }
    }

    private void addAllForeignKeys() {
        if (!enableForeignKeys) {
            plugin.getLogger().info("Foreign keys are disabled - skipping constraint creation");
            return;
        }

        if (databaseType != DatabaseType.MYSQL) {
            plugin.getLogger().info("Foreign keys are only supported on MySQL - skipping constraint creation");
            return;
        }

        plugin.getLogger().info("Adding foreign key constraints...");

        addForeignKeyIfNotExists("groups_table", "fk_groups_leader",
                "leader_uuid", "players", "uuid", "CASCADE");

        addForeignKeyIfNotExists("group_members", "fk_group_members_group",
                "group_id", "groups_table", "id", "CASCADE");
        addForeignKeyIfNotExists("group_members", "fk_group_members_player",
                "player_uuid", "players", "uuid", "CASCADE");

        addForeignKeyIfNotExists("group_invitations", "fk_group_invitations_group",
                "group_id", "groups_table", "id", "CASCADE");
        addForeignKeyIfNotExists("group_invitations", "fk_group_invitations_inviter",
                "inviter_uuid", "players", "uuid", "CASCADE");
        addForeignKeyIfNotExists("group_invitations", "fk_group_invitations_invited",
                "invited_uuid", "players", "uuid", "CASCADE");

        addForeignKeyIfNotExists("clans", "fk_clans_leader",
                "leader_uuid", "players", "uuid", "CASCADE");

        addForeignKeyIfNotExists("clan_members", "fk_clan_members_clan",
                "clan_id", "clans", "id", "CASCADE");
        addForeignKeyIfNotExists("clan_members", "fk_clan_members_player",
                "player_uuid", "players", "uuid", "CASCADE");

        addForeignKeyIfNotExists("clan_ranks", "fk_clan_ranks_clan",
                "clan_id", "clans", "id", "CASCADE");

        addForeignKeyIfNotExists("clan_invitations", "fk_clan_invitations_clan",
                "clan_id", "clans", "id", "CASCADE");
        addForeignKeyIfNotExists("clan_invitations", "fk_clan_invitations_inviter",
                "inviter_uuid", "players", "uuid", "CASCADE");
        addForeignKeyIfNotExists("clan_invitations", "fk_clan_invitations_invited",
                "invited_uuid", "players", "uuid", "CASCADE");

        addForeignKeyIfNotExists("clan_transactions", "fk_clan_transactions_clan",
                "clan_id", "clans", "id", "CASCADE");
        addForeignKeyIfNotExists("clan_transactions", "fk_clan_transactions_player",
                "player_uuid", "players", "uuid", "CASCADE");

        addForeignKeyIfNotExists("shop_items", "fk_shop_items_category",
                "category_id", "shop_categories", "id", "CASCADE");

        addForeignKeyIfNotExists("transactions", "fk_transactions_player",
                "player_uuid", "players", "uuid", "CASCADE");

        plugin.getLogger().info("\u2713 Phase 2: Foreign key constraints added");
    }

    public void diagnosePlayersTable() {
        plugin.getLogger().info("=== DIAGNOSTIC TABLE PLAYERS ===");

        if (!tableExists("players")) {
            plugin.getLogger().warning("Table players does not exist!");
            plugin.getLogger().info("=== END DIAGNOSTIC ===");
            return;
        }

        try (Connection connection = getConnection()) {
            if (databaseType == DatabaseType.MYSQL) {
                final String engineQuery = """
                        SELECT ENGINE FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'players'
                        """;
                try (PreparedStatement statement = connection.prepareStatement(engineQuery);
                     ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        final String engine = resultSet.getString("ENGINE");
                        plugin.getLogger().info("Players table engine: " + engine);
                        if (engine != null && !"InnoDB".equalsIgnoreCase(engine)) {
                            plugin.getLogger().warning("Table players uses " + engine
                                    + " instead of InnoDB - Foreign Keys not supported!");
                        }
                    }
                }
            }

            final DatabaseMetaData metadata = connection.getMetaData();

            plugin.getLogger().info("Players table columns:");
            boolean hasColumns = false;
            try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, "players", "%")) {
                while (columns.next()) {
                    hasColumns = true;
                    final String columnName = columns.getString("COLUMN_NAME");
                    final String dataType = columns.getString("TYPE_NAME");
                    final String columnSize = columns.getString("COLUMN_SIZE");
                    final String isNullable = columns.getString("IS_NULLABLE");
                    plugin.getLogger().info(String.format(Locale.ROOT,
                            "  - %s: %s(%s) nullable:%s",
                            columnName, dataType, columnSize, isNullable));
                }
            }
            if (!hasColumns) {
                plugin.getLogger().info("  (no columns reported by metadata)");
            }

            plugin.getLogger().info("Players table indexes:");
            boolean hasIndexes = false;
            try (ResultSet indexes = metadata.getIndexInfo(connection.getCatalog(), null, "players", false, false)) {
                while (indexes.next()) {
                    final String indexName = indexes.getString("INDEX_NAME");
                    final String columnName = indexes.getString("COLUMN_NAME");
                    final boolean unique = !indexes.getBoolean("NON_UNIQUE");
                    if (indexName != null && columnName != null) {
                        hasIndexes = true;
                        plugin.getLogger().info(String.format(Locale.ROOT,
                                "  - %s on %s (unique: %s)", indexName, columnName, unique));
                    }
                }
            }
            if (!hasIndexes) {
                plugin.getLogger().info("  (no indexes found)");
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Diagnostic failed: " + exception.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Diagnostic stack trace:", exception);
        }

        plugin.getLogger().info("=== END DIAGNOSTIC ===");
    }

    public void recreatePlayersTable() throws SQLException {
        plugin.getLogger().warning("Recreating players table with proper structure...");

        final Map<String, PlayerBackupEntry> backup = backupPlayersData();

        executeSQL("DROP TABLE IF EXISTS players");
        createOrUpdatePlayersTable();
        restorePlayersData(backup);

        plugin.getLogger().info("\u2713 Players table recreated successfully");
    }

    private Map<String, PlayerBackupEntry> backupPlayersData() {
        final Map<String, PlayerBackupEntry> backup = new HashMap<>();

        if (!tableExists("players")) {
            plugin.getLogger().warning("Table players does not exist - skipping backup");
            return backup;
        }

        final String query = """
                SELECT uuid, username, coins, tokens, first_join, last_join, total_playtime, discord_id
                FROM players
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                final PlayerBackupEntry data = new PlayerBackupEntry();
                data.uuid = resultSet.getString("uuid");
                data.username = resultSet.getString("username");
                data.coins = resultSet.getLong("coins");
                data.tokens = resultSet.getLong("tokens");
                data.firstJoin = resultSet.getTimestamp("first_join");
                data.lastJoin = resultSet.getTimestamp("last_join");
                data.totalPlaytime = resultSet.getLong("total_playtime");
                data.discordId = resultSet.getString("discord_id");

                if (data.uuid != null) {
                    backup.put(data.uuid, data);
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().warning("Could not backup player data: " + exception.getMessage());
            plugin.getLogger().log(Level.FINE, "Player backup failure", exception);
            return backup;
        }

        plugin.getLogger().info("Backed up " + backup.size() + " player records");
        return backup;
    }

    private void restorePlayersData(final Map<String, PlayerBackupEntry> backup) {
        if (backup.isEmpty()) {
            plugin.getLogger().info("No player data to restore");
            return;
        }

        final String insertQuery = """
                INSERT INTO players (uuid, username, coins, tokens, first_join, last_join, total_playtime, discord_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(insertQuery)) {
            for (final PlayerBackupEntry data : backup.values()) {
                statement.setString(1, data.uuid);
                statement.setString(2, data.username);
                statement.setLong(3, data.coins);
                statement.setLong(4, data.tokens);
                statement.setTimestamp(5, data.firstJoin);
                statement.setTimestamp(6, data.lastJoin);
                statement.setLong(7, data.totalPlaytime);
                statement.setString(8, data.discordId);
                statement.addBatch();
            }

            final int[] results = statement.executeBatch();
            plugin.getLogger().info("Restored " + results.length + " player records");
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Failed to restore player data: " + exception.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Player data restore failure", exception);
        }
    }

    private static class PlayerBackupEntry {
        private String uuid;
        private String username;
        private long coins;
        private long tokens;
        private Timestamp firstJoin;
        private Timestamp lastJoin;
        private long totalPlaytime;
        private String discordId;
    }

    private void addForeignKeyIfNotExists(final String table,
                                          final String constraintName,
                                          final String column,
                                          final String referencedTable,
                                          final String referencedColumn,
                                          final String onDelete) {
        final String checkSql = """
                SELECT CONSTRAINT_NAME
                FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND CONSTRAINT_NAME = ?
                  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(checkSql)) {
            statement.setString(1, table);
            statement.setString(2, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    plugin.getLogger().fine("Foreign key " + constraintName + " already exists");
                    return;
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not verify foreign key '" + constraintName + "': " + exception.getMessage(), exception);
            return;
        }

        if (!tableExists(table) || !tableExists(referencedTable)) {
            plugin.getLogger().warning("Cannot create FK " + constraintName + ": missing table(s)");
            return;
        }

        final String alterSql = String.format(
                "ALTER TABLE %s ADD CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s (%s) ON DELETE %s ON UPDATE CASCADE",
                table, constraintName, column, referencedTable, referencedColumn, onDelete);

        try {
            executeSQL(alterSql);
            plugin.getLogger().info("\u2713 Added foreign key: " + constraintName);
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to add FK " + constraintName + ": " + exception.getMessage(), exception);
        }
    }

    private boolean tableExists(final String tableName) {
        try (Connection connection = getConnection()) {
            if (databaseType == DatabaseType.MYSQL) {
                final String sql = """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                        """;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, tableName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        return resultSet.next() && resultSet.getInt(1) > 0;
                    }
                }
            }

            final String sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tableName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.FINE,
                    "Table existence check failed for '" + tableName + "': " + exception.getMessage(), exception);
        }
        return false;
    }

    private void verifyTableIntegrity() throws SQLException {
        if (!verifyStructure) {
            plugin.getLogger().info("\u2713 Phase 3: Database integrity verification skipped (configuration)");
            return;
        }

        plugin.getLogger().info("Verifying database integrity...");

        final String[] requiredTables = {
                "players",
                "groups_table", "group_members", "group_invitations",
                "clans", "clan_members", "clan_ranks", "clan_invitations", "clan_transactions",
                "shop_categories", "shop_items", "transactions"
        };

        for (final String tableName : requiredTables) {
            if (!tableExists(tableName)) {
                throw new SQLException("Critical table missing: " + tableName);
            }
        }

        verifyTableStructure("players", "uuid", "username");
        verifyTableStructure("groups_table", "leader_uuid");
        verifyTableStructure("clans", "name", "tag");

        plugin.getLogger().info("\u2713 Phase 3: Database integrity verified");
    }

    private void verifyTableStructure(final String tableName, final String... requiredColumns) throws SQLException {
        try (Connection connection = getConnection()) {
            final DatabaseMetaData metadata = connection.getMetaData();
            final Set<String> existingColumns = new HashSet<>();

            try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, tableName, "%")) {
                while (columns.next()) {
                    final String columnName = columns.getString("COLUMN_NAME");
                    if (columnName != null) {
                        existingColumns.add(columnName.toLowerCase(Locale.ROOT));
                    }
                }
            }

            if (existingColumns.isEmpty()) {
                final String pragma = "PRAGMA table_info(" + tableName + ")";
                try (Statement statement = connection.createStatement();
                     ResultSet pragmaResult = statement.executeQuery(pragma)) {
                    while (pragmaResult.next()) {
                        final String columnName = pragmaResult.getString("name");
                        if (columnName != null) {
                            existingColumns.add(columnName.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }

            for (final String requiredColumn : requiredColumns) {
                if (!existingColumns.contains(requiredColumn.toLowerCase(Locale.ROOT))) {
                    throw new SQLException("Missing column '" + requiredColumn + "' in table '" + tableName + "'");
                }
            }
        }
    }

    public void dropAllTables() {
        plugin.getLogger().warning("Dropping all tables for clean reinstall...");

        final String[] tables = {
                "clan_invitations", "clan_ranks", "clan_members", "clans",
                "group_invitations", "group_members", "groups_table",
                "transactions",
                "shop_items", "shop_categories", "players"
        };

        if (databaseType == DatabaseType.MYSQL) {
            try {
                executeSQL("SET FOREIGN_KEY_CHECKS = 0");
            } catch (final SQLException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not disable foreign key checks: " + exception.getMessage(), exception);
            }
        }

        for (final String table : tables) {
            try {
                executeSQL("DROP TABLE IF EXISTS " + table);
                plugin.getLogger().info("Dropped table: " + table);
            } catch (final SQLException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not drop table '" + table + "': " + exception.getMessage(), exception);
            }
        }

        if (databaseType == DatabaseType.MYSQL) {
            try {
                executeSQL("SET FOREIGN_KEY_CHECKS = 1");
            } catch (final SQLException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not enable foreign key checks: " + exception.getMessage(), exception);
            }
        }
    }

    private void createGroupsTable() throws SQLException {
        if (databaseType == DatabaseType.MYSQL) {
            final String sql = """
                    CREATE TABLE IF NOT EXISTS groups_table (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        leader_uuid VARCHAR(36) NOT NULL,
                        name VARCHAR(50) NULL,
                        description TEXT NULL,
                        max_members INT DEFAULT 8 NOT NULL,
                        is_public BOOLEAN DEFAULT FALSE NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        disbanded_at TIMESTAMP NULL,
                        INDEX idx_groups_leader_uuid (leader_uuid),
                        INDEX idx_groups_created_at (created_at),
                        INDEX idx_groups_is_public (is_public)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """;
            executeSQL(sql);
            addColumnIfNotExists("groups_table", "description", "TEXT NULL");
            addColumnIfNotExists("groups_table", "is_public", "BOOLEAN DEFAULT FALSE NOT NULL");
            addColumnIfNotExists("groups_table", "disbanded_at", "TIMESTAMP NULL");
            return;
        }

        final String sql = """
                CREATE TABLE IF NOT EXISTS groups_table (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    leader_uuid VARCHAR(36) NOT NULL,
                    name TEXT,
                    description TEXT,
                    max_members INTEGER DEFAULT 8,
                    is_public BOOLEAN DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    disbanded_at TIMESTAMP NULL
                )
                """;
        executeSQL(sql);
        if (createIndexes) {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_groups_leader_uuid ON groups_table(leader_uuid)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_groups_created_at ON groups_table(created_at)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_groups_is_public ON groups_table(is_public)");
        }
        addColumnIfNotExists("groups_table", "description", "TEXT");
        addColumnIfNotExists("groups_table", "is_public", "BOOLEAN DEFAULT 0");
        addColumnIfNotExists("groups_table", "disbanded_at", "TIMESTAMP NULL");
    }

    private void createGroupMembersTable() throws SQLException {
        final String roleDefinition = databaseType == DatabaseType.MYSQL
                ? "ENUM('LEADER','MODERATOR','MEMBER') DEFAULT 'MEMBER' NOT NULL"
                : "TEXT DEFAULT 'MEMBER'";

        if (databaseType == DatabaseType.MYSQL) {
            final String sql = String.format("""
                    CREATE TABLE IF NOT EXISTS group_members (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        group_id INT NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        role %s,
                        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        UNIQUE KEY unique_group_member (group_id, player_uuid),
                        INDEX idx_group_members_group_id (group_id),
                        INDEX idx_group_members_player_uuid (player_uuid),
                        INDEX idx_group_members_role (role)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """, roleDefinition);
            executeSQL(sql);
            addColumnIfNotExists("group_members", "joined_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL");
            return;
        }

        final String sql = String.format("""
                CREATE TABLE IF NOT EXISTS group_members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_id INTEGER NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    role %s,
                    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (group_id, player_uuid)
                )
                """, roleDefinition);
        executeSQL(sql);
        if (createIndexes) {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_group_members_group_id ON group_members(group_id)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_group_members_player_uuid ON group_members(player_uuid)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_group_members_role ON group_members(role)");
        }
        addColumnIfNotExists("group_members", "joined_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
    }

    private void createGroupInvitationsTable() throws SQLException {
        final String statusDefinition = databaseType == DatabaseType.MYSQL
                ? "ENUM('PENDING','ACCEPTED','DECLINED','EXPIRED') DEFAULT 'PENDING' NOT NULL"
                : "TEXT DEFAULT 'PENDING'";

        if (databaseType == DatabaseType.MYSQL) {
            final String sql = String.format("""
                    CREATE TABLE IF NOT EXISTS group_invitations (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        group_id INT NOT NULL,
                        inviter_uuid VARCHAR(36) NOT NULL,
                        invited_uuid VARCHAR(36) NOT NULL,
                        message TEXT NULL,
                        status %s,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        expires_at TIMESTAMP NOT NULL,
                        INDEX idx_group_invitations_group_id (group_id),
                        INDEX idx_group_invitations_inviter_uuid (inviter_uuid),
                        INDEX idx_group_invitations_invited_uuid (invited_uuid),
                        INDEX idx_group_invitations_status (status),
                        INDEX idx_group_invitations_expires_at (expires_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """, statusDefinition);
            executeSQL(sql);
            addColumnIfNotExists("group_invitations", "message", "TEXT NULL");
            addColumnIfNotExists("group_invitations", "expires_at", "TIMESTAMP NOT NULL");
            return;
        }

        final String sql = String.format("""
                CREATE TABLE IF NOT EXISTS group_invitations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_id INTEGER NOT NULL,
                    inviter_uuid VARCHAR(36) NOT NULL,
                    invited_uuid VARCHAR(36) NOT NULL,
                    message TEXT,
                    status %s,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP NOT NULL
                )
                """, statusDefinition);
        executeSQL(sql);
        if (createIndexes) {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_group_invitations_group_id ON group_invitations(group_id)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_group_invitations_inviter_uuid ON group_invitations(inviter_uuid)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_group_invitations_invited_uuid ON group_invitations(invited_uuid)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_group_invitations_status ON group_invitations(status)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_group_invitations_expires_at ON group_invitations(expires_at)");
        }
        addColumnIfNotExists("group_invitations", "message", "TEXT");
        addColumnIfNotExists("group_invitations", "expires_at", "TIMESTAMP NOT NULL");
    }

    private void createClansTable() throws SQLException {
        if (databaseType == DatabaseType.MYSQL) {
            final String sql = """
                    CREATE TABLE IF NOT EXISTS clans (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(50) UNIQUE NOT NULL,
                        tag VARCHAR(6) UNIQUE NOT NULL,
                        description TEXT NULL,
                        leader_uuid VARCHAR(36) NOT NULL,
                        max_members INT DEFAULT 50 NOT NULL,
                        points INT DEFAULT 0 NOT NULL,
                        level INT DEFAULT 1 NOT NULL,
                        bank_coins BIGINT DEFAULT 0 NOT NULL,
                        bank_tokens BIGINT DEFAULT 0 NOT NULL,
                        is_public BOOLEAN DEFAULT TRUE NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        disbanded_at TIMESTAMP NULL,
                        INDEX idx_clans_leader_uuid (leader_uuid),
                        INDEX idx_clans_points (points),
                        INDEX idx_clans_level (level),
                        INDEX idx_clans_is_public (is_public)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """;
            executeSQL(sql);
            addColumnIfNotExists("clans", "bank_tokens", "BIGINT DEFAULT 0 NOT NULL");
            addColumnIfNotExists("clans", "is_public", "BOOLEAN DEFAULT TRUE NOT NULL");
            addColumnIfNotExists("clans", "disbanded_at", "TIMESTAMP NULL");
            return;
        }

        final String sql = """
                CREATE TABLE IF NOT EXISTS clans (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL,
                    tag TEXT UNIQUE NOT NULL,
                    description TEXT,
                    leader_uuid VARCHAR(36) NOT NULL,
                    max_members INTEGER DEFAULT 50,
                    points INTEGER DEFAULT 0,
                    level INTEGER DEFAULT 1,
                    bank_coins BIGINT DEFAULT 0,
                    bank_tokens BIGINT DEFAULT 0,
                    is_public BOOLEAN DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    disbanded_at TIMESTAMP NULL
                )
                """;
        executeSQL(sql);
        if (createIndexes) {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clans_leader_uuid ON clans(leader_uuid)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clans_points ON clans(points)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clans_level ON clans(level)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clans_is_public ON clans(is_public)");
        }
        addColumnIfNotExists("clans", "bank_tokens", "BIGINT DEFAULT 0");
        addColumnIfNotExists("clans", "is_public", "BOOLEAN DEFAULT 1");
        addColumnIfNotExists("clans", "disbanded_at", "TIMESTAMP NULL");
    }

    private void createClanMembersTable() throws SQLException {
        if (databaseType == DatabaseType.MYSQL) {
            final String sql = """
                    CREATE TABLE IF NOT EXISTS clan_members (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        clan_id INT NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        rank_name VARCHAR(30) DEFAULT 'Membre' NOT NULL,
                        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        last_contribution TIMESTAMP NULL,
                        total_contributions BIGINT DEFAULT 0 NOT NULL,
                        UNIQUE KEY unique_clan_member (clan_id, player_uuid),
                        INDEX idx_clan_members_clan_id (clan_id),
                        INDEX idx_clan_members_player_uuid (player_uuid),
                        INDEX idx_clan_members_rank_name (rank_name),
                        INDEX idx_clan_members_total_contributions (total_contributions)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """;
            executeSQL(sql);
            addColumnIfNotExists("clan_members", "last_contribution", "TIMESTAMP NULL");
            addColumnIfNotExists("clan_members", "total_contributions", "BIGINT DEFAULT 0 NOT NULL");
            return;
        }

        final String sql = """
                CREATE TABLE IF NOT EXISTS clan_members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    clan_id INTEGER NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    rank_name TEXT DEFAULT 'Membre',
                    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_contribution TIMESTAMP NULL,
                    total_contributions BIGINT DEFAULT 0,
                    UNIQUE (clan_id, player_uuid)
                )
                """;
        executeSQL(sql);
        if (createIndexes) {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_members_clan_id ON clan_members(clan_id)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_members_player_uuid ON clan_members(player_uuid)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_members_rank_name ON clan_members(rank_name)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_members_total_contributions ON clan_members(total_contributions)");
        }
        addColumnIfNotExists("clan_members", "last_contribution", "TIMESTAMP NULL");
        addColumnIfNotExists("clan_members", "total_contributions", "BIGINT DEFAULT 0");
    }

    private void createClanMemberPermissionsTable() throws SQLException {
        if (databaseType == DatabaseType.MYSQL) {
            final String sql = """
                    CREATE TABLE IF NOT EXISTS clan_member_permissions (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        clan_id INT NOT NULL,
                        member_uuid VARCHAR(36) NOT NULL,
                        permission_node VARCHAR(255) NOT NULL,
                        INDEX idx_clan_member_permissions_clan_id (clan_id),
                        INDEX idx_clan_member_permissions_member_uuid (member_uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """;
            executeSQL(sql);
            return;
        }

        final String sql = """
                CREATE TABLE IF NOT EXISTS clan_member_permissions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    clan_id INTEGER NOT NULL,
                    member_uuid VARCHAR(36) NOT NULL,
                    permission_node TEXT NOT NULL
                )
                """;
        executeSQL(sql);
        if (createIndexes) {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_member_permissions_clan_id ON clan_member_permissions(clan_id)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_member_permissions_member_uuid ON clan_member_permissions(member_uuid)");
        }
    }

    private void createClanRanksTable() throws SQLException {
        if (databaseType == DatabaseType.MYSQL) {
            final String sql = """
                    CREATE TABLE IF NOT EXISTS clan_ranks (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        clan_id INT NOT NULL,
                        name VARCHAR(30) NOT NULL,
                        display_name VARCHAR(50) NOT NULL DEFAULT 'Membre',
                        priority INT DEFAULT 0 NOT NULL,
                        permissions JSON NULL,
                        can_promote BOOLEAN DEFAULT FALSE NOT NULL,
                        can_demote BOOLEAN DEFAULT FALSE NOT NULL,
                        can_manage_ranks BOOLEAN DEFAULT FALSE NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY unique_clan_rank (clan_id, name),
                        INDEX idx_clan_ranks_clan_id (clan_id),
                        INDEX idx_clan_ranks_priority (priority)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """;
            executeSQL(sql);
            addColumnIfNotExists("clan_ranks", "display_name", "VARCHAR(50) NOT NULL DEFAULT 'Membre'");
            addColumnIfNotExists("clan_ranks", "permissions", "JSON NULL");
            addColumnIfNotExists("clan_ranks", "can_promote", "BOOLEAN DEFAULT FALSE NOT NULL");
            addColumnIfNotExists("clan_ranks", "can_demote", "BOOLEAN DEFAULT FALSE NOT NULL");
            addColumnIfNotExists("clan_ranks", "can_manage_ranks", "BOOLEAN DEFAULT FALSE NOT NULL");
            addColumnIfNotExists("clan_ranks", "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            ensureClanRankDisplayNames();
            return;
        }

        final String sql = """
                CREATE TABLE IF NOT EXISTS clan_ranks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    clan_id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    display_name TEXT NOT NULL DEFAULT 'Membre',
                    priority INTEGER DEFAULT 0,
                    permissions TEXT,
                    can_promote BOOLEAN DEFAULT 0,
                    can_demote BOOLEAN DEFAULT 0,
                    can_manage_ranks BOOLEAN DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (clan_id, name)
                )
                """;
        executeSQL(sql);
        if (createIndexes) {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_ranks_clan_id ON clan_ranks(clan_id)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_ranks_priority ON clan_ranks(priority)");
        }
        addColumnIfNotExists("clan_ranks", "display_name", "TEXT NOT NULL DEFAULT 'Membre'");
        addColumnIfNotExists("clan_ranks", "permissions", "TEXT");
        addColumnIfNotExists("clan_ranks", "can_promote", "BOOLEAN DEFAULT 0");
        addColumnIfNotExists("clan_ranks", "can_demote", "BOOLEAN DEFAULT 0");
        addColumnIfNotExists("clan_ranks", "can_manage_ranks", "BOOLEAN DEFAULT 0");
        addColumnIfNotExists("clan_ranks", "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        ensureClanRankDisplayNames();
    }

    private void ensureClanRankDisplayNames() {
        try {
            executeSQL("UPDATE clan_ranks SET display_name = name WHERE display_name IS NULL OR TRIM(display_name) = ''");
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to backfill clan rank display names: " + exception.getMessage(), exception);
        }

        if (databaseType != DatabaseType.MYSQL) {
            return;
        }

        final String alterSql = "ALTER TABLE clan_ranks MODIFY COLUMN display_name VARCHAR(50) NOT NULL DEFAULT 'Membre'";
        try {
            executeSQL(alterSql);
        } catch (final SQLException exception) {
            plugin.getLogger().fine("Skipping clan_ranks display_name default enforcement: " + exception.getMessage());
        }
    }

    private void createClanInvitationsTable() throws SQLException {
        final String statusDefinition = databaseType == DatabaseType.MYSQL
                ? "ENUM('PENDING','ACCEPTED','DECLINED','EXPIRED') DEFAULT 'PENDING' NOT NULL"
                : "TEXT DEFAULT 'PENDING'";

        if (databaseType == DatabaseType.MYSQL) {
            final String sql = String.format("""
                    CREATE TABLE IF NOT EXISTS clan_invitations (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        clan_id INT NOT NULL,
                        inviter_uuid VARCHAR(36) NOT NULL,
                        invited_uuid VARCHAR(36) NOT NULL,
                        message TEXT NULL,
                        status %s,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        expires_at TIMESTAMP NOT NULL,
                        INDEX idx_clan_invitations_clan_id (clan_id),
                        INDEX idx_clan_invitations_inviter_uuid (inviter_uuid),
                        INDEX idx_clan_invitations_invited_uuid (invited_uuid),
                        INDEX idx_clan_invitations_status (status),
                        INDEX idx_clan_invitations_expires_at (expires_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """, statusDefinition);
            executeSQL(sql);
            addColumnIfNotExists("clan_invitations", "message", "TEXT NULL");
            addColumnIfNotExists("clan_invitations", "expires_at", "TIMESTAMP NOT NULL");
            return;
        }

        final String sql = String.format("""
                CREATE TABLE IF NOT EXISTS clan_invitations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    clan_id INTEGER NOT NULL,
                    inviter_uuid VARCHAR(36) NOT NULL,
                    invited_uuid VARCHAR(36) NOT NULL,
                    message TEXT,
                    status %s,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP NOT NULL
                )
                """, statusDefinition);
        executeSQL(sql);
        if (createIndexes) {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_invitations_clan_id ON clan_invitations(clan_id)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_invitations_inviter_uuid ON clan_invitations(inviter_uuid)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_invitations_invited_uuid ON clan_invitations(invited_uuid)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_invitations_status ON clan_invitations(status)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_invitations_expires_at ON clan_invitations(expires_at)");
        }
        addColumnIfNotExists("clan_invitations", "message", "TEXT");
        addColumnIfNotExists("clan_invitations", "expires_at", "TIMESTAMP NOT NULL");
    }

    private void createClanTransactionsTable() throws SQLException {
        if (databaseType == DatabaseType.MYSQL) {
            final String sql = """
                    CREATE TABLE IF NOT EXISTS clan_transactions (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        clan_id INT NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        transaction_type VARCHAR(16) NOT NULL,
                        amount BIGINT NOT NULL,
                        balance_after BIGINT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        INDEX idx_clan_transactions_clan_id (clan_id),
                        INDEX idx_clan_transactions_player_uuid (player_uuid),
                        INDEX idx_clan_transactions_type (transaction_type)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """;
            executeSQL(sql);
            return;
        }

        final String sql = """
                CREATE TABLE IF NOT EXISTS clan_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    clan_id INTEGER NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    transaction_type TEXT NOT NULL,
                    amount BIGINT NOT NULL,
                    balance_after BIGINT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
        executeSQL(sql);
        if (createIndexes) {
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_transactions_clan_id ON clan_transactions(clan_id)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_transactions_player_uuid ON clan_transactions(player_uuid)");
            executeSQL("CREATE INDEX IF NOT EXISTS idx_clan_transactions_type ON clan_transactions(transaction_type)");
        }
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
