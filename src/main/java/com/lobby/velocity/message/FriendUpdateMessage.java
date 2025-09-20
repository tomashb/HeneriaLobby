package com.lobby.velocity.message;

import java.util.UUID;

public class FriendUpdateMessage {

    private final UUID playerUuid;
    private final String action;
    private final UUID targetUuid;

    public FriendUpdateMessage(final UUID playerUuid, final String action, final UUID targetUuid) {
        this.playerUuid = playerUuid;
        this.action = action;
        this.targetUuid = targetUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getAction() {
        return action;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String serialize() {
        final String targetPart = targetUuid == null ? "" : targetUuid.toString();
        return playerUuid + "|" + sanitize(action) + "|" + targetPart;
    }

    public static FriendUpdateMessage deserialize(final String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Friend update payload is empty");
        }
        final String[] parts = raw.split("\\|", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Friend update payload is malformed");
        }
        final UUID playerUuid = UUID.fromString(parts[0]);
        final String action = parts[1];
        final UUID targetUuid = parts[2].isEmpty() ? null : UUID.fromString(parts[2]);
        return new FriendUpdateMessage(playerUuid, action, targetUuid);
    }

    private String sanitize(final String value) {
        return value == null ? "" : value.replace('|', ':');
    }
}
