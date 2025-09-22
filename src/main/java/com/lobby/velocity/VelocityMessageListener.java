package com.lobby.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Arrays;

public class VelocityMessageListener implements PluginMessageListener {

    private final VelocityManager velocityManager;

    public VelocityMessageListener(final VelocityManager velocityManager) {
        this.velocityManager = velocityManager;
    }

    @Override
    public void onPluginMessageReceived(final String channel, final Player player, final byte[] message) {
        if (!"BungeeCord".equalsIgnoreCase(channel)) {
            return;
        }
        final ByteArrayDataInput input = ByteStreams.newDataInput(message);
        final String subChannel = input.readUTF();
        if ("PlayerCount".equalsIgnoreCase(subChannel)) {
            final String serverName = input.readUTF();
            final int count = input.readInt();
            velocityManager.updateServerPlayerCount(serverName, count);
            return;
        }
        if ("PlayerList".equalsIgnoreCase(subChannel)) {
            final String serverName = input.readUTF();
            final String players = input.readUTF();
            velocityManager.updateServerPlayerList(serverName, players);
            return;
        }
        if ("GetServers".equalsIgnoreCase(subChannel)) {
            final String[] servers = input.readUTF().split(", ?");
            velocityManager.updateAvailableServers(Arrays.asList(servers));
        }
    }
}
