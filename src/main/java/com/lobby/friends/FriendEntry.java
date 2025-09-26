package com.lobby.friends;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable view model representing a single friend entry in the menu.
 */
public record FriendEntry(UUID uuid,
                          String name,
                          boolean online,
                          boolean favorite,
                          Instant since) {

    public FriendEntry {
        if (uuid == null) {
            throw new IllegalArgumentException("Friend UUID cannot be null");
        }
    }
}
