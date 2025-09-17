package com.lobby.economy;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LeaderboardManager {

    private static final int DEFAULT_CACHE_LIMIT = 100;
    private final DatabaseManager databaseManager;
    private final Logger logger;
    private final AtomicLong lastUpdate = new AtomicLong(0L);
    private long cacheIntervalMillis = Duration.ofMinutes(5).toMillis();
    private volatile List<LeaderboardEntry> topCoins = Collections.emptyList();
    private volatile List<LeaderboardEntry> topTokens = Collections.emptyList();

    public LeaderboardManager(final LobbyPlugin plugin, final DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.logger = plugin.getLogger();
    }

    public void setCacheIntervalMinutes(final long minutes) {
        this.cacheIntervalMillis = Math.max(60_000L, Duration.ofMinutes(minutes).toMillis());
    }

    public void invalidate() {
        lastUpdate.set(0L);
    }

    public List<LeaderboardEntry> getTopCoins(final int limit) {
        updateLeaderboardsIfNeeded();
        return topCoins.stream().limit(Math.max(limit, 0)).toList();
    }

    public List<LeaderboardEntry> getTopTokens(final int limit) {
        updateLeaderboardsIfNeeded();
        return topTokens.stream().limit(Math.max(limit, 0)).toList();
    }

    public int getRank(final UUID playerUuid, final CurrencyType currencyType) {
        final String column = currencyType.isCoins() ? "coins" : "tokens";
        final String query = "SELECT 1 + (SELECT COUNT(*) FROM players WHERE " + column + " > p." + column + ") AS rank, p." + column
                + " AS balance FROM players p WHERE p.uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final int rank = resultSet.getInt("rank");
                    final long balance = resultSet.getLong("balance");
                    if (balance <= 0 && rank == 0) {
                        return -1;
                    }
                    return rank;
                }
            }
        } catch (final SQLException exception) {
            logger.log(Level.SEVERE, "Failed to fetch leaderboard rank", exception);
        }
        return -1;
    }

    private void updateLeaderboardsIfNeeded() {
        final long now = System.currentTimeMillis();
        final long last = lastUpdate.get();
        if (now - last < cacheIntervalMillis) {
            return;
        }
        synchronized (this) {
            if (now - lastUpdate.get() < cacheIntervalMillis) {
                return;
            }
            topCoins = fetchTopPlayers("coins");
            topTokens = fetchTopPlayers("tokens");
            lastUpdate.set(now);
        }
    }

    private List<LeaderboardEntry> fetchTopPlayers(final String column) {
        final List<LeaderboardEntry> entries = new ArrayList<>();
        final String query = "SELECT uuid, username, " + column + " FROM players ORDER BY " + column + " DESC LIMIT ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, DEFAULT_CACHE_LIMIT);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                    final String username = Objects.requireNonNullElse(resultSet.getString("username"), "Inconnu");
                    final long balance = resultSet.getLong(column);
                    entries.add(new LeaderboardEntry(uuid, username, balance));
                }
            }
        } catch (final SQLException exception) {
            logger.log(Level.SEVERE, "Failed to fetch leaderboard for " + column, exception);
        }
        return entries;
    }

    public record LeaderboardEntry(UUID uuid, String username, long amount) {
        public String formattedAmount() {
            return String.format(Locale.FRANCE, "%,d", amount).replace('\u00a0', ' ');
        }
    }
}
