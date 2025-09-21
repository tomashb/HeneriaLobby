package com.lobby.social.friends;

/**
 * Represents the possible outcomes when attempting to send a friend request.
 */
public enum FriendRequestResult {

    SUCCESS(true),
    AUTO_ACCEPTED(true),
    SELF_REQUEST(false),
    ALREADY_FRIENDS(false),
    BLOCKED(false),
    REQUEST_ALREADY_SENT(false),
    INCOMING_REQUEST_PENDING(false),
    SETTINGS_DISABLED(false),
    MUTUAL_FRIENDS_REQUIRED(false),
    DATABASE_ERROR(false);

    private final boolean success;

    FriendRequestResult(final boolean success) {
        this.success = success;
    }

    /**
     * Indicates whether the result corresponds to a successful friend request.
     *
     * @return {@code true} if the request has been created, {@code false} otherwise.
     */
    public boolean isSuccess() {
        return success;
    }
}

