package com.heneria.lobby.menu;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.heneria.lobby.HeneriaLobbyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles plugin message communication with Velocity/Bungee to retrieve
 * server information such as player counts.
 */
public class ServerInfoManager implements PluginMessageListener {

    private final HeneriaLobbyPlugin plugin;
    private final Map<String, Integer> playerCounts = new ConcurrentHashMap<>();

    public ServerInfoManager(HeneriaLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public void requestPlayerCount(Player player, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("PlayerCount");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    public int getPlayerCount(String server) {
        return playerCounts.getOrDefault(server, -1);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!"BungeeCord".equals(channel)) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String sub = in.readUTF();
        if ("PlayerCount".equals(sub)) {
            String server = in.readUTF();
            int count = in.readInt();
            playerCounts.put(server, count);
        }
    }
}

