package com.lobby.data;

import com.lobby.economy.TransactionType;

import java.time.Instant;
import java.util.UUID;

public record TransactionData(
        long id,
        UUID playerUuid,
        TransactionType type,
        long amount,
        long balanceAfter,
        String reason,
        Instant timestamp
) {
}
