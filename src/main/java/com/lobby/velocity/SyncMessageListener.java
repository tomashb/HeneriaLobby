package com.lobby.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.lobby.LobbyPlugin;
import com.lobby.economy.EconomyManager;
import com.lobby.velocity.message.EconomyUpdateMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.logging.Level;

public class SyncMessageListener implements PluginMessageListener {

    private final LobbyPlugin plugin;
    private final VelocityManager velocityManager;

    public SyncMessageListener(final VelocityManager velocityManager) {
        this.velocityManager = velocityManager;
        this.plugin = velocityManager.getPlugin();
    }

    @Override
    public void onPluginMessageReceived(final String channel, final Player player, final byte[] message) {
        if (!velocityManager.getMessagingChannel().equalsIgnoreCase(channel)) {
            return;
        }
        final ByteArrayDataInput input = ByteStreams.newDataInput(message);
        final String type = input.readUTF();
        final String data = input.readUTF();
        try {
            switch (type.toUpperCase()) {
                case "ECONOMY_UPDATE" -> handleEconomyUpdate(data);
                case "FRIEND_UPDATE" -> handleDeprecatedSocialUpdate(data);
                case "STATS_UPDATE" -> handleStatsUpdate(data);
                case "SERVER_STATUS" -> handleServerStatusUpdate(data);
                default -> plugin.getLogger().fine("Unknown sync message type received: " + type);
            }
        } catch (final Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to process sync message " + type + ": " + data, exception);
        }
    }

    private void handleEconomyUpdate(final String data) {
        final EconomyManager economyManager = plugin.getEconomyManager();
        if (economyManager == null) {
            return;
        }
        final EconomyUpdateMessage message = EconomyUpdateMessage.deserialize(data);
        economyManager.synchronizeBalances(message.getPlayerUuid(), message.getCoins(), message.getTokens());
    }

    private void handleDeprecatedSocialUpdate(final String data) {
        plugin.getLogger().fine("Ignored deprecated social update payload: " + data);
    }

    private void handleStatsUpdate(final String data) {
        // Stats synchronization is optional and depends on an external implementation.
        // Log the received payload for debugging purposes without further processing.
        plugin.getLogger().fine("Received stats update payload: " + data);
    }

    private void handleServerStatusUpdate(final String data) {
        if (data == null || data.isBlank()) {
            return;
        }
        final var placeholderCache = plugin.getServerPlaceholderCache();
        if (placeholderCache == null) {
            return;
        }
        final String[] segments = data.split(";\\s*");
        for (final String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            final String[] parts = segment.split(":");
            if (parts.length < 2) {
                continue;
            }
            final String serverId = parts[0];
            if (serverId == null || serverId.isBlank()) {
                continue;
            }
            try {
                final int playerCount = Integer.parseInt(parts[1]);
                placeholderCache.updateServerPlayerCount(serverId, playerCount);
                if (parts.length >= 3) {
                    final int activeGames = Integer.parseInt(parts[2]);
                    placeholderCache.updateActiveGames(serverId, activeGames);
                }
            } catch (final NumberFormatException exception) {
                plugin.getLogger().fine("Ignoring malformed server status entry: " + segment);
            }
        }
    }
}
