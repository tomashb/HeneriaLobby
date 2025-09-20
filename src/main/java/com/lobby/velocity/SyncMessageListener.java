package com.lobby.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.lobby.LobbyPlugin;
import com.lobby.economy.EconomyManager;
import com.lobby.social.friends.FriendManager;
import com.lobby.velocity.message.EconomyUpdateMessage;
import com.lobby.velocity.message.FriendUpdateMessage;
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
                case "FRIEND_UPDATE" -> handleFriendUpdate(data);
                case "STATS_UPDATE" -> handleStatsUpdate(data);
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

    private void handleFriendUpdate(final String data) {
        final FriendManager friendManager = plugin.getFriendManager();
        if (friendManager == null) {
            return;
        }
        final FriendUpdateMessage message = FriendUpdateMessage.deserialize(data);
        // For now, simply invalidate caches by reloading the manager.
        // Remote updates are processed asynchronously, so a full reload keeps data consistent.
        friendManager.reload();
        plugin.getLogger().fine("Processed friend update " + message.getAction() + " for " + message.getPlayerUuid());
    }

    private void handleStatsUpdate(final String data) {
        // Stats synchronization is optional and depends on an external implementation.
        // Log the received payload for debugging purposes without further processing.
        plugin.getLogger().fine("Received stats update payload: " + data);
    }
}
