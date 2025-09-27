package com.lobby.friends.database;

import com.lobby.friends.data.FriendData;
import com.lobby.friends.data.FriendRequest;
import com.lobby.friends.data.FriendSettings;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Handles persistence for the friends system. The implementation currently
 * targets SQLite but has been written in a way that keeps it compatible with
 * the existing Bukkit scheduler and avoids blocking the main thread.
 */
public class FriendsDatabase {

    private final Plugin plugin;
    private Connection connection;

    public FriendsDatabase(final Plugin plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            final File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                plugin.getLogger().warning("Unable to create data folder for friends database");
            }
            final String url = "jdbc:sqlite:" + new File(dataFolder, "friends.db");
            connection = DriverManager.getConnection(url);
            createTables();
            plugin.getLogger().info("Base de données Friends initialisée avec succès !");
        } catch (SQLException exception) {
            plugin.getLogger().severe("Erreur lors de l'initialisation de la base de données : " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        final String friendshipsTable = """
            CREATE TABLE IF NOT EXISTS friendships (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid VARCHAR(36) NOT NULL,
                friend_uuid VARCHAR(36) NOT NULL,
                friendship_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                is_favorite BOOLEAN DEFAULT FALSE,
                messages_exchanged INTEGER DEFAULT 0,
                time_together INTEGER DEFAULT 0,
                last_interaction TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(player_uuid, friend_uuid)
            )
        """;

        final String requestsTable = """
            CREATE TABLE IF NOT EXISTS friend_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_uuid VARCHAR(36) NOT NULL,
                receiver_uuid VARCHAR(36) NOT NULL,
                message TEXT,
                request_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                status VARCHAR(20) DEFAULT 'PENDING',
                UNIQUE(sender_uuid, receiver_uuid)
            )
        """;

        final String blockedTable = """
            CREATE TABLE IF NOT EXISTS blocked_players (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                blocker_uuid VARCHAR(36) NOT NULL,
                blocked_uuid VARCHAR(36) NOT NULL,
                block_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                reason TEXT,
                block_type VARCHAR(20) DEFAULT 'MANUAL',
                expires_at TIMESTAMP NULL,
                UNIQUE(blocker_uuid, blocked_uuid)
            )
        """;

        final String settingsTable = """
            CREATE TABLE IF NOT EXISTS friend_settings (
                player_uuid VARCHAR(36) PRIMARY KEY,
                notifications VARCHAR(20) DEFAULT 'IMPORTANT',
                visibility VARCHAR(20) DEFAULT 'FRIENDS',
                auto_requests VARCHAR(20) DEFAULT 'MANUAL',
                sounds BOOLEAN DEFAULT TRUE,
                sound_volume INTEGER DEFAULT 70,
                private_messages VARCHAR(20) DEFAULT 'FRIENDS',
                teleportation VARCHAR(20) DEFAULT 'ASK_PERMISSION',
                teleport_delay INTEGER DEFAULT 3,
                favorite_limit INTEGER DEFAULT 5,
                status_message TEXT DEFAULT '',
                do_not_disturb BOOLEAN DEFAULT FALSE,
                dnd_expires_at TIMESTAMP NULL,
                theme VARCHAR(20) DEFAULT 'CLASSIC',
                performance_mode VARCHAR(20) DEFAULT 'BALANCED',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(friendshipsTable);
            statement.execute(requestsTable);
            statement.execute(blockedTable);
            statement.execute(settingsTable);
        }
    }

    // region Settings

    public CompletableFuture<FriendSettings> getFriendSettings(final String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return supplyAsync(() -> loadFriendSettings(playerUuid));
    }

    public CompletableFuture<Boolean> saveFriendSettings(final FriendSettings settings) {
        if (settings == null) {
            return CompletableFuture.completedFuture(false);
        }
        return supplyAsync(() -> {
            synchronized (this) {
                return writeFriendSettings(settings);
            }
        });
    }

    private FriendSettings loadFriendSettings(final String playerUuid) {
        final String query = """
            SELECT notifications, visibility, auto_requests, sounds, private_messages, teleportation
            FROM friend_settings
            WHERE player_uuid = ?
        """;

        synchronized (this) {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerUuid);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return mapFriendSettings(playerUuid, resultSet);
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().severe("Erreur lors du chargement des paramètres d'amis : " + exception.getMessage());
            }
            final FriendSettings defaults = FriendSettings.defaults(playerUuid);
            writeFriendSettings(defaults);
            return defaults;
        }
    }

    private boolean writeFriendSettings(final FriendSettings settings) {
        final String query = """
            INSERT INTO friend_settings (player_uuid, notifications, visibility, auto_requests, sounds, private_messages, teleportation)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                notifications = excluded.notifications,
                visibility = excluded.visibility,
                auto_requests = excluded.auto_requests,
                sounds = excluded.sounds,
                private_messages = excluded.private_messages,
                teleportation = excluded.teleportation,
                updated_at = CURRENT_TIMESTAMP
        """;

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, settings.getPlayerUuid());
            statement.setString(2, settings.getNotifications());
            statement.setString(3, settings.getVisibility());
            statement.setString(4, settings.getAutoRequests());
            statement.setBoolean(5, settings.isSoundsEnabled());
            statement.setString(6, settings.getPrivateMessages());
            statement.setString(7, settings.getTeleportation());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde des paramètres d'amis : " + exception.getMessage());
            return false;
        }
    }

    private FriendSettings mapFriendSettings(final String playerUuid, final ResultSet resultSet) throws SQLException {
        return new FriendSettings(
                playerUuid,
                resultSet.getString("notifications"),
                resultSet.getString("visibility"),
                resultSet.getString("auto_requests"),
                resultSet.getBoolean("sounds"),
                resultSet.getString("private_messages"),
                resultSet.getString("teleportation")
        );
    }

    // endregion

    // region Friendships

    public CompletableFuture<List<FriendData>> getFriends(final String playerUuid) {
        return supplyAsync(() -> {
            final List<FriendData> friends = new ArrayList<>();
            final String query = """
                SELECT f.friend_uuid, f.friendship_date, f.is_favorite,
                       f.messages_exchanged, f.time_together, f.last_interaction
                FROM friendships f
                WHERE f.player_uuid = ?
                ORDER BY f.is_favorite DESC, f.last_interaction DESC
            """;

            synchronized (this) {
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, playerUuid);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            friends.add(new FriendData(
                                    resultSet.getString("friend_uuid"),
                                    resultSet.getTimestamp("friendship_date"),
                                    resultSet.getBoolean("is_favorite"),
                                    resultSet.getInt("messages_exchanged"),
                                    resultSet.getLong("time_together"),
                                    resultSet.getTimestamp("last_interaction")
                            ));
                        }
                    }
                } catch (SQLException exception) {
                    plugin.getLogger().severe("Erreur lors de la récupération des amis : " + exception.getMessage());
                }
            }
            return friends;
        });
    }

    public CompletableFuture<Boolean> addFriendship(final String playerUuid, final String friendUuid) {
        return supplyAsync(() -> {
            final String query = """
                INSERT OR IGNORE INTO friendships (player_uuid, friend_uuid)
                VALUES (?, ?), (?, ?)
            """;

            synchronized (this) {
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, playerUuid);
                    statement.setString(2, friendUuid);
                    statement.setString(3, friendUuid);
                    statement.setString(4, playerUuid);
                    return statement.executeUpdate() > 0;
                } catch (SQLException exception) {
                    plugin.getLogger().severe("Erreur lors de l'ajout d'amitié : " + exception.getMessage());
                }
            }
            return false;
        });
    }

    public CompletableFuture<Boolean> removeFriendship(final String playerUuid, final String friendUuid) {
        return supplyAsync(() -> {
            final String query = """
                DELETE FROM friendships
                WHERE (player_uuid = ? AND friend_uuid = ?)
                   OR (player_uuid = ? AND friend_uuid = ?)
            """;

            synchronized (this) {
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, playerUuid);
                    statement.setString(2, friendUuid);
                    statement.setString(3, friendUuid);
                    statement.setString(4, playerUuid);
                    return statement.executeUpdate() > 0;
                } catch (SQLException exception) {
                    plugin.getLogger().severe("Erreur lors de la suppression d'amitié : " + exception.getMessage());
                }
            }
            return false;
        });
    }

    public CompletableFuture<Boolean> setFavorite(final String playerUuid, final String friendUuid, final boolean favorite) {
        return supplyAsync(() -> {
            final String query = "UPDATE friendships SET is_favorite = ? WHERE player_uuid = ? AND friend_uuid = ?";

            synchronized (this) {
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setBoolean(1, favorite);
                    statement.setString(2, playerUuid);
                    statement.setString(3, friendUuid);
                    return statement.executeUpdate() > 0;
                } catch (SQLException exception) {
                    plugin.getLogger().severe("Erreur lors de la mise à jour du favori : " + exception.getMessage());
                }
            }
            return false;
        });
    }

    // endregion

    // region Requests

    public CompletableFuture<List<FriendRequest>> getPendingRequests(final String playerUuid) {
        return supplyAsync(() -> {
            final List<FriendRequest> requests = new ArrayList<>();
            final String query = """
                SELECT sender_uuid, message, request_date
                FROM friend_requests
                WHERE receiver_uuid = ? AND status = 'PENDING'
                ORDER BY request_date DESC
            """;

            synchronized (this) {
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, playerUuid);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            requests.add(new FriendRequest(
                                    resultSet.getString("sender_uuid"),
                                    playerUuid,
                                    resultSet.getString("message"),
                                    resultSet.getTimestamp("request_date")
                            ));
                        }
                    }
                } catch (SQLException exception) {
                    plugin.getLogger().severe("Erreur lors de la récupération des demandes : " + exception.getMessage());
                }
            }
            return requests;
        });
    }

    public CompletableFuture<Boolean> sendFriendRequest(final String senderUuid, final String receiverUuid, final String message) {
        return supplyAsync(() -> {
            if (hasExistingRequest(senderUuid, receiverUuid)) {
                return false;
            }

            final String query = """
                INSERT INTO friend_requests (sender_uuid, receiver_uuid, message)
                VALUES (?, ?, ?)
            """;

            synchronized (this) {
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, senderUuid);
                    statement.setString(2, receiverUuid);
                    statement.setString(3, message);
                    return statement.executeUpdate() > 0;
                } catch (SQLException exception) {
                    plugin.getLogger().severe("Erreur lors de l'envoi de demande : " + exception.getMessage());
                }
            }
            return false;
        });
    }

    public CompletableFuture<Boolean> acceptFriendRequest(final String senderUuid, final String receiverUuid) {
        return supplyAsync(() -> {
            synchronized (this) {
                try {
                    connection.setAutoCommit(false);

                    final String updateRequest = "UPDATE friend_requests SET status = 'ACCEPTED' WHERE sender_uuid = ? AND receiver_uuid = ?";
                    try (PreparedStatement statement = connection.prepareStatement(updateRequest)) {
                        statement.setString(1, senderUuid);
                        statement.setString(2, receiverUuid);
                        statement.executeUpdate();
                    }

                    final String addFriendship = """
                        INSERT OR IGNORE INTO friendships (player_uuid, friend_uuid)
                        VALUES (?, ?), (?, ?)
                    """;
                    try (PreparedStatement statement = connection.prepareStatement(addFriendship)) {
                        statement.setString(1, senderUuid);
                        statement.setString(2, receiverUuid);
                        statement.setString(3, receiverUuid);
                        statement.setString(4, senderUuid);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    connection.setAutoCommit(true);
                    return true;
                } catch (SQLException exception) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollback) {
                        plugin.getLogger().severe("Erreur lors du rollback : " + rollback.getMessage());
                    }
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException ignore) {
                        // ignore
                    }
                    plugin.getLogger().severe("Erreur lors de l'acceptation de demande : " + exception.getMessage());
                }
            }
            return false;
        });
    }

    public CompletableFuture<Boolean> rejectFriendRequest(final String senderUuid, final String receiverUuid) {
        return supplyAsync(() -> {
            final String query = "UPDATE friend_requests SET status = 'REJECTED' WHERE sender_uuid = ? AND receiver_uuid = ?";
            synchronized (this) {
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, senderUuid);
                    statement.setString(2, receiverUuid);
                    return statement.executeUpdate() > 0;
                } catch (SQLException exception) {
                    plugin.getLogger().severe("Erreur lors du rejet de demande : " + exception.getMessage());
                }
            }
            return false;
        });
    }

    // endregion

    private boolean hasExistingRequest(final String senderUuid, final String receiverUuid) {
        final String query = """
            SELECT COUNT(*) FROM friend_requests
            WHERE ((sender_uuid = ? AND receiver_uuid = ?) OR (sender_uuid = ? AND receiver_uuid = ?))
              AND status = 'PENDING'
        """;

        synchronized (this) {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, senderUuid);
                statement.setString(2, receiverUuid);
                statement.setString(3, receiverUuid);
                statement.setString(4, senderUuid);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt(1) > 0;
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().severe("Erreur lors de la vérification de demande existante : " + exception.getMessage());
            }
        }
        return false;
    }

    public CompletableFuture<Boolean> areFriends(final String playerUuid, final String friendUuid) {
        return supplyAsync(() -> {
            final String query = "SELECT COUNT(*) FROM friendships WHERE player_uuid = ? AND friend_uuid = ?";
            synchronized (this) {
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, playerUuid);
                    statement.setString(2, friendUuid);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return resultSet.getInt(1) > 0;
                        }
                    }
                } catch (SQLException exception) {
                    plugin.getLogger().severe("Erreur lors de la vérification d'amitié : " + exception.getMessage());
                }
            }
            return false;
        });
    }

    public void close() {
        synchronized (this) {
            if (connection == null) {
                return;
            }
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException exception) {
                plugin.getLogger().severe("Erreur lors de la fermeture de la base de données : " + exception.getMessage());
            }
        }
    }

    private <T> CompletableFuture<T> supplyAsync(final Supplier<T> supplier) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }
}
