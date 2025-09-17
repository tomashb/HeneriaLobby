package com.lobby.economy;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.data.PlayerData;
import com.lobby.economy.event.EconomyTransactionEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class EconomyManager implements EconomyAPI {

    private final LobbyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final TransactionManager transactionManager;
    private final LeaderboardManager leaderboardManager;
    private final ConcurrentMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();
    private BukkitTask autoSaveTask;

    private long maxCoins;
    private long maxTokens;
    private long startingCoins;
    private long startingTokens;
    private boolean allowTransfers;
    private double transferTaxPercentage;
    private long saveIntervalMinutes;

    public EconomyManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.transactionManager = new TransactionManager(plugin, databaseManager);
        this.leaderboardManager = new LeaderboardManager(plugin, databaseManager);
        reload();
    }

    public void reload() {
        final var config = plugin.getConfig();
        this.maxCoins = config.getLong("economy.max_coins", 999_999_999L);
        this.maxTokens = config.getLong("economy.max_tokens", 999_999L);
        this.startingCoins = config.getLong("economy.starting_coins", 1_000L);
        this.startingTokens = config.getLong("economy.starting_tokens", 0L);
        this.allowTransfers = config.getBoolean("economy.allow_transfers", true);
        this.transferTaxPercentage = config.getDouble("economy.transfer_tax_percentage", 0D);
        this.saveIntervalMinutes = config.getLong("economy.save_interval_minutes", 5L);
        final boolean logTransactions = config.getBoolean("economy.log_transactions", true);
        this.transactionManager.setLogTransactions(logTransactions);
        this.leaderboardManager.setCacheIntervalMinutes(saveIntervalMinutes);
        restartAutoSaveTask();
    }

    public void shutdown() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        saveAll();
        cache.clear();
        locks.clear();
    }

    public boolean isTransfersAllowed() {
        return allowTransfers;
    }

    public double getTransferTaxPercentage() {
        return transferTaxPercentage;
    }

    @Override
    public long getCoins(final UUID player) {
        return getPlayerData(player).coins();
    }

    @Override
    public long getTokens(final UUID player) {
        return getPlayerData(player).tokens();
    }

    @Override
    public boolean hasCoins(final UUID player, final long amount) {
        return amount <= 0 || getCoins(player) >= amount;
    }

    @Override
    public boolean hasTokens(final UUID player, final long amount) {
        return amount <= 0 || getTokens(player) >= amount;
    }

    @Override
    public void addCoins(final UUID player, final long amount, final String reason) {
        if (amount <= 0) {
            return;
        }
        changeBalance(player, CurrencyType.COINS, amount, reason);
    }

    @Override
    public void removeCoins(final UUID player, final long amount, final String reason) {
        if (amount <= 0) {
            return;
        }
        changeBalance(player, CurrencyType.COINS, -amount, reason);
    }

    @Override
    public void addTokens(final UUID player, final long amount, final String reason) {
        if (amount <= 0) {
            return;
        }
        changeBalance(player, CurrencyType.TOKENS, amount, reason);
    }

    @Override
    public void removeTokens(final UUID player, final long amount, final String reason) {
        if (amount <= 0) {
            return;
        }
        changeBalance(player, CurrencyType.TOKENS, -amount, reason);
    }

    @Override
    public boolean transfer(final UUID from, final UUID to, final long amount, final String reason) {
        return performTransfer(from, to, amount, reason).isPresent();
    }

    public Optional<TransactionManager.TransferResult> transferDetailed(final UUID from, final UUID to, final long amount,
                                                                         final String reason) {
        return performTransfer(from, to, amount, reason);
    }

    @Override
    public List<String> getTopCoins(final int limit) {
        return leaderboardManager.getTopCoins(limit).stream()
                .map(entry -> entry.username() + ":" + entry.amount())
                .toList();
    }

    @Override
    public List<String> getTopTokens(final int limit) {
        return leaderboardManager.getTopTokens(limit).stream()
                .map(entry -> entry.username() + ":" + entry.amount())
                .toList();
    }

    public List<LeaderboardManager.LeaderboardEntry> getTopCoinsEntries(final int limit) {
        return leaderboardManager.getTopCoins(limit);
    }

    public List<LeaderboardManager.LeaderboardEntry> getTopTokenEntries(final int limit) {
        return leaderboardManager.getTopTokens(limit);
    }

    public int getPlayerRank(final UUID uuid, final CurrencyType currencyType) {
        return leaderboardManager.getRank(uuid, currencyType);
    }

    public Optional<PlayerData> getPlayerDataByName(final String username) {
        if (username == null || username.isEmpty()) {
            return Optional.empty();
        }
        final Optional<PlayerData> cached = cache.values().stream()
                .filter(data -> username.equalsIgnoreCase(data.username()))
                .findFirst();
        if (cached.isPresent()) {
            return cached;
        }
        final String query = "SELECT uuid FROM players WHERE LOWER(username) = LOWER(?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                    return Optional.of(getPlayerData(uuid));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player by name", exception);
        }
        return Optional.empty();
    }

    public PlayerData getPlayerData(final UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadPlayerData);
    }

    public void save(final UUID uuid) {
        final PlayerData data = cache.get(uuid);
        if (data == null) {
            return;
        }
        persistPlayerData(data);
    }

    public void saveAll() {
        final List<PlayerData> snapshot = new ArrayList<>(cache.values());
        snapshot.forEach(this::persistPlayerData);
    }

    public void handlePlayerJoin(final UUID uuid, final String username) {
        final ReentrantLock lock = locks.computeIfAbsent(uuid, key -> new ReentrantLock());
        lock.lock();
        try {
            final PlayerData data = getPlayerData(uuid);
            if (username != null && !username.equalsIgnoreCase(data.username())) {
                final PlayerData updated = data.withUsername(username);
                cache.put(uuid, updated);
                persistPlayerData(updated);
            }
        } finally {
            lock.unlock();
        }
    }

    public void handlePlayerQuit(final UUID uuid) {
        save(uuid);
        cache.remove(uuid);
        locks.remove(uuid);
    }

    private Optional<TransactionManager.TransferResult> performTransfer(final UUID from, final UUID to, final long amount,
                                                                         final String reason) {
        if (!allowTransfers || from == null || to == null || from.equals(to) || amount <= 0L) {
            return Optional.empty();
        }
        final List<UUID> ordered = new ArrayList<>(List.of(from, to));
        ordered.sort(Comparator.comparing(UUID::toString));
        final List<ReentrantLock> acquiredLocks = ordered.stream()
                .map(uuid -> locks.computeIfAbsent(uuid, key -> new ReentrantLock()))
                .toList();
        acquiredLocks.forEach(ReentrantLock::lock);
        final List<EconomyTransactionEvent> events = new ArrayList<>(2);
        try {
            final PlayerData senderData = getPlayerData(from);
            if (senderData.coins() < amount) {
                return Optional.empty();
            }
            final PlayerData receiverData = getPlayerData(to);
            final Optional<TransactionManager.TransferResult> result = transactionManager.executeTransfer(from, to, amount,
                    maxCoins, reason, transferTaxPercentage);
            if (result.isEmpty()) {
                return Optional.empty();
            }
            final TransactionManager.TransferResult transferResult = result.get();
            cache.put(from, senderData.withCoins(transferResult.senderBalanceAfter()));
            cache.put(to, receiverData.withCoins(transferResult.receiverBalanceAfter()));
            leaderboardManager.invalidate();
            events.add(new EconomyTransactionEvent(from, CurrencyType.COINS, -amount,
                    transferResult.senderBalanceAfter(), reason));
            events.add(new EconomyTransactionEvent(to, CurrencyType.COINS, transferResult.transferredAmount(),
                    transferResult.receiverBalanceAfter(), reason));
            return Optional.of(transferResult);
        } finally {
            for (int index = acquiredLocks.size() - 1; index >= 0; index--) {
                acquiredLocks.get(index).unlock();
            }
            events.forEach(event -> Bukkit.getPluginManager().callEvent(event));
        }
    }

    private void changeBalance(final UUID uuid, final CurrencyType currencyType, final long delta, final String reason) {
        final ReentrantLock lock = locks.computeIfAbsent(uuid, key -> new ReentrantLock());
        EconomyTransactionEvent transactionEvent = null;
        lock.lock();
        try {
            final PlayerData data = getPlayerData(uuid);
            final long current = currencyType.isCoins() ? data.coins() : data.tokens();
            final long max = currencyType.isCoins() ? maxCoins : maxTokens;
            final long clampedDelta = clampDelta(current, delta, max);
            if (clampedDelta == 0L) {
                return;
            }
            final OptionalLong result = transactionManager.applyBalanceChange(uuid, currencyType, clampedDelta, max, reason);
            if (result.isEmpty()) {
                return;
            }
            final long newBalance = result.getAsLong();
            final PlayerData updated = currencyType.isCoins() ? data.withCoins(newBalance) : data.withTokens(newBalance);
            cache.put(uuid, updated);
            leaderboardManager.invalidate();
            transactionEvent = new EconomyTransactionEvent(uuid, currencyType, clampedDelta, newBalance, reason);
        } finally {
            lock.unlock();
        }
        if (transactionEvent != null) {
            Bukkit.getPluginManager().callEvent(transactionEvent);
        }
    }

    private long clampDelta(final long current, final long delta, final long max) {
        if (delta > 0) {
            final long available = Math.max(0L, max - current);
            return Math.min(delta, available);
        }
        if (delta < 0) {
            return -Math.min(current, Math.abs(delta));
        }
        return 0;
    }

    private PlayerData loadPlayerData(final UUID uuid) {
        final String query = "SELECT username, coins, tokens, first_join, last_join, total_playtime FROM players WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final String username = resultSet.getString("username");
                    final long coins = resultSet.getLong("coins");
                    final long tokens = resultSet.getLong("tokens");
                    final Instant firstJoin = getInstant(resultSet.getTimestamp("first_join"));
                    final Instant lastJoin = getInstant(resultSet.getTimestamp("last_join"));
                    final long playtime = resultSet.getLong("total_playtime");
                    return new PlayerData(uuid, username, coins, tokens, firstJoin, lastJoin, playtime);
                }
            }
            final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            final String username = offlinePlayer != null ? offlinePlayer.getName() : "Inconnu";
            final PlayerData playerData = new PlayerData(uuid, Objects.requireNonNullElse(username, "Inconnu"),
                    startingCoins, startingTokens, Instant.now(), Instant.now(), 0L);
            insertPlayerData(playerData);
            return playerData;
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player data", exception);
            return new PlayerData(uuid, "Erreur", 0L, 0L, Instant.now(), Instant.now(), 0L);
        }
    }

    private void insertPlayerData(final PlayerData playerData) throws SQLException {
        final String query = "INSERT INTO players (uuid, username, coins, tokens, first_join, last_join, total_playtime) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerData.uuid().toString());
            statement.setString(2, playerData.username());
            statement.setLong(3, playerData.coins());
            statement.setLong(4, playerData.tokens());
            statement.setLong(5, playerData.totalPlaytime());
            statement.executeUpdate();
        }
    }

    private void persistPlayerData(final PlayerData data) {
        final String query = "UPDATE players SET username = ?, coins = ?, tokens = ?, first_join = ?, last_join = ?, total_playtime = ? "
                + "WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, data.username());
            statement.setLong(2, data.coins());
            statement.setLong(3, data.tokens());
            statement.setTimestamp(4, Timestamp.from(data.firstJoin()));
            statement.setTimestamp(5, Timestamp.from(data.lastJoin()));
            statement.setLong(6, data.totalPlaytime());
            statement.setString(7, data.uuid().toString());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to persist player data", exception);
        }
    }

    private Instant getInstant(final Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private void startAutoSaveTask() {
        final long intervalTicks = Math.max(1L, saveIntervalMinutes * 60L * 20L);
        autoSaveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveAll,
                intervalTicks, intervalTicks);
    }

    private void restartAutoSaveTask() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        startAutoSaveTask();
    }
}
