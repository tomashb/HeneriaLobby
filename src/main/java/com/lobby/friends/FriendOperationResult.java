package com.lobby.friends;

/**
 * Result returned by friend operations such as sending a request.
 */
public record FriendOperationResult(boolean success, String message) {

    public static FriendOperationResult success(final String message) {
        return new FriendOperationResult(true, message);
    }

    public static FriendOperationResult failure(final String message) {
        return new FriendOperationResult(false, message);
    }
}
