package com.lobby.friends;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a pending friend request sent to the viewing player.
 */
public record FriendRequestEntry(UUID senderUuid,
                                 String senderName,
                                 Instant createdAt) {

    public FriendRequestEntry {
        if (senderUuid == null) {
            throw new IllegalArgumentException("Sender UUID cannot be null");
        }
    }
}
