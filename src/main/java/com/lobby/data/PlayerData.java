package com.lobby.data;

import java.time.Instant;
import java.util.UUID;

public record PlayerData(
        UUID uuid,
        String username,
        long coins,
        long tokens,
        Instant firstJoin,
        Instant lastJoin,
        long totalPlaytime
) {
    public PlayerData withCoins(final long newCoins) {
        return new PlayerData(uuid, username, newCoins, tokens, firstJoin, lastJoin, totalPlaytime);
    }

    public PlayerData withTokens(final long newTokens) {
        return new PlayerData(uuid, username, coins, newTokens, firstJoin, lastJoin, totalPlaytime);
    }

    public PlayerData withUsername(final String newUsername) {
        return new PlayerData(uuid, newUsername, coins, tokens, firstJoin, lastJoin, totalPlaytime);
    }
}
