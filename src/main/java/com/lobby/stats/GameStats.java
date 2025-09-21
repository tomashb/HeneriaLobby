package com.lobby.stats;

public class GameStats {

    private final int gamesPlayed;
    private final int wins;
    private final int losses;
    private final int kills;
    private final int deaths;
    private final int specialStat1;
    private final int specialStat2;
    private final long playtimeSeconds;

    public GameStats() {
        this(0, 0, 0, 0, 0, 0, 0, 0L);
    }

    public GameStats(final int gamesPlayed,
                     final int wins,
                     final int losses,
                     final int kills,
                     final int deaths,
                     final int specialStat1,
                     final int specialStat2,
                     final long playtimeSeconds) {
        this.gamesPlayed = gamesPlayed;
        this.wins = wins;
        this.losses = losses;
        this.kills = kills;
        this.deaths = deaths;
        this.specialStat1 = specialStat1;
        this.specialStat2 = specialStat2;
        this.playtimeSeconds = playtimeSeconds;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getSpecialStat1() {
        return specialStat1;
    }

    public int getSpecialStat2() {
        return specialStat2;
    }

    public long getPlaytimeSeconds() {
        return playtimeSeconds;
    }

    public double getRatio() {
        if (deaths <= 0) {
            return kills;
        }
        return (double) kills / (double) deaths;
    }

    public String getFormattedPlaytime() {
        final long hours = playtimeSeconds / 3600L;
        final long minutes = (playtimeSeconds % 3600L) / 60L;
        return String.format("%dh %02dm", hours, minutes);
    }
}

