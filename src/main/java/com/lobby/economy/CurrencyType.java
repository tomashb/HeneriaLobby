package com.lobby.economy;

public enum CurrencyType {
    COINS,
    TOKENS;

    public boolean isCoins() {
        return this == COINS;
    }
}
