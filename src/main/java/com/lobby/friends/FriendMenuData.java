package com.lobby.friends;

import java.util.Collections;
import java.util.List;

/**
 * Bundles the data required to render the friends menu.
 */
public record FriendMenuData(List<FriendEntry> friends, List<FriendRequestEntry> requests) {

    public FriendMenuData {
        if (friends == null) {
            throw new IllegalArgumentException("friends list cannot be null");
        }
        if (requests == null) {
            throw new IllegalArgumentException("requests list cannot be null");
        }
    }

    public List<FriendEntry> friends() {
        return Collections.unmodifiableList(friends);
    }

    public List<FriendRequestEntry> requests() {
        return Collections.unmodifiableList(requests);
    }
}
