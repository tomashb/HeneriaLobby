package com.lobby.social.friends;

public enum FriendRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    BLOCKED;

    public static FriendRequestStatus fromDatabase(final String value) {
        if (value == null || value.isEmpty()) {
            return PENDING;
        }
        try {
            return FriendRequestStatus.valueOf(value.toUpperCase());
        } catch (final IllegalArgumentException exception) {
            return PENDING;
        }
    }
}
