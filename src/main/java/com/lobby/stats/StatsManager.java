package com.lobby.stats;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.core.DatabaseManager.DatabaseType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager {

    private static final long CACHE_DURATION_MS = 5_000L;

    private final LobbyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, CachedStats> cache = new ConcurrentHashMap<>();

    public StatsManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public GameStats getPlayerStats(final UUID playerUuid, final String rawGameType) {
        if (playerUuid == null || rawGameType == null || rawGameType.isBlank()) {
            return new GameStats();
        }
        final String gameType = rawGameType.trim().toUpperCase(Locale.ROOT);
        final CachedStats cachedStats = loadCachedStats(playerUuid);
        return cachedStats.gameStats().getOrDefault(gameType, new GameStats());
    }

    public GlobalStats getGlobalStats(final UUID playerUuid) {
        if (playerUuid == null) {
            return new GlobalStats();
        }
        final CachedStats cachedStats = loadCachedStats(playerUuid);
        return cachedStats.globalStats();
    }

    public void updateStats(final UUID playerUuid, final String rawGameType, final GameStatsUpdate update) {
        if (playerUuid == null || rawGameType == null || rawGameType.isBlank() || update == null) {
            return;
        }
        final String gameType = rawGameType.trim().toUpperCase(Locale.ROOT);

        final String query;
        if (databaseManager.getDatabaseType() == DatabaseType.MYSQL) {
            query = """
                    INSERT INTO player_game_stats (player_uuid, game_type, games_played, wins, losses, kills, deaths, special_stat_1, special_stat_2, playtime_seconds)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        games_played = games_played + VALUES(games_played),
                        wins = wins + VALUES(wins),
                        losses = losses + VALUES(losses),
                        kills = kills + VALUES(kills),
                        deaths = deaths + VALUES(deaths),
                        special_stat_1 = special_stat_1 + VALUES(special_stat_1),
                        special_stat_2 = special_stat_2 + VALUES(special_stat_2),
                        playtime_seconds = playtime_seconds + VALUES(playtime_seconds)
                    """;
        } else {
            query = """
                    INSERT INTO player_game_stats (player_uuid, game_type, games_played, wins, losses, kills, deaths, special_stat_1, special_stat_2, playtime_seconds)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(player_uuid, game_type) DO UPDATE SET
                        games_played = games_played + excluded.games_played,
                        wins = wins + excluded.wins,
                        losses = losses + excluded.losses,
                        kills = kills + excluded.kills,
                        deaths = deaths + excluded.deaths,
                        special_stat_1 = special_stat_1 + excluded.special_stat_1,
                        special_stat_2 = special_stat_2 + excluded.special_stat_2,
                        playtime_seconds = playtime_seconds + excluded.playtime_seconds
                    """;
        }

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, gameType);
            statement.setInt(3, update.getGamesPlayed());
            statement.setInt(4, update.getWins());
            statement.setInt(5, update.getLosses());
            statement.setInt(6, update.getKills());
            statement.setInt(7, update.getDeaths());
            statement.setInt(8, update.getSpecialStat1());
            statement.setInt(9, update.getSpecialStat2());
            statement.setLong(10, update.getPlaytimeSeconds());
            statement.executeUpdate();
            invalidateCache(playerUuid);
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur lors de la mise à jour des stats: " + exception.getMessage());
        }
    }

    public void invalidateCache(final UUID playerUuid) {
        if (playerUuid != null) {
            cache.remove(playerUuid);
        }
    }

    public void clearCache() {
        cache.clear();
    }

    private CachedStats loadCachedStats(final UUID playerUuid) {
        final long now = System.currentTimeMillis();
        final CachedStats cached = cache.get(playerUuid);
        if (cached != null && (now - cached.timestamp()) < CACHE_DURATION_MS) {
            return cached;
        }

        final CachedStats loaded = loadStatsFromDatabase(playerUuid);
        cache.put(playerUuid, loaded);
        return loaded;
    }

    private CachedStats loadStatsFromDatabase(final UUID playerUuid) {
        final Map<String, GameStats> perGame = new HashMap<>();
        int totalGames = 0;
        int totalWins = 0;
        int totalLosses = 0;
        int totalKills = 0;
        int totalDeaths = 0;
        long totalPlaytime = 0L;

        final String query = "SELECT game_type, games_played, wins, losses, kills, deaths, special_stat_1, special_stat_2, playtime_seconds "
                + "FROM player_game_stats WHERE player_uuid = ?";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final String gameType = resultSet.getString("game_type");
                    final int gamesPlayed = resultSet.getInt("games_played");
                    final int wins = resultSet.getInt("wins");
                    final int losses = resultSet.getInt("losses");
                    final int kills = resultSet.getInt("kills");
                    final int deaths = resultSet.getInt("deaths");
                    final int special1 = resultSet.getInt("special_stat_1");
                    final int special2 = resultSet.getInt("special_stat_2");
                    final long playtimeSeconds = resultSet.getLong("playtime_seconds");

                    totalGames += gamesPlayed;
                    totalWins += wins;
                    totalLosses += losses;
                    totalKills += kills;
                    totalDeaths += deaths;
                    totalPlaytime += playtimeSeconds;

                    perGame.put(gameType.toUpperCase(Locale.ROOT), new GameStats(
                            gamesPlayed,
                            wins,
                            losses,
                            kills,
                            deaths,
                            special1,
                            special2,
                            playtimeSeconds
                    ));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur lors de la récupération des stats: " + exception.getMessage());
        }

        final double ratio = totalDeaths > 0 ? (double) totalWins / (double) totalDeaths : (double) totalWins;
        final GlobalStats globalStats = new GlobalStats(totalGames, totalWins, totalLosses, totalKills, totalDeaths,
                totalPlaytime, ratio);

        return new CachedStats(Collections.unmodifiableMap(perGame), globalStats, System.currentTimeMillis());
    }

    private record CachedStats(Map<String, GameStats> gameStats, GlobalStats globalStats, long timestamp) {
    }
}

