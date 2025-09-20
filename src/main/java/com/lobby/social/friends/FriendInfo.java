package com.lobby.social.friends;

import java.util.UUID;

public class FriendInfo {

    private final UUID uuid;
    private final String name;
    private final boolean online;
    private final String server;
    private final long friendsSince;
    private final long lastSeen;
    private final boolean favorite;

    public FriendInfo(final UUID uuid,
                      final String name,
                      final boolean online,
                      final String server,
                      final long friendsSince,
                      final long lastSeen,
                      final boolean favorite) {
        this.uuid = uuid;
        this.name = name;
        this.online = online;
        this.server = server;
        this.friendsSince = friendsSince;
        this.lastSeen = lastSeen;
        this.favorite = favorite;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public boolean isOnline() {
        return online;
    }

    public String getServer() {
        return server;
    }

    public long getFriendsSince() {
        return friendsSince;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public boolean isFavorite() {
        return favorite;
    }
}
