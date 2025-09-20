package com.lobby.social.friends;

import java.util.UUID;

public class FriendRequest {

    private final UUID sender;
    private final UUID target;
    private final long timestamp;
    private FriendRequestStatus status;

    public FriendRequest(final UUID sender, final UUID target, final long timestamp, final FriendRequestStatus status) {
        this.sender = sender;
        this.target = target;
        this.timestamp = timestamp;
        this.status = status;
    }

    public UUID getSender() {
        return sender;
    }

    public UUID getTarget() {
        return target;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public FriendRequestStatus getStatus() {
        return status;
    }

    public void setStatus(final FriendRequestStatus status) {
        this.status = status;
    }
}
