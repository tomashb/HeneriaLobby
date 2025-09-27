package com.lobby.friends.manager;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Centralises all persistence and caching logic related to the blocked players
 * feature. The manager keeps the database in sync with the in-memory cache and
 * guarantees that updates to the block reason are always reflected on both
 * layers.
 */
public class BlockedPlayersManager {

    private static final String DEFAULT_REASON = "Aucune raison spécifiée";

    private final LobbyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Set<UUID>> blockCache = new ConcurrentHashMap<>();
    private final Map<String, BlockInfo> blockInfoCache = new ConcurrentHashMap<>();

    public BlockedPlayersManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();

        createTable();
        loadBlocksCache();
    }

    private void createTable() {
        final String createTableSql = """
            CREATE TABLE IF NOT EXISTS blocked_players (
                id INT AUTO_INCREMENT PRIMARY KEY,
                blocker_uuid VARCHAR(36) NOT NULL,
                blocked_uuid VARCHAR(36) NOT NULL,
                reason VARCHAR(255) DEFAULT 'Aucune raison spécifiée',
                blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY unique_block (blocker_uuid, blocked_uuid),
                INDEX idx_blocker (blocker_uuid),
                INDEX idx_blocked (blocked_uuid)
            )
        """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(createTableSql)) {
            statement.executeUpdate();
            plugin.getLogger().info("Table blocked_players vérifiée avec succès");
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la création de la table blocked_players", exception);
        }
    }

    public boolean blockPlayer(final UUID blocker, final UUID blocked, final String reason) {
        if (blocker == null || blocked == null || blocker.equals(blocked)) {
            return false;
        }

        final String normalizedReason = normalizeReason(reason);

        if (isPlayerBlocked(blocker, blocked)) {
            return updateBlockReason(blocker, blocked, normalizedReason);
        }

        final String query = "INSERT INTO blocked_players (blocker_uuid, blocked_uuid, reason) VALUES (?, ?, ?)";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, blocker.toString());
            statement.setString(2, blocked.toString());
            statement.setString(3, normalizedReason);

            final int rowsAffected = statement.executeUpdate();
            if (rowsAffected == 0) {
                return false;
            }

            blockCache.computeIfAbsent(blocker, key -> ConcurrentHashMap.newKeySet()).add(blocked);

            final BlockInfo blockInfo = fetchBlockInfo(connection, blocker, blocked)
                    .orElseGet(() -> new BlockInfo(normalizedReason, currentTimestamp(), currentTimestamp()));
            blockInfoCache.put(getBlockKey(blocker, blocked), blockInfo);

            if (plugin.getFriendsManager() != null) {
                plugin.getFriendsManager().removeFriendship(blocker, blocked);
            }

            plugin.getLogger().info("Joueur " + blocked + " bloqué par " + blocker + " - Raison: " + normalizedReason);
            return true;
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du blocage d'un joueur", exception);
        }
        return false;
    }

    public boolean updateBlockReason(final UUID blocker, final UUID blocked, final String newReason) {
        if (blocker == null || blocked == null) {
            return false;
        }

        final String normalizedReason = normalizeReason(newReason);
        final String query = "UPDATE blocked_players SET reason = ?, updated_at = CURRENT_TIMESTAMP WHERE blocker_uuid = ? AND blocked_uuid = ?";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, normalizedReason);
            statement.setString(2, blocker.toString());
            statement.setString(3, blocked.toString());

            final int rowsAffected = statement.executeUpdate();
            if (rowsAffected == 0) {
                plugin.getLogger().warning("Aucune ligne affectée lors de la mise à jour de la raison de blocage");
                return false;
            }

            blockCache.computeIfAbsent(blocker, key -> ConcurrentHashMap.newKeySet()).add(blocked);

            final BlockInfo updatedInfo = fetchBlockInfo(connection, blocker, blocked)
                    .orElseGet(() -> new BlockInfo(normalizedReason, currentTimestamp(), currentTimestamp()));
            blockInfoCache.put(getBlockKey(blocker, blocked), updatedInfo);

            plugin.getLogger().info("Raison de blocage mise à jour: " + blocker + " -> " + blocked + " - Nouvelle raison: " + normalizedReason);
            return true;
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la mise à jour de la raison de blocage", exception);
        }
        return false;
    }

    public boolean unblockPlayer(final UUID blocker, final UUID blocked) {
        if (blocker == null || blocked == null) {
            return false;
        }

        final String query = "DELETE FROM blocked_players WHERE blocker_uuid = ? AND blocked_uuid = ?";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, blocker.toString());
            statement.setString(2, blocked.toString());

            final int rowsAffected = statement.executeUpdate();
            if (rowsAffected == 0) {
                return false;
            }

            final Set<UUID> blockedSet = blockCache.get(blocker);
            if (blockedSet != null) {
                blockedSet.remove(blocked);
                if (blockedSet.isEmpty()) {
                    blockCache.remove(blocker);
                }
            }
            blockInfoCache.remove(getBlockKey(blocker, blocked));

            plugin.getLogger().info("Joueur " + blocked + " débloqué par " + blocker);
            return true;
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du déblocage d'un joueur", exception);
        }
        return false;
    }

    public boolean isPlayerBlocked(final UUID blocker, final UUID blocked) {
        final Set<UUID> blockedSet = blockCache.get(blocker);
        return blockedSet != null && blockedSet.contains(blocked);
    }

    public Set<UUID> getBlockedPlayers(final UUID blocker) {
        final Set<UUID> blockedSet = blockCache.get(blocker);
        if (blockedSet == null || blockedSet.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(blockedSet);
    }

    public String getBlockReason(final UUID blocker, final UUID blocked) {
        final BlockInfo info = blockInfoCache.get(getBlockKey(blocker, blocked));
        return info != null ? info.getReason() : null;
    }

    public BlockInfo getBlockInfo(final UUID blocker, final UUID blocked) {
        return blockInfoCache.get(getBlockKey(blocker, blocked));
    }

    public int getTotalBlocks() {
        return blockInfoCache.size();
    }

    public int getBlockedCount(final UUID blocker) {
        final Set<UUID> blockedSet = blockCache.get(blocker);
        return blockedSet != null ? blockedSet.size() : 0;
    }

    public void reloadCache() {
        loadBlocksCache();
    }

    public void shutdown() {
        blockCache.clear();
        blockInfoCache.clear();
    }

    private void loadBlocksCache() {
        final String query = "SELECT blocker_uuid, blocked_uuid, reason, blocked_at, updated_at FROM blocked_players";

        blockCache.clear();
        blockInfoCache.clear();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {

            int loadedBlocks = 0;
            while (resultSet.next()) {
                final UUID blocker = UUID.fromString(resultSet.getString("blocker_uuid"));
                final UUID blocked = UUID.fromString(resultSet.getString("blocked_uuid"));
                final String reason = resultSet.getString("reason");
                final Timestamp blockedAt = resultSet.getTimestamp("blocked_at");
                final Timestamp updatedAt = resultSet.getTimestamp("updated_at");

                blockCache.computeIfAbsent(blocker, key -> ConcurrentHashMap.newKeySet()).add(blocked);
                blockInfoCache.put(getBlockKey(blocker, blocked), new BlockInfo(reason, blockedAt, updatedAt));
                loadedBlocks++;
            }

            plugin.getLogger().info("Chargé " + loadedBlocks + " blocage(s) en cache");
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement du cache des joueurs bloqués", exception);
        }
    }

    private Optional<BlockInfo> fetchBlockInfo(final Connection connection, final UUID blocker, final UUID blocked) {
        final String query = "SELECT reason, blocked_at, updated_at FROM blocked_players WHERE blocker_uuid = ? AND blocked_uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, blocker.toString());
            statement.setString(2, blocked.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new BlockInfo(
                            resultSet.getString("reason"),
                            resultSet.getTimestamp("blocked_at"),
                            resultSet.getTimestamp("updated_at")));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération des informations de blocage", exception);
        }
        return Optional.empty();
    }

    private Timestamp currentTimestamp() {
        return new Timestamp(System.currentTimeMillis());
    }

    private String getBlockKey(final UUID blocker, final UUID blocked) {
        return blocker + ":" + blocked;
    }

    private String normalizeReason(final String reason) {
        if (reason == null) {
            return DEFAULT_REASON;
        }
        final String trimmed = reason.trim();
        return trimmed.isEmpty() ? DEFAULT_REASON : trimmed;
    }

    public static class BlockInfo {
        private final String reason;
        private final Timestamp blockedAt;
        private final Timestamp updatedAt;

        public BlockInfo(final String reason, final Timestamp blockedAt, final Timestamp updatedAt) {
            this.reason = reason;
            this.blockedAt = blockedAt;
            this.updatedAt = updatedAt;
        }

        public String getReason() {
            return reason;
        }

        public Timestamp getBlockedAt() {
            return blockedAt;
        }

        public Timestamp getUpdatedAt() {
            return updatedAt;
        }
    }
}
