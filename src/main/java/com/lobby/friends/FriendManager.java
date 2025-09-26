package com.lobby.friends;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.core.DatabaseManager.DatabaseType;
import com.lobby.utils.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Central access point for all friend related persistence logic.
 */
public class FriendManager {

    private static final String STATUS_ACCEPTED = "ACCEPTED";

    private final LobbyPlugin plugin;
    private final DatabaseManager databaseManager;

    public FriendManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public FriendMenuData loadMenuData(final UUID playerUuid, final Set<UUID> onlinePlayersSnapshot) {
        final List<FriendEntry> friends = loadFriends(playerUuid, onlinePlayersSnapshot);
        final List<FriendRequestEntry> requests = loadIncomingRequests(playerUuid);
        return new FriendMenuData(friends, requests);
    }

    public List<FriendEntry> loadFriends(final UUID playerUuid, final Set<UUID> onlinePlayersSnapshot) {
        if (playerUuid == null) {
            return List.of();
        }
        final Set<UUID> onlinePlayers = onlinePlayersSnapshot == null ? Set.of() : new HashSet<>(onlinePlayersSnapshot);
        final List<FriendEntry> entries = new ArrayList<>();
        final String query = """
                SELECT f.friend_uuid, f.created_at, f.is_favorite, p.username
                FROM friends f
                LEFT JOIN players p ON p.uuid = f.friend_uuid
                WHERE f.player_uuid = ? AND f.status = 'ACCEPTED'
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID friendUuid = UUID.fromString(resultSet.getString("friend_uuid"));
                    final String name = optionalString(resultSet.getString("username"))
                            .orElseGet(() -> resolveOfflineName(friendUuid));
                    final boolean favorite = resultSet.getBoolean("is_favorite");
                    final Timestamp created = resultSet.getTimestamp("created_at");
                    final Instant since = created == null ? Instant.EPOCH : created.toInstant();
                    final boolean online = onlinePlayers.contains(friendUuid);
                    entries.add(new FriendEntry(friendUuid, name, online, favorite, since));
                }
            }
        } catch (final SQLException exception) {
            LogUtils.warning(plugin, "Failed to load friends for " + playerUuid + ": " + exception.getMessage());
        }

        entries.sort(Comparator
                .comparing(FriendEntry::favorite).reversed()
                .thenComparing(FriendEntry::online).reversed()
                .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        return List.copyOf(entries);
    }

    public List<FriendRequestEntry> loadIncomingRequests(final UUID playerUuid) {
        if (playerUuid == null) {
            return List.of();
        }
        final List<FriendRequestEntry> requests = new ArrayList<>();
        final String query = """
                SELECT r.sender_uuid, r.created_at, p.username
                FROM friend_requests r
                LEFT JOIN players p ON p.uuid = r.sender_uuid
                WHERE r.target_uuid = ?
                ORDER BY r.created_at DESC
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID senderUuid = UUID.fromString(resultSet.getString("sender_uuid"));
                    final String name = optionalString(resultSet.getString("username"))
                            .orElseGet(() -> resolveOfflineName(senderUuid));
                    final Timestamp created = resultSet.getTimestamp("created_at");
                    final Instant createdAt = created == null ? Instant.now() : created.toInstant();
                    requests.add(new FriendRequestEntry(senderUuid, name, createdAt));
                }
            }
        } catch (final SQLException exception) {
            LogUtils.warning(plugin, "Failed to load friend requests for " + playerUuid + ": "
                    + exception.getMessage());
        }
        return List.copyOf(requests);
    }

    public boolean toggleFavorite(final UUID playerUuid, final UUID friendUuid) {
        if (playerUuid == null || friendUuid == null) {
            return false;
        }
        final String select = "SELECT is_favorite FROM friends WHERE player_uuid = ? AND friend_uuid = ? AND status = 'ACCEPTED'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement selectStatement = connection.prepareStatement(select)) {
            selectStatement.setString(1, playerUuid.toString());
            selectStatement.setString(2, friendUuid.toString());
            final boolean current;
            try (ResultSet resultSet = selectStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }
                current = resultSet.getBoolean("is_favorite");
            }
            final String update = "UPDATE friends SET is_favorite = ? WHERE player_uuid = ? AND friend_uuid = ?";
            try (PreparedStatement updateStatement = connection.prepareStatement(update)) {
                updateStatement.setBoolean(1, !current);
                updateStatement.setString(2, playerUuid.toString());
                updateStatement.setString(3, friendUuid.toString());
                return updateStatement.executeUpdate() > 0;
            }
        } catch (final SQLException exception) {
            LogUtils.warning(plugin, "Failed to toggle favorite for " + playerUuid + ": " + exception.getMessage());
            return false;
        }
    }

    public boolean acceptRequest(final UUID playerUuid, final UUID senderUuid) {
        if (playerUuid == null || senderUuid == null) {
            return false;
        }
        try (Connection connection = databaseManager.getConnection()) {
            final boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteRequest(connection, senderUuid, playerUuid);
                upsertFriend(connection, senderUuid, playerUuid);
                upsertFriend(connection, playerUuid, senderUuid);
                connection.commit();
                connection.setAutoCommit(previousAutoCommit);
                return true;
            } catch (final SQLException exception) {
                connection.rollback();
                connection.setAutoCommit(previousAutoCommit);
                LogUtils.warning(plugin, "Failed to accept friend request for " + playerUuid + ": "
                        + exception.getMessage());
            }
        } catch (final SQLException exception) {
            LogUtils.warning(plugin, "Failed to accept friend request for " + playerUuid + ": "
                    + exception.getMessage());
        }
        return false;
    }

    public boolean declineRequest(final UUID playerUuid, final UUID senderUuid) {
        if (playerUuid == null || senderUuid == null) {
            return false;
        }
        try (Connection connection = databaseManager.getConnection()) {
            deleteRequest(connection, senderUuid, playerUuid);
            return true;
        } catch (final SQLException exception) {
            LogUtils.warning(plugin, "Failed to decline friend request for " + playerUuid + ": "
                    + exception.getMessage());
            return false;
        }
    }

    public FriendOperationResult sendFriendRequest(final UUID senderUuid, final String rawTargetName) {
        if (senderUuid == null) {
            return FriendOperationResult.failure("§cImpossible d'envoyer la demande.");
        }
        final String trimmed = rawTargetName == null ? "" : rawTargetName.trim();
        if (trimmed.isEmpty()) {
            return FriendOperationResult.failure("§cVous devez préciser un pseudo.");
        }

        final UUID targetUuid = resolveUuidByName(trimmed);
        if (targetUuid == null) {
            return FriendOperationResult.failure("§cAucun joueur trouvé pour " + trimmed + ".");
        }
        if (senderUuid.equals(targetUuid)) {
            return FriendOperationResult.failure("§cVous ne pouvez pas vous ajouter vous-même.");
        }

        if (areAlreadyFriends(senderUuid, targetUuid)) {
            return FriendOperationResult.failure("§cVous êtes déjà amis.");
        }

        if (hasPendingRequest(senderUuid, targetUuid)) {
            return FriendOperationResult.failure("§cVous avez déjà envoyé une demande à ce joueur.");
        }

        if (hasPendingRequest(targetUuid, senderUuid)) {
            final boolean accepted = acceptRequest(senderUuid, targetUuid);
            if (accepted) {
                return FriendOperationResult.success("§aDemande acceptée automatiquement !");
            }
            return FriendOperationResult.failure("§cImpossible d'accepter la demande existante.");
        }

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(buildInsertRequestQuery())) {
            statement.setString(1, senderUuid.toString());
            statement.setString(2, targetUuid.toString());
            statement.executeUpdate();
            return FriendOperationResult.success("§aDemande envoyée à §f" + trimmed + "§a !");
        } catch (final SQLException exception) {
            LogUtils.warning(plugin, "Failed to create friend request: " + exception.getMessage());
            return FriendOperationResult.failure("§cErreur lors de l'envoi de la demande.");
        }
    }

    private void deleteRequest(final Connection connection, final UUID senderUuid, final UUID targetUuid) throws SQLException {
        final String delete = "DELETE FROM friend_requests WHERE sender_uuid = ? AND target_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(delete)) {
            statement.setString(1, senderUuid.toString());
            statement.setString(2, targetUuid.toString());
            statement.executeUpdate();
        }
    }

    private void upsertFriend(final Connection connection,
                              final UUID playerUuid,
                              final UUID friendUuid) throws SQLException {
        final DatabaseType type = databaseManager.getDatabaseType();
        final String query;
        if (type == DatabaseType.MYSQL) {
            query = """
                    INSERT INTO friends (player_uuid, friend_uuid, status, created_at, accepted_at, is_favorite)
                    VALUES (?, ?, 'ACCEPTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
                    ON DUPLICATE KEY UPDATE status = 'ACCEPTED', accepted_at = CURRENT_TIMESTAMP
                    """;
        } else {
            query = """
                    INSERT INTO friends (player_uuid, friend_uuid, status, created_at, accepted_at, is_favorite)
                    VALUES (?, ?, 'ACCEPTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    ON CONFLICT(player_uuid, friend_uuid) DO UPDATE SET status = 'ACCEPTED', accepted_at = CURRENT_TIMESTAMP
                    """;
        }
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, friendUuid.toString());
            statement.executeUpdate();
        }
    }

    private boolean areAlreadyFriends(final UUID playerUuid, final UUID friendUuid) {
        final String query = "SELECT status FROM friends WHERE player_uuid = ? AND friend_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, friendUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }
                final String status = resultSet.getString("status");
                return STATUS_ACCEPTED.equalsIgnoreCase(status);
            }
        } catch (final SQLException exception) {
            LogUtils.warning(plugin, "Failed to check friend status: " + exception.getMessage());
            return false;
        }
    }

    private boolean hasPendingRequest(final UUID senderUuid, final UUID targetUuid) {
        final String query = "SELECT 1 FROM friend_requests WHERE sender_uuid = ? AND target_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, senderUuid.toString());
            statement.setString(2, targetUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (final SQLException exception) {
            LogUtils.warning(plugin, "Failed to check pending friend request: " + exception.getMessage());
            return false;
        }
    }

    private UUID resolveUuidByName(final String playerName) {
        final String query = "SELECT uuid FROM players WHERE LOWER(username) = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerName.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return UUID.fromString(resultSet.getString("uuid"));
                }
            }
        } catch (final SQLException exception) {
            LogUtils.warning(plugin, "Failed to resolve uuid for " + playerName + ": " + exception.getMessage());
        }
        return null;
    }

    private String buildInsertRequestQuery() {
        final DatabaseType type = databaseManager.getDatabaseType();
        if (type == DatabaseType.MYSQL) {
            return """
                    INSERT INTO friend_requests (sender_uuid, target_uuid, created_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE created_at = VALUES(created_at)
                    """;
        }
        return """
                INSERT INTO friend_requests (sender_uuid, target_uuid, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(sender_uuid, target_uuid) DO UPDATE SET created_at = CURRENT_TIMESTAMP
                """;
    }

    private Optional<String> optionalString(final String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private String resolveOfflineName(final UUID uuid) {
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        return offlinePlayer.getName() == null ? uuid.toString() : offlinePlayer.getName();
    }
}
