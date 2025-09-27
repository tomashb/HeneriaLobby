package com.lobby.friends.manager;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.core.DatabaseManager.DatabaseType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight settings facade dedicated to the advanced friends interactions.
 * <p>
 * The historic friends system already stores a subset of the configuration in
 * {@code friend_settings}. However, recent product requirements introduced new
 * toggles (private messages scope, teleportation behaviour, favourite
 * auto-accept, etc.) that are not represented in the legacy schema. This manager
 * keeps a dedicated table with the additional columns while exposing a concise
 * API tailored for runtime checks performed from inventories and listeners.
 */
public final class FriendsSettingsManager {

    private final LobbyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, FriendsSettings> settingsCache = new ConcurrentHashMap<>();

    public FriendsSettingsManager(final LobbyPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.databaseManager = Objects.requireNonNull(plugin.getDatabaseManager(), "databaseManager");
        createTable();
        plugin.getLogger().info("FriendsSettingsManager initialisé");
    }

    private void createTable() {
        final String createTableSql = """
                CREATE TABLE IF NOT EXISTS friends_settings (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    friend_requests VARCHAR(16) DEFAULT 'EVERYONE',
                    notifications VARCHAR(16) DEFAULT 'ENABLED',
                    online_status VARCHAR(16) DEFAULT 'VISIBLE',
                    private_messages VARCHAR(16) DEFAULT 'FRIENDS',
                    teleportation VARCHAR(16) DEFAULT 'ASK_PERMISSION',
                    auto_accept_favorites VARCHAR(16) DEFAULT 'DISABLED',
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(createTableSql)) {
            statement.executeUpdate();
            plugin.getLogger().info("Table friends_settings créée/vérifiée avec succès");
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur création table friends_settings: " + exception.getMessage());
        }
    }

    /**
     * Retrieves the cached settings of the player or loads them from the
     * database if they are not available yet.
     *
     * @param playerUuid the player identifier
     * @return a future completed with the player settings snapshot
     */
    public CompletableFuture<FriendsSettings> getSettings(final UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(FriendsSettings.defaults(null));
        }
        final FriendsSettings cached = settingsCache.get(playerUuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> loadSettings(playerUuid)).thenApply(settings -> {
            settingsCache.put(playerUuid, settings);
            return settings;
        });
    }

    private FriendsSettings loadSettings(final UUID playerUuid) {
        final String selectSql = "SELECT friend_requests, notifications, online_status, private_messages, teleportation, "
                + "auto_accept_favorites, updated_at FROM friends_settings WHERE player_uuid = ?";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new FriendsSettings(playerUuid,
                            resultSet.getString("friend_requests"),
                            resultSet.getString("notifications"),
                            resultSet.getString("online_status"),
                            resultSet.getString("private_messages"),
                            resultSet.getString("teleportation"),
                            resultSet.getString("auto_accept_favorites"),
                            resultSet.getTimestamp("updated_at"));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().warning("Erreur récupération paramètres amis: " + exception.getMessage());
        }

        return insertDefaultSettings(playerUuid);
    }

    private FriendsSettings insertDefaultSettings(final UUID playerUuid) {
        final FriendsSettings defaults = FriendsSettings.defaults(playerUuid);
        final String insertSql = """
                INSERT INTO friends_settings (
                    player_uuid, friend_requests, notifications, online_status,
                    private_messages, teleportation, auto_accept_favorites, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, defaults.friendRequests());
            statement.setString(3, defaults.notifications());
            statement.setString(4, defaults.onlineStatus());
            statement.setString(5, defaults.privateMessages());
            statement.setString(6, defaults.teleportation());
            statement.setString(7, defaults.autoAcceptFavorites());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().warning("Erreur insertion paramètres amis par défaut: " + exception.getMessage());
        }
        return defaults;
    }

    /**
     * Returns a future completed with the teleportation setting of the player.
     */
    public CompletableFuture<String> getTeleportationSetting(final UUID playerUuid) {
        return getSettings(playerUuid).thenApply(settings -> settings.teleportation().toUpperCase());
    }

    /**
     * Returns a future completed with the private message setting of the
     * player.
     */
    public CompletableFuture<String> getPrivateMessagesSetting(final UUID playerUuid) {
        return getSettings(playerUuid).thenApply(settings -> settings.privateMessages().toUpperCase());
    }

    /**
     * Clears the cached settings for a player. This is mostly used when the
     * player updates their configuration through a dedicated menu.
     */
    public void invalidate(final UUID playerUuid) {
        if (playerUuid != null) {
            settingsCache.remove(playerUuid);
        }
    }

    /**
     * Persists the provided settings snapshot and refreshes the cache entry for
     * the player.
     */
    public CompletableFuture<Boolean> saveSettings(final FriendsSettings settings) {
        if (settings == null || settings.playerUuid() == null) {
            return CompletableFuture.completedFuture(false);
        }

        final DatabaseType type = databaseManager.getDatabaseType();
        final String updateSql;
        if (type == DatabaseType.MYSQL) {
            updateSql = """
                    INSERT INTO friends_settings (
                        player_uuid, friend_requests, notifications, online_status,
                        private_messages, teleportation, auto_accept_favorites, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        friend_requests = VALUES(friend_requests),
                        notifications = VALUES(notifications),
                        online_status = VALUES(online_status),
                        private_messages = VALUES(private_messages),
                        teleportation = VALUES(teleportation),
                        auto_accept_favorites = VALUES(auto_accept_favorites),
                        updated_at = CURRENT_TIMESTAMP
                    """;
        } else {
            updateSql = """
                    INSERT INTO friends_settings (
                        player_uuid, friend_requests, notifications, online_status,
                        private_messages, teleportation, auto_accept_favorites, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        friend_requests = excluded.friend_requests,
                        notifications = excluded.notifications,
                        online_status = excluded.online_status,
                        private_messages = excluded.private_messages,
                        teleportation = excluded.teleportation,
                        auto_accept_favorites = excluded.auto_accept_favorites,
                        updated_at = CURRENT_TIMESTAMP
                    """;
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(updateSql)) {
                statement.setString(1, settings.playerUuid().toString());
                statement.setString(2, settings.friendRequests());
                statement.setString(3, settings.notifications());
                statement.setString(4, settings.onlineStatus());
                statement.setString(5, settings.privateMessages());
                statement.setString(6, settings.teleportation());
                statement.setString(7, settings.autoAcceptFavorites());
                final boolean success = statement.executeUpdate() > 0;
                if (success) {
                    settingsCache.put(settings.playerUuid(), settings.withUpdatedAt(new Timestamp(System.currentTimeMillis())));
                }
                return success;
            } catch (final SQLException exception) {
                plugin.getLogger().warning("Erreur sauvegarde paramètres amis: " + exception.getMessage());
                return false;
            }
        });
    }

    /**
     * Immutable view over the advanced friends settings of a player.
     */
    public record FriendsSettings(UUID playerUuid,
                                  String friendRequests,
                                  String notifications,
                                  String onlineStatus,
                                  String privateMessages,
                                  String teleportation,
                                  String autoAcceptFavorites,
                                  Timestamp updatedAt) {

        public FriendsSettings {
            friendRequests = normalize(friendRequests, "EVERYONE");
            notifications = normalize(notifications, "ENABLED");
            onlineStatus = normalize(onlineStatus, "VISIBLE");
            privateMessages = normalize(privateMessages, "FRIENDS");
            teleportation = normalize(teleportation, "ASK_PERMISSION");
            autoAcceptFavorites = normalize(autoAcceptFavorites, "DISABLED");
        }

        private static String normalize(final String value, final String fallback) {
            return value == null || value.isBlank() ? fallback : value.toUpperCase();
        }

        public FriendsSettings withUpdatedAt(final Timestamp timestamp) {
            return new FriendsSettings(playerUuid, friendRequests, notifications, onlineStatus,
                    privateMessages, teleportation, autoAcceptFavorites, timestamp);
        }

        public static FriendsSettings defaults(final UUID playerUuid) {
            return new FriendsSettings(playerUuid, "EVERYONE", "ENABLED", "VISIBLE",
                    "FRIENDS", "ASK_PERMISSION", "DISABLED", new Timestamp(System.currentTimeMillis()));
        }
    }
}
