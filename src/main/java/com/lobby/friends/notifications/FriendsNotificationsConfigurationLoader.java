package com.lobby.friends.notifications;

import com.lobby.LobbyPlugin;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loader for {@code notifications.yml}. The configuration powers real-time
 * notifications about friend activity.
 */
public final class FriendsNotificationsConfigurationLoader {

    private static final String RESOURCE_PATH = "friends/notifications.yml";

    private FriendsNotificationsConfigurationLoader() {
    }

    public static FriendsNotificationsConfiguration load(final LobbyPlugin plugin) {
        final File directory = ensureDirectory(plugin);
        final File file = new File(directory, "notifications.yml");
        ensureResource(plugin, file);

        final YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Impossible de charger '" + file.getName() + "': " + exception.getMessage());
            return buildFallback();
        }

        final Map<String, FriendsNotificationsConfiguration.NotificationSettings> notifications = parseNotifications(configuration.getConfigurationSection("notifications"));
        final FriendsNotificationsConfiguration.AutoUpdateSettings autoUpdate = parseAutoUpdate(configuration.getConfigurationSection("auto_update"));

        return new FriendsNotificationsConfiguration(notifications, autoUpdate);
    }

    private static File ensureDirectory(final LobbyPlugin plugin) {
        final File dataFolder = plugin.getDataFolder();
        final File directory = new File(dataFolder, "friends");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Impossible de créer le dossier des amis: " + directory.getAbsolutePath());
        }
        return directory;
    }

    private static void ensureResource(final LobbyPlugin plugin, final File file) {
        if (file.exists()) {
            return;
        }
        try {
            plugin.saveResource(RESOURCE_PATH, false);
        } catch (final IllegalArgumentException ignored) {
            // Resource not packaged; rely on fallback.
        }
    }

    private static FriendsNotificationsConfiguration buildFallback() {
        return new FriendsNotificationsConfiguration(Map.of(), FriendsNotificationsConfiguration.AutoUpdateSettings.disabled());
    }

    private static Map<String, FriendsNotificationsConfiguration.NotificationSettings> parseNotifications(final ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        final Map<String, FriendsNotificationsConfiguration.NotificationSettings> notifications = new HashMap<>();
        for (String key : section.getKeys(false)) {
            final ConfigurationSection notificationSection = section.getConfigurationSection(key);
            if (notificationSection == null) {
                continue;
            }
            final boolean enabled = notificationSection.getBoolean("enabled", true);
            final String message = notificationSection.getString("message", "");
            final Sound sound = resolveSound(notificationSection.getString("sound"));
            final double volume = notificationSection.getDouble("volume", 1.0d);
            final double pitch = notificationSection.getDouble("pitch", 1.0d);
            final String actionBar = notificationSection.getString("actionbar", "");
            final int duration = notificationSection.getInt("duration", 0);
            final String chat = notificationSection.getString("chat_notification", "");
            notifications.put(key, new FriendsNotificationsConfiguration.NotificationSettings(enabled, message, sound,
                    volume, pitch, actionBar, duration, chat));
        }
        return Map.copyOf(notifications);
    }

    private static FriendsNotificationsConfiguration.AutoUpdateSettings parseAutoUpdate(final ConfigurationSection section) {
        if (section == null) {
            return FriendsNotificationsConfiguration.AutoUpdateSettings.disabled();
        }
        final boolean enabled = section.getBoolean("enabled", true);
        final int interval = section.getInt("interval", 5);
        final boolean updateOffline = section.getBoolean("update_offline_status", true);
        final boolean updateServer = section.getBoolean("update_server_status", true);
        final boolean refreshHeads = section.getBoolean("refresh_heads", true);
        return new FriendsNotificationsConfiguration.AutoUpdateSettings(enabled, interval, updateOffline, updateServer, refreshHeads);
    }

    private static Sound resolveSound(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

