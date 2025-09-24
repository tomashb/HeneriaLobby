package com.lobby.scoreboard;

public record PlayerScoreboardData(String prefix, long coins, long tokens) {

    public PlayerScoreboardData {
        prefix = prefix == null ? "" : prefix;
    }

    public static PlayerScoreboardData empty() {
        return new PlayerScoreboardData("", 0L, 0L);
    }
}
