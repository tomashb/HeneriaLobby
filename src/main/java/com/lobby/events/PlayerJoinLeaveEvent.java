package com.lobby.events;

import com.lobby.LobbyPlugin;
import com.lobby.core.PlayerDataManager;
import com.lobby.economy.EconomyManager;
import com.lobby.utils.MessageUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public class PlayerJoinLeaveEvent implements Listener {

    private final LobbyPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final EconomyManager economyManager;

    public PlayerJoinLeaveEvent(final LobbyPlugin plugin, final PlayerDataManager playerDataManager,
                                final EconomyManager economyManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.economyManager = economyManager;
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        playerDataManager.handlePlayerJoin(player);
        if (economyManager != null) {
            economyManager.handlePlayerJoin(player.getUniqueId(), player.getName());
        }
        sendWelcomeMessage(player);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        playerDataManager.handlePlayerQuit(player);
        if (economyManager != null) {
            economyManager.handlePlayerQuit(player.getUniqueId());
        }
    }

    private void sendWelcomeMessage(final Player player) {
        final FileConfiguration messagesConfig = plugin.getConfigManager().getMessagesConfig();
        final List<String> lines = messagesConfig.getStringList("welcome_message");
        if (lines.isEmpty()) {
            return;
        }
        lines.stream()
                .map(line -> line.replace("%player_name%", player.getName()))
                .forEach(line -> player.sendMessage(MessageUtils.applyPrefix(line)));
    }
}
