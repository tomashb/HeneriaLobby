package com.lobby.economy.event;

import com.lobby.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class EconomyTransactionEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final CurrencyType currencyType;
    private final long delta;
    private final long newBalance;
    private final String reason;

    public EconomyTransactionEvent(final UUID playerUuid, final CurrencyType currencyType, final long delta,
                                   final long newBalance, final String reason) {
        super(!Bukkit.isPrimaryThread());
        this.playerUuid = playerUuid;
        this.currencyType = currencyType;
        this.delta = delta;
        this.newBalance = newBalance;
        this.reason = reason;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public CurrencyType getCurrencyType() {
        return currencyType;
    }

    public long getDelta() {
        return delta;
    }

    public long getNewBalance() {
        return newBalance;
    }

    public String getReason() {
        return reason;
    }

    public Player getPlayer() {
        return playerUuid == null ? null : Bukkit.getPlayer(playerUuid);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
