package com.lobby.stats;

public class GlobalStats {

    private final int totalGames;
    private final int totalWins;
    private final int totalLosses;
    private final int totalKills;
    private final int totalDeaths;
    private final long totalPlaytime;
    private final double ratio;

    public GlobalStats() {
        this(0, 0, 0, 0, 0, 0L, 0.0D);
    }

    public GlobalStats(final int totalGames,
                       final int totalWins,
                       final int totalLosses,
                       final int totalKills,
                       final int totalDeaths,
                       final long totalPlaytime,
                       final double ratio) {
        this.totalGames = totalGames;
        this.totalWins = totalWins;
        this.totalLosses = totalLosses;
        this.totalKills = totalKills;
        this.totalDeaths = totalDeaths;
        this.totalPlaytime = totalPlaytime;
        this.ratio = ratio;
    }

    public int getTotalGames() {
        return totalGames;
    }

    public int getTotalWins() {
        return totalWins;
    }

    public int getTotalLosses() {
        return totalLosses;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public int getTotalDeaths() {
        return totalDeaths;
    }

    public long getTotalPlaytime() {
        return totalPlaytime;
    }

    public double getRatio() {
        return ratio;
    }

    public String getFormattedPlaytime() {
        final long hours = totalPlaytime / 3600L;
        final long minutes = (totalPlaytime % 3600L) / 60L;
        return String.format("%dh %02dm", hours, minutes);
    }
}

