package com.lobby.economy;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TransactionManager {

    private final DatabaseManager databaseManager;
    private final Logger logger;
    private boolean logTransactions = true;

    public TransactionManager(final LobbyPlugin plugin, final DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.logger = plugin.getLogger();
    }

    public void setLogTransactions(final boolean logTransactions) {
        this.logTransactions = logTransactions;
    }

    public OptionalLong applyBalanceChange(final UUID playerUuid, final CurrencyType currencyType, final long delta,
                                           final long maxBalance, final String reason) {
        if (delta == 0L) {
            return OptionalLong.empty();
        }
        try (Connection connection = databaseManager.getConnection()) {
            final boolean initialAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                final BalanceSnapshot snapshot = fetchSnapshot(connection, playerUuid);
                if (snapshot == null) {
                    connection.rollback();
                    return OptionalLong.empty();
                }

                final long currentBalance = snapshot.balance(currencyType);
                final long newBalance = currentBalance + delta;
                if (newBalance < 0 || newBalance > maxBalance) {
                    connection.rollback();
                    return OptionalLong.empty();
                }

                final String updateQuery = currencyType.isCoins()
                        ? "UPDATE players SET coins = ? WHERE uuid = ?"
                        : "UPDATE players SET tokens = ? WHERE uuid = ?";
                try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
                    updateStatement.setLong(1, newBalance);
                    updateStatement.setString(2, playerUuid.toString());
                    updateStatement.executeUpdate();
                }

                if (logTransactions) {
                    final TransactionType transactionType = determineTransactionType(currencyType, delta);
                    insertTransaction(connection, playerUuid, transactionType, Math.abs(delta), newBalance, reason);
                }

                connection.commit();
                return OptionalLong.of(newBalance);
            } catch (final SQLException exception) {
                connection.rollback();
                logger.log(Level.SEVERE, "Failed to apply balance change", exception);
            } finally {
                connection.setAutoCommit(initialAutoCommit);
            }
        } catch (final SQLException exception) {
            logger.log(Level.SEVERE, "Unable to open connection for balance change", exception);
        }
        return OptionalLong.empty();
    }

    public Optional<TransferResult> executeTransfer(final UUID from, final UUID to, final long amount,
                                                    final long maxCoins, final String reason,
                                                    final double taxPercentage) {
        if (amount <= 0L) {
            return Optional.empty();
        }
        final long taxAmount = calculateTax(amount, taxPercentage);
        final long netAmount = amount - taxAmount;
        try (Connection connection = databaseManager.getConnection()) {
            final boolean initialAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                final Map<UUID, BalanceSnapshot> snapshots = fetchSnapshots(connection, from, to);
                final BalanceSnapshot senderSnapshot = snapshots.get(from);
                final BalanceSnapshot receiverSnapshot = snapshots.get(to);
                if (senderSnapshot == null || receiverSnapshot == null) {
                    connection.rollback();
                    return Optional.empty();
                }

                final long senderCoins = senderSnapshot.balance(CurrencyType.COINS);
                final long receiverCoins = receiverSnapshot.balance(CurrencyType.COINS);

                if (senderCoins < amount) {
                    connection.rollback();
                    return Optional.empty();
                }

                if (receiverCoins + netAmount > maxCoins) {
                    connection.rollback();
                    return Optional.empty();
                }

                updateCoins(connection, from, senderCoins - amount);
                updateCoins(connection, to, receiverCoins + netAmount);

                if (logTransactions) {
                    insertTransaction(connection, from, TransactionType.TRANSFER, amount, senderCoins - amount, reason);
                    insertTransaction(connection, to, TransactionType.COINS_ADD, netAmount, receiverCoins + netAmount, reason);
                }

                connection.commit();
                return Optional.of(new TransferResult(senderCoins - amount, receiverCoins + netAmount, netAmount, taxAmount));
            } catch (final SQLException exception) {
                connection.rollback();
                logger.log(Level.SEVERE, "Failed to execute transfer", exception);
            } finally {
                connection.setAutoCommit(initialAutoCommit);
            }
        } catch (final SQLException exception) {
            logger.log(Level.SEVERE, "Unable to open connection for transfer", exception);
        }
        return Optional.empty();
    }

    private long calculateTax(final long amount, final double taxPercentage) {
        if (taxPercentage <= 0) {
            return 0L;
        }
        final long computed = Math.round(amount * (taxPercentage / 100.0));
        return Math.min(computed, amount);
    }

    private BalanceSnapshot fetchSnapshot(final Connection connection, final UUID uuid) throws SQLException {
        final String query = "SELECT coins, tokens FROM players WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final long coins = resultSet.getLong("coins");
                    final long tokens = resultSet.getLong("tokens");
                    return new BalanceSnapshot(uuid, coins, tokens);
                }
            }
        }
        return null;
    }

    private Map<UUID, BalanceSnapshot> fetchSnapshots(final Connection connection, final UUID first, final UUID second)
            throws SQLException {
        final Map<UUID, BalanceSnapshot> snapshots = new HashMap<>();
        final String query = "SELECT uuid, coins, tokens FROM players WHERE uuid = ? OR uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, first.toString());
            statement.setString(2, second.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                    final long coins = resultSet.getLong("coins");
                    final long tokens = resultSet.getLong("tokens");
                    snapshots.put(uuid, new BalanceSnapshot(uuid, coins, tokens));
                }
            }
        }
        return snapshots;
    }

    private void updateCoins(final Connection connection, final UUID uuid, final long newBalance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE players SET coins = ? WHERE uuid = ?")) {
            statement.setLong(1, newBalance);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }

    private void insertTransaction(final Connection connection, final UUID uuid, final TransactionType type,
                                    final long amount, final long balanceAfter, final String reason) throws SQLException {
        final String query = "INSERT INTO transactions (player_uuid, transaction_type, amount, balance_after, reason, timestamp) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, type.name());
            statement.setLong(3, amount);
            statement.setLong(4, balanceAfter);
            statement.setString(5, reason);
            statement.setTimestamp(6, java.sql.Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private TransactionType determineTransactionType(final CurrencyType currencyType, final long delta) {
        if (currencyType.isCoins()) {
            return delta > 0 ? TransactionType.COINS_ADD : TransactionType.COINS_REMOVE;
        }
        return delta > 0 ? TransactionType.TOKENS_ADD : TransactionType.TOKENS_REMOVE;
    }

    private record BalanceSnapshot(UUID uuid, long coins, long tokens) {
        long balance(final CurrencyType currencyType) {
            return currencyType.isCoins() ? coins : tokens;
        }
    }

    public record TransferResult(long senderBalanceAfter, long receiverBalanceAfter, long transferredAmount, long taxAmount) {
    }
}
