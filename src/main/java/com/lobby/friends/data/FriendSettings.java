package com.lobby.friends.data;

import java.util.Objects;

/**
 * Immutable snapshot of a player's friend settings. Only the properties used by
 * the in-game settings menu are currently exposed. The remaining columns in the
 * database keep their previous values when these settings are persisted.
 */
public final class FriendSettings {

    private final String playerUuid;
    private final String notifications;
    private final String visibility;
    private final String autoRequests;
    private final boolean soundsEnabled;
    private final String privateMessages;
    private final String teleportation;

    public FriendSettings(final String playerUuid,
                          final String notifications,
                          final String visibility,
                          final String autoRequests,
                          final boolean soundsEnabled,
                          final String privateMessages,
                          final String teleportation) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.notifications = normalize(notifications, "IMPORTANT");
        this.visibility = normalize(visibility, "FRIENDS");
        this.autoRequests = normalize(autoRequests, "MANUAL");
        this.soundsEnabled = soundsEnabled;
        this.privateMessages = normalize(privateMessages, "FRIENDS");
        this.teleportation = normalize(teleportation, "ASK_PERMISSION");
    }

    private static String normalize(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value.toUpperCase();
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public String getNotifications() {
        return notifications;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getAutoRequests() {
        return autoRequests;
    }

    public boolean isSoundsEnabled() {
        return soundsEnabled;
    }

    public String getPrivateMessages() {
        return privateMessages;
    }

    public String getTeleportation() {
        return teleportation;
    }

    public FriendSettings withNotifications(final String notifications) {
        return new FriendSettings(playerUuid, notifications, visibility, autoRequests, soundsEnabled, privateMessages, teleportation);
    }

    public FriendSettings withVisibility(final String visibility) {
        return new FriendSettings(playerUuid, notifications, visibility, autoRequests, soundsEnabled, privateMessages, teleportation);
    }

    public FriendSettings withAutoRequests(final String autoRequests) {
        return new FriendSettings(playerUuid, notifications, visibility, autoRequests, soundsEnabled, privateMessages, teleportation);
    }

    public FriendSettings withSoundsEnabled(final boolean enabled) {
        return new FriendSettings(playerUuid, notifications, visibility, autoRequests, enabled, privateMessages, teleportation);
    }

    public FriendSettings withPrivateMessages(final String privateMessages) {
        return new FriendSettings(playerUuid, notifications, visibility, autoRequests, soundsEnabled, privateMessages, teleportation);
    }

    public FriendSettings withTeleportation(final String teleportation) {
        return new FriendSettings(playerUuid, notifications, visibility, autoRequests, soundsEnabled, privateMessages, teleportation);
    }

    public static FriendSettings defaults(final String playerUuid) {
        return new FriendSettings(playerUuid, "IMPORTANT", "FRIENDS", "MANUAL", true, "FRIENDS", "ASK_PERMISSION");
    }
}
