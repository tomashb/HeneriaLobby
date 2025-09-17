package com.lobby.economy;

import java.util.List;
import java.util.UUID;

public interface EconomyAPI {

    long getCoins(UUID player);

    long getTokens(UUID player);

    boolean hasCoins(UUID player, long amount);

    boolean hasTokens(UUID player, long amount);

    void addCoins(UUID player, long amount, String reason);

    void removeCoins(UUID player, long amount, String reason);

    void addTokens(UUID player, long amount, String reason);

    void removeTokens(UUID player, long amount, String reason);

    boolean transfer(UUID from, UUID to, long amount, String reason);

    List<String> getTopCoins(int limit);

    List<String> getTopTokens(int limit);
}
