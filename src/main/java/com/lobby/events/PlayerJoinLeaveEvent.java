package com.lobby.events;

import com.lobby.core.PlayerDataManager;
import com.lobby.economy.EconomyManager;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinLeaveEvent implements Listener {

    private final PlayerDataManager playerDataManager;
    private final EconomyManager economyManager;

    public PlayerJoinLeaveEvent(final PlayerDataManager playerDataManager, final EconomyManager economyManager) {
        this.playerDataManager = playerDataManager;
        this.economyManager = economyManager;
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        event.setJoinMessage(null);
        playerDataManager.handlePlayerJoin(player);
        if (economyManager != null) {
            economyManager.handlePlayerJoin(player.getUniqueId(), player.getName());
        }
        broadcastConnectionMessage("&7[&a+&7] &a" + player.getName());
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        event.setQuitMessage(null);
        playerDataManager.handlePlayerQuit(player);
        if (economyManager != null) {
            economyManager.handlePlayerQuit(player.getUniqueId());
        }
        broadcastConnectionMessage("&7[&c-&7] &c" + player.getName());
    }

    private void broadcastConnectionMessage(final String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Bukkit.getOnlinePlayers().forEach(target ->
                target.sendMessage(MessageUtils.colorize(message)));
    }
}
