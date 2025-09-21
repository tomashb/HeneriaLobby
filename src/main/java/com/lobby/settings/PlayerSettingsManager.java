package com.lobby.settings;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.core.DatabaseManager.DatabaseType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSettingsManager {

    private final LobbyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();

    public PlayerSettingsManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public PlayerSettings getPlayerSettings(final UUID playerUuid) {
        if (playerUuid == null) {
            return new PlayerSettings();
        }
        return cache.computeIfAbsent(playerUuid, this::loadSettingsFromDatabase);
    }

    public void toggleSetting(final UUID playerUuid, final SettingType type) {
        if (playerUuid == null || type == null) {
            return;
        }

        final PlayerSettings settings = getPlayerSettings(playerUuid);
        switch (type) {
            case PRIVATE_MESSAGES -> settings.setPrivateMessages(!settings.isPrivateMessages());
            case FRIEND_REQUESTS -> settings.setFriendRequestSetting(getNextFriendRequestSetting(settings.getFriendRequestSetting()));
            case GROUP_REQUESTS -> settings.setGroupRequestSetting(getNextGroupRequestSetting(settings.getGroupRequestSetting()));
            case PLAYER_VISIBILITY -> settings.setVisibilitySetting(getNextVisibilitySetting(settings.getVisibilitySetting()));
            case UI_SOUNDS -> settings.setUiSounds(!settings.isUiSounds());
            case PARTICLES -> settings.setParticles(!settings.isParticles());
            case MUSIC -> settings.setMusic(!settings.isMusic());
            case FRIEND_NOTIFICATIONS -> settings.setFriendNotifications(!settings.isFriendNotifications());
            case CLAN_NOTIFICATIONS -> settings.setClanNotifications(!settings.isClanNotifications());
            case SYSTEM_NOTIFICATIONS -> settings.setSystemNotifications(!settings.isSystemNotifications());
        }

        saveSettings(playerUuid, settings);
        cache.put(playerUuid, settings);
    }

    public void setLanguage(final UUID playerUuid, final String languageCode) {
        if (playerUuid == null || languageCode == null || languageCode.isBlank()) {
            return;
        }
        final PlayerSettings settings = getPlayerSettings(playerUuid);
        settings.setLanguage(languageCode);
        saveSettings(playerUuid, settings);
        cache.put(playerUuid, settings);
    }

    public void invalidateCache(final UUID playerUuid) {
        if (playerUuid != null) {
            cache.remove(playerUuid);
        }
    }

    public void clearCache() {
        cache.clear();
    }

    private PlayerSettings loadSettingsFromDatabase(final UUID playerUuid) {
        final String query = "SELECT private_messages, friend_requests, group_requests, visibility, ui_sounds, particles, music, "
                + "friend_notifications, clan_notifications, system_notifications, language FROM player_settings WHERE player_uuid = ?";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final boolean privateMessages = resultSet.getBoolean("private_messages");
                    final FriendRequestSetting friendRequests = parseFriendSetting(resultSet.getString("friend_requests"));
                    final GroupRequestSetting groupRequests = parseGroupSetting(resultSet.getString("group_requests"));
                    final VisibilitySetting visibility = parseVisibilitySetting(resultSet.getString("visibility"));
                    final boolean uiSounds = resultSet.getBoolean("ui_sounds");
                    final boolean particles = resultSet.getBoolean("particles");
                    final boolean music = resultSet.getBoolean("music");
                    final boolean friendNotifications = resultSet.getBoolean("friend_notifications");
                    final boolean clanNotifications = resultSet.getBoolean("clan_notifications");
                    final boolean systemNotifications = resultSet.getBoolean("system_notifications");
                    final String language = resultSet.getString("language");

                    return new PlayerSettings(privateMessages, friendRequests, groupRequests, visibility, uiSounds,
                            particles, music, friendNotifications, clanNotifications, systemNotifications, language);
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur lors du chargement des paramètres: " + exception.getMessage());
        }

        return new PlayerSettings();
    }

    private void saveSettings(final UUID playerUuid, final PlayerSettings settings) {
        final String sql;
        if (databaseManager.getDatabaseType() == DatabaseType.MYSQL) {
            sql = """
                    INSERT INTO player_settings (player_uuid, private_messages, friend_requests, group_requests, visibility, ui_sounds, particles, music, friend_notifications, clan_notifications, system_notifications, language)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        private_messages = VALUES(private_messages),
                        friend_requests = VALUES(friend_requests),
                        group_requests = VALUES(group_requests),
                        visibility = VALUES(visibility),
                        ui_sounds = VALUES(ui_sounds),
                        particles = VALUES(particles),
                        music = VALUES(music),
                        friend_notifications = VALUES(friend_notifications),
                        clan_notifications = VALUES(clan_notifications),
                        system_notifications = VALUES(system_notifications),
                        language = VALUES(language)
                    """;
        } else {
            sql = """
                    INSERT INTO player_settings (player_uuid, private_messages, friend_requests, group_requests, visibility, ui_sounds, particles, music, friend_notifications, clan_notifications, system_notifications, language)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        private_messages = excluded.private_messages,
                        friend_requests = excluded.friend_requests,
                        group_requests = excluded.group_requests,
                        visibility = excluded.visibility,
                        ui_sounds = excluded.ui_sounds,
                        particles = excluded.particles,
                        music = excluded.music,
                        friend_notifications = excluded.friend_notifications,
                        clan_notifications = excluded.clan_notifications,
                        system_notifications = excluded.system_notifications,
                        language = excluded.language
                    """;
        }

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setBoolean(2, settings.isPrivateMessages());
            statement.setString(3, settings.getFriendRequestSetting().name());
            statement.setString(4, settings.getGroupRequestSetting().name());
            statement.setString(5, settings.getVisibilitySetting().name());
            statement.setBoolean(6, settings.isUiSounds());
            statement.setBoolean(7, settings.isParticles());
            statement.setBoolean(8, settings.isMusic());
            statement.setBoolean(9, settings.isFriendNotifications());
            statement.setBoolean(10, settings.isClanNotifications());
            statement.setBoolean(11, settings.isSystemNotifications());
            statement.setString(12, settings.getLanguage().toLowerCase(Locale.ROOT));
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde des paramètres: " + exception.getMessage());
        }
    }

    private FriendRequestSetting getNextFriendRequestSetting(final FriendRequestSetting current) {
        return switch (current) {
            case EVERYONE -> FriendRequestSetting.FRIENDS_OF_FRIENDS;
            case FRIENDS_OF_FRIENDS -> FriendRequestSetting.DISABLED;
            case DISABLED -> FriendRequestSetting.EVERYONE;
        };
    }

    private GroupRequestSetting getNextGroupRequestSetting(final GroupRequestSetting current) {
        return switch (current) {
            case EVERYONE -> GroupRequestSetting.FRIENDS_ONLY;
            case FRIENDS_ONLY -> GroupRequestSetting.DISABLED;
            case DISABLED -> GroupRequestSetting.EVERYONE;
        };
    }

    private VisibilitySetting getNextVisibilitySetting(final VisibilitySetting current) {
        return switch (current) {
            case EVERYONE -> VisibilitySetting.FRIENDS_ONLY;
            case FRIENDS_ONLY -> VisibilitySetting.NOBODY;
            case NOBODY -> VisibilitySetting.EVERYONE;
        };
    }

    private FriendRequestSetting parseFriendSetting(final String value) {
        if (value == null) {
            return FriendRequestSetting.EVERYONE;
        }
        try {
            return FriendRequestSetting.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return FriendRequestSetting.EVERYONE;
        }
    }

    private GroupRequestSetting parseGroupSetting(final String value) {
        if (value == null) {
            return GroupRequestSetting.EVERYONE;
        }
        try {
            return GroupRequestSetting.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return GroupRequestSetting.EVERYONE;
        }
    }

    private VisibilitySetting parseVisibilitySetting(final String value) {
        if (value == null) {
            return VisibilitySetting.EVERYONE;
        }
        try {
            return VisibilitySetting.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return VisibilitySetting.EVERYONE;
        }
    }
}

