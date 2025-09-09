package com.heneria.lobby.friends;

import com.heneria.lobby.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages friend relationships for players. Data is cached for online players
 * and persisted in the database.
 */
public class FriendManager {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Set<UUID>> cache = new ConcurrentHashMap<>();

    public FriendManager(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Loads the friends of the given player into the cache.
     */
    public void loadFriends(UUID uuid) {
        Set<UUID> friends = new HashSet<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT player_uuid, friend_uuid FROM player_friends WHERE status='ACCEPTED' AND (player_uuid=? OR friend_uuid=?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String a = rs.getString("player_uuid");
                    String b = rs.getString("friend_uuid");
                    UUID friend = uuid.toString().equals(a) ? UUID.fromString(b) : UUID.fromString(a);
                    friends.add(friend);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load friends for " + uuid + ": " + e.getMessage());
        }
        cache.put(uuid, friends);
    }

    public Set<UUID> getFriends(UUID uuid) {
        return cache.getOrDefault(uuid, Collections.emptySet());
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    public boolean areFriends(UUID a, UUID b) {
        return getFriends(a).contains(b);
    }

    public boolean hasPendingRequest(UUID from, UUID to) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id FROM player_friends WHERE status='PENDING' AND player_uuid=? AND friend_uuid=?")) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check pending request: " + e.getMessage());
        }
        return false;
    }

    public boolean hasIncomingRequest(UUID player, UUID requester) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id FROM player_friends WHERE status='PENDING' AND player_uuid=? AND friend_uuid=?")) {
            ps.setString(1, requester.toString());
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check incoming request: " + e.getMessage());
        }
        return false;
    }

    public void sendRequest(UUID from, UUID to) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO player_friends (player_uuid, friend_uuid, status, created_at) VALUES (?, ?, 'PENDING', ?)")) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to send friend request: " + e.getMessage());
        }
    }

    public void acceptRequest(UUID player, UUID requester) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE player_friends SET status='ACCEPTED' WHERE player_uuid=? AND friend_uuid=? AND status='PENDING'")) {
            ps.setString(1, requester.toString());
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to accept friend request: " + e.getMessage());
        }
        reload(player);
        reload(requester);
    }

    public void denyRequest(UUID player, UUID requester) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM player_friends WHERE player_uuid=? AND friend_uuid=? AND status='PENDING'")) {
            ps.setString(1, requester.toString());
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to deny friend request: " + e.getMessage());
        }
    }

    public void removeFriend(UUID player, UUID friend) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM player_friends WHERE status='ACCEPTED' AND ((player_uuid=? AND friend_uuid=?) OR (player_uuid=? AND friend_uuid=?))")) {
            ps.setString(1, player.toString());
            ps.setString(2, friend.toString());
            ps.setString(3, friend.toString());
            ps.setString(4, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove friend: " + e.getMessage());
        }
        reload(player);
        reload(friend);
    }

    public Set<UUID> getPendingRequests(UUID player) {
        Set<UUID> requests = new HashSet<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT player_uuid FROM player_friends WHERE status='PENDING' AND friend_uuid=?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    requests.add(UUID.fromString(rs.getString("player_uuid")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load pending requests: " + e.getMessage());
        }
        return requests;
    }

    private void reload(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            invalidate(uuid);
            loadFriends(uuid);
        });
    }
}
