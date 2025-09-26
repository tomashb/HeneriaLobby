package com.lobby.friends.notifications;

import org.bukkit.Sound;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable container for the notifications system configuration. Each
 * notification entry describes the feedback sent to players when specific
 * friend events occur (online, offline, etc.).
 */
public final class FriendsNotificationsConfiguration {

    private final Map<String, NotificationSettings> notifications;
    private final AutoUpdateSettings autoUpdateSettings;

    public FriendsNotificationsConfiguration(final Map<String, NotificationSettings> notifications,
                                             final AutoUpdateSettings autoUpdateSettings) {
        this.notifications = notifications == null ? Map.of() : Map.copyOf(notifications);
        this.autoUpdateSettings = Objects.requireNonNullElse(autoUpdateSettings, AutoUpdateSettings.disabled());
    }

    public Map<String, NotificationSettings> getNotifications() {
        return Collections.unmodifiableMap(notifications);
    }

    public AutoUpdateSettings getAutoUpdateSettings() {
        return autoUpdateSettings;
    }

    /**
     * Single notification entry configuration.
     */
    public record NotificationSettings(boolean enabled,
                                       String message,
                                       Sound sound,
                                       double volume,
                                       double pitch,
                                       String actionBar,
                                       int durationSeconds,
                                       String chatNotification) {

        public NotificationSettings {
            volume = Math.max(0.0d, volume);
            pitch = Math.max(0.0d, pitch);
            durationSeconds = Math.max(0, durationSeconds);
            message = message == null ? "" : message;
            actionBar = actionBar == null ? "" : actionBar;
            chatNotification = chatNotification == null ? "" : chatNotification;
        }
    }

    /**
     * Settings controlling the automatic status refresh behaviour.
     */
    public record AutoUpdateSettings(boolean enabled,
                                     int intervalSeconds,
                                     boolean updateOfflineStatus,
                                     boolean updateServerStatus,
                                     boolean refreshHeads) {

        public AutoUpdateSettings {
            intervalSeconds = Math.max(1, intervalSeconds);
        }

        public static AutoUpdateSettings disabled() {
            return new AutoUpdateSettings(false, 5, true, true, true);
        }
    }
}

