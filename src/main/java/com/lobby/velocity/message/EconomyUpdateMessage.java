package com.lobby.velocity.message;

import java.util.UUID;

public class EconomyUpdateMessage {

    private final UUID playerUuid;
    private final String playerName;
    private final long coins;
    private final long tokens;

    public EconomyUpdateMessage(final UUID playerUuid, final String playerName, final long coins, final long tokens) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.coins = coins;
        this.tokens = tokens;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getCoins() {
        return coins;
    }

    public long getTokens() {
        return tokens;
    }

    public String serialize() {
        return playerUuid + "|" + sanitize(playerName) + "|" + coins + "|" + tokens;
    }

    public static EconomyUpdateMessage deserialize(final String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Economy update payload is empty");
        }
        final String[] parts = raw.split("\\|", 4);
        if (parts.length < 4) {
            throw new IllegalArgumentException("Economy update payload is malformed");
        }
        final UUID uuid = UUID.fromString(parts[0]);
        final String name = parts[1];
        final long coins = Long.parseLong(parts[2]);
        final long tokens = Long.parseLong(parts[3]);
        return new EconomyUpdateMessage(uuid, name, coins, tokens);
    }

    private String sanitize(final String value) {
        return value == null ? "" : value.replace('|', ':');
    }
}
