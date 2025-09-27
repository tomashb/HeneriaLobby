package com.lobby.friends.manager;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.core.DatabaseManager.DatabaseType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Handles creation, persistence and lookup of friend invitation codes.
 * Codes are cached in memory for fast lookups and synchronised with the
 * database to ensure uniqueness across the network.
 */
public class FriendCodeManager {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private static final int CODE_SECTION_LENGTH = 4;
    private static final int MAX_GENERATION_ATTEMPTS = 100;

    private final LobbyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, String> codeCache = new ConcurrentHashMap<>();

    public FriendCodeManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();

        createTable();
        loadCodesCache();
    }

    private void createTable() {
        final DatabaseType databaseType = databaseManager.getDatabaseType();
        final String createTableSql;
        if (databaseType == DatabaseType.MYSQL) {
            createTableSql = """
                    CREATE TABLE IF NOT EXISTS friend_codes (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        code VARCHAR(9) UNIQUE NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """;
        } else {
            createTableSql = """
                    CREATE TABLE IF NOT EXISTS friend_codes (
                        player_uuid TEXT PRIMARY KEY,
                        code TEXT UNIQUE NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                    """;
        }

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableSql);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_friend_code ON friend_codes(code)");
            plugin.getLogger().info("Table friend_codes créée avec succès");
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Erreur création table friend_codes", exception);
        }
    }

    public String generateUniqueCode(final UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }

        final String existing = getPlayerCode(playerUuid);
        if (existing != null) {
            return existing;
        }

        String newCode = null;
        int attempts = 0;

        while (attempts < MAX_GENERATION_ATTEMPTS) {
            newCode = generateRandomCode();
            if (!isCodeAlreadyUsed(newCode)) {
                break;
            }
            attempts++;
        }

        if (attempts >= MAX_GENERATION_ATTEMPTS) {
            plugin.getLogger().severe("Impossible de générer un code unique après " + MAX_GENERATION_ATTEMPTS + " tentatives !");
            return null;
        }

        if (saveFriendCode(playerUuid, newCode)) {
            codeCache.put(playerUuid, newCode);
            plugin.getLogger().info("Code d'ami généré pour " + playerUuid + ": " + newCode);
            return newCode;
        }

        return null;
    }

    private String generateRandomCode() {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final char[] buffer = new char[CODE_LENGTH + 1];

        for (int index = 0; index < CODE_SECTION_LENGTH; index++) {
            buffer[index] = CHARACTERS.charAt(random.nextInt(CHARACTERS.length()));
        }

        buffer[CODE_SECTION_LENGTH] = '-';

        for (int index = CODE_SECTION_LENGTH + 1; index < buffer.length; index++) {
            buffer[index] = CHARACTERS.charAt(random.nextInt(CHARACTERS.length()));
        }

        return new String(buffer);
    }

    private boolean isCodeAlreadyUsed(final String code) {
        final String query = "SELECT 1 FROM friend_codes WHERE code = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, normalize(code));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur vérification code: " + exception.getMessage());
            return true;
        }
    }

    private boolean saveFriendCode(final UUID playerUuid, final String code) {
        final DatabaseType databaseType = databaseManager.getDatabaseType();
        final String sql;
        if (databaseType == DatabaseType.MYSQL) {
            sql = "INSERT INTO friend_codes (player_uuid, code) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE code = VALUES(code), updated_at = CURRENT_TIMESTAMP";
        } else {
            sql = "INSERT INTO friend_codes (player_uuid, code) VALUES (?, ?) " +
                    "ON CONFLICT(player_uuid) DO UPDATE SET code = excluded.code, updated_at = CURRENT_TIMESTAMP";
        }

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, normalize(code));
            return statement.executeUpdate() > 0;
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur sauvegarde code: " + exception.getMessage());
            return false;
        }
    }

    public String getPlayerCode(final UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        final String cached = codeCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        final String query = "SELECT code FROM friend_codes WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final String code = resultSet.getString("code");
                    if (code != null) {
                        codeCache.put(playerUuid, code);
                    }
                    return code;
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur récupération code: " + exception.getMessage());
        }
        return null;
    }

    public UUID getPlayerByCode(final String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }

        final String normalized = normalize(code);
        if (!normalized.matches("[A-Z0-9]{4}-[A-Z0-9]{4}")) {
            return null;
        }

        final String query = "SELECT player_uuid FROM friend_codes WHERE code = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, normalized);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return UUID.fromString(resultSet.getString("player_uuid"));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur recherche par code: " + exception.getMessage());
        }
        return null;
    }

    private void loadCodesCache() {
        final String query = "SELECT player_uuid, code FROM friend_codes";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            int loadedCodes = 0;
            while (resultSet.next()) {
                final UUID uuid = UUID.fromString(resultSet.getString("player_uuid"));
                final String code = resultSet.getString("code");
                if (code != null) {
                    codeCache.put(uuid, code);
                    loadedCodes++;
                }
            }
            plugin.getLogger().info("Chargé " + loadedCodes + " codes d'amis en cache");
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur chargement codes: " + exception.getMessage());
        }
    }

    public String regenerateCode(final UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        codeCache.remove(playerUuid);

        final String query = "DELETE FROM friend_codes WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur suppression ancien code: " + exception.getMessage());
        }

        return generateUniqueCode(playerUuid);
    }

    public String getCachedCode(final UUID playerUuid) {
        return codeCache.get(playerUuid);
    }

    private String normalize(final String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
