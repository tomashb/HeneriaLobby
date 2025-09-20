package com.lobby.social.groups;

import java.util.UUID;

public class GroupInvitation {

    private final int id;
    private final int groupId;
    private final UUID inviter;
    private final UUID invited;
    private final long createdAt;
    private final long expiresAt;

    public GroupInvitation(final int id, final int groupId, final UUID inviter, final UUID invited, final long createdAt, final long expiresAt) {
        this.id = id;
        this.groupId = groupId;
        this.inviter = inviter;
        this.invited = invited;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public int getId() {
        return id;
    }

    public int getGroupId() {
        return groupId;
    }

    public UUID getInviter() {
        return inviter;
    }

    public UUID getInvited() {
        return invited;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }
}
