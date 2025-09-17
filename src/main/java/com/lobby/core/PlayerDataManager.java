package com.lobby.core;

import com.lobby.LobbyPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerDataManager {

    private final LobbyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();
    private long startingCoins;
    private long startingTokens;

    public PlayerDataManager(final LobbyPlugin plugin, final DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        reload();
    }

    public void reload() {
        final FileConfiguration config = plugin.getConfig();
        startingCoins = config.getLong("economy.starting_coins", 1000L);
        startingTokens = config.getLong("economy.starting_tokens", 0L);
    }

    public void handlePlayerJoin(final Player player) {
        sessionStartTimes.put(player.getUniqueId(), System.currentTimeMillis());
        ensurePlayerExists(player.getUniqueId(), player.getName());
    }

    public void handlePlayerQuit(final Player player) {
        final long sessionStart = sessionStartTimes.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
        final long sessionDurationSeconds = (System.currentTimeMillis() - sessionStart) / 1000L;
        updatePlaytime(player.getUniqueId(), sessionDurationSeconds);
        sessionStartTimes.remove(player.getUniqueId());
    }

    public void ensurePlayerExists(final UUID uuid, final String username) {
        final DatabaseManager.DatabaseType type = databaseManager.getDatabaseType();
        final String query;
        if (type == DatabaseManager.DatabaseType.MYSQL) {
            query = "INSERT INTO players (uuid, username, coins, tokens, first_join, last_join) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                    "ON DUPLICATE KEY UPDATE username = VALUES(username), last_join = VALUES(last_join)";
        } else {
            query = "INSERT INTO players (uuid, username, coins, tokens, first_join, last_join) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                    "ON CONFLICT(uuid) DO UPDATE SET username = excluded.username, last_join = excluded.last_join";
        }

        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.setLong(3, startingCoins);
            statement.setLong(4, startingTokens);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to ensure player data exists", exception);
        }
    }

    public void updatePlaytime(final UUID uuid, final long sessionDurationSeconds) {
        final String query = "UPDATE players SET total_playtime = total_playtime + ?, last_join = CURRENT_TIMESTAMP WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, Math.max(sessionDurationSeconds, 0L));
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update playtime for " + uuid, exception);
        }
    }
}
