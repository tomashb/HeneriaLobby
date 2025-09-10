package com.heneria.lobby.player;

import com.heneria.lobby.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides an interface for retrieving and storing player data without exposing
 * raw database operations.
 */
public class PlayerDataManager {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public PlayerData getPlayerData(UUID uuid) {
        return cache.get(uuid);
    }

    public PlayerData load(UUID uuid, String username) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            return data;
        }

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data = new PlayerData(
                                uuid,
                                rs.getString("username"),
                                rs.getLong("coins"),
                                rs.getTimestamp("first_join") != null ? rs.getTimestamp("first_join").toInstant() : Instant.now(),
                                rs.getTimestamp("last_seen") != null ? rs.getTimestamp("last_seen").toInstant() : Instant.now()
                        );
                        updateUsernameAndSeen(uuid, username, data.getLastSeen());
                    } else {
                        Instant now = Instant.now();
                        data = new PlayerData(uuid, username, 0L, now, now);
                        insert(data);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load player data for " + username + ": " + e.getMessage());
        }

        cache.put(uuid, data);
        return data;
    }

    /**
     * Loads player data by username without updating the last seen timestamp.
     * This is used for offline lookups where only the name is known.
     *
     * @param username the name of the player to load
     * @return the loaded {@link PlayerData} or {@code null} if not found
     */
    public PlayerData loadByUsername(String username) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM players WHERE username=?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    PlayerData data = new PlayerData(
                            uuid,
                            rs.getString("username"),
                            rs.getLong("coins"),
                            rs.getTimestamp("first_join") != null ? rs.getTimestamp("first_join").toInstant() : Instant.now(),
                            rs.getTimestamp("last_seen") != null ? rs.getTimestamp("last_seen").toInstant() : Instant.now()
                    );
                    cache.put(uuid, data);
                    return data;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load player data for " + username + ": " + e.getMessage());
        }
        return null;
    }

    public void save(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            return;
        }
        data.setLastSeen(Instant.now());
        update(data);
        cache.remove(uuid);
    }

    public void setCoins(UUID uuid, long coins) {
        PlayerData data = cache.computeIfPresent(uuid, (u, d) -> {
            d.setCoins(coins);
            return d;
        });
        if (data != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> update(data));
        }
    }

    private void insert(PlayerData data) throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO players (uuid, username, coins, first_join, last_seen) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getUsername());
            ps.setLong(3, data.getCoins());
            ps.setTimestamp(4, Timestamp.from(data.getFirstJoin()));
            ps.setTimestamp(5, Timestamp.from(data.getLastSeen()));
            ps.executeUpdate();
        }
    }

    private void update(PlayerData data) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE players SET username=?, coins=?, first_join=?, last_seen=? WHERE uuid=?")) {
            ps.setString(1, data.getUsername());
            ps.setLong(2, data.getCoins());
            ps.setTimestamp(3, Timestamp.from(data.getFirstJoin()));
            ps.setTimestamp(4, Timestamp.from(data.getLastSeen()));
            ps.setString(5, data.getUuid().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update data for " + data.getUsername() + ": " + e.getMessage());
        }
    }

    private void updateUsernameAndSeen(UUID uuid, String username, Instant lastSeen) throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE players SET username=?, last_seen=? WHERE uuid=?")) {
            ps.setString(1, username);
            ps.setTimestamp(2, Timestamp.from(lastSeen));
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        }
    }
}
