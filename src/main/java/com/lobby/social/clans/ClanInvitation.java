package com.lobby.social.clans;

import java.util.UUID;

public class ClanInvitation {

    private final int id;
    private final int clanId;
    private final UUID inviterUuid;
    private final UUID invitedUuid;
    private final String message;
    private final long createdAt;
    private final long expiresAt;

    public ClanInvitation(final int id, final int clanId, final UUID inviterUuid, final UUID invitedUuid, final String message, final long createdAt, final long expiresAt) {
        this.id = id;
        this.clanId = clanId;
        this.inviterUuid = inviterUuid;
        this.invitedUuid = invitedUuid;
        this.message = message;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public int getId() {
        return id;
    }

    public int getClanId() {
        return clanId;
    }

    public UUID getInviterUuid() {
        return inviterUuid;
    }

    public UUID getInvitedUuid() {
        return invitedUuid;
    }

    public String getMessage() {
        return message;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }
}
