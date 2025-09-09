package com.heneria.lobby.player;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents persistent data for a player.
 */
public class PlayerData {
    private final UUID uuid;
    private String username;
    private long coins;
    private Instant firstJoin;
    private Instant lastSeen;

    public PlayerData(UUID uuid, String username, long coins, Instant firstJoin, Instant lastSeen) {
        this.uuid = uuid;
        this.username = username;
        this.coins = coins;
        this.firstJoin = firstJoin;
        this.lastSeen = lastSeen;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getCoins() {
        return coins;
    }

    public void setCoins(long coins) {
        this.coins = coins;
    }

    public Instant getFirstJoin() {
        return firstJoin;
    }

    public void setFirstJoin(Instant firstJoin) {
        this.firstJoin = firstJoin;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }
}
