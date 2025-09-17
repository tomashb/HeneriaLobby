package com.lobby.events;

import com.lobby.LobbyPlugin;
import com.lobby.core.PlayerDataManager;
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

    public PlayerJoinLeaveEvent(final LobbyPlugin plugin, final PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        playerDataManager.handlePlayerJoin(player);
        sendWelcomeMessage(player);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        playerDataManager.handlePlayerQuit(player);
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
