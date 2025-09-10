package com.heneria.lobby.economy;

import com.heneria.lobby.player.PlayerData;
import com.heneria.lobby.player.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Simple economy manager to handle coin balance for players.
 */
public class EconomyManager {

    private final JavaPlugin plugin;
    private final PlayerDataManager dataManager;

    public EconomyManager(JavaPlugin plugin, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public long getCoins(UUID uuid) {
        PlayerData data = dataManager.getPlayerData(uuid);
        return data != null ? data.getCoins() : 0L;
    }

    public void addCoins(UUID uuid, long amount) {
        if (amount == 0) {
            return;
        }
        PlayerData data = dataManager.getPlayerData(uuid);
        if (data != null) {
            long newBalance = Math.max(0, data.getCoins() + amount);
            dataManager.setCoins(uuid, newBalance);
        }
    }

    public boolean hasEnoughCoins(Player player, long amount) {
        return getCoins(player.getUniqueId()) >= amount;
    }

    public void removeCoins(Player player, long amount) {
        addCoins(player.getUniqueId(), -amount);
    }

    public void startPassiveRewardTask(long intervalTicks, long rewardAmount) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                addCoins(player.getUniqueId(), rewardAmount);
            }
        }, intervalTicks, intervalTicks);
    }
}
