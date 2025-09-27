package com.lobby.events;

import com.lobby.LobbyPlugin;
import com.lobby.core.PlayerDataManager;
import com.lobby.economy.EconomyManager;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerJoinLeaveEvent implements Listener {

    private final LobbyPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final EconomyManager economyManager;

    public PlayerJoinLeaveEvent(final LobbyPlugin plugin,
                                final PlayerDataManager playerDataManager,
                                final EconomyManager economyManager) {
        this.plugin = plugin;
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

        final UUID playerUuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (plugin.getFriendCodeManager() == null) {
                return;
            }

            final String existingCode = plugin.getFriendCodeManager().getPlayerCode(playerUuid);
            if (existingCode != null) {
                return;
            }

            final String newCode = plugin.getFriendCodeManager().generateUniqueCode(playerUuid);
            if (newCode == null) {
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                final Player online = Bukkit.getPlayer(playerUuid);
                if (online != null) {
                    online.sendMessage("§a✅ Votre code d'ami a été généré : §2" + newCode);
                    online.sendMessage("§7Partagez-le pour que d'autres puissent vous ajouter !");
                }
            });
        });
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
