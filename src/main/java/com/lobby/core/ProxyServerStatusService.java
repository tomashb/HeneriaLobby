package com.lobby.core;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.lobby.LobbyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyServerStatusService implements PluginMessageListener {

    private static final String CHANNEL = "BungeeCord";
    private static final long UPDATE_PERIOD_TICKS = 200L; // 10 seconds

    private final LobbyPlugin plugin;
    private final Map<String, Integer> playerCounts = new ConcurrentHashMap<>();
    private final Set<String> trackedServers = ConcurrentHashMap.newKeySet();
    private final Map<String, String> serverNameMapping = new ConcurrentHashMap<>();
    private BukkitTask updateTask;

    public ProxyServerStatusService(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        startUpdateTask();
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        trackedServers.clear();
        serverNameMapping.clear();
        playerCounts.clear();
    }

    public void trackServer(final String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return;
        }
        final String key = serverName.toLowerCase(Locale.ROOT);
        serverNameMapping.put(key, serverName);
        if (trackedServers.add(key)) {
            requestPlayerCount(serverName);
        }
    }

    public int getCachedPlayerCount(final String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return 0;
        }
        final String key = serverName.toLowerCase(Locale.ROOT);
        return playerCounts.getOrDefault(key, 0);
    }

    @Override
    public void onPluginMessageReceived(final String channel, final Player player, final byte[] message) {
        if (!CHANNEL.equalsIgnoreCase(channel)) {
            return;
        }
        final ByteArrayDataInput input = ByteStreams.newDataInput(message);
        final String subChannel = input.readUTF();
        if (!"PlayerCount".equalsIgnoreCase(subChannel)) {
            return;
        }
        final String server = input.readUTF();
        final int count = input.readInt();
        playerCounts.put(server.toLowerCase(Locale.ROOT), Math.max(count, 0));
    }

    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::requestTrackedServers,
                20L, UPDATE_PERIOD_TICKS);
    }

    private void requestTrackedServers() {
        if (trackedServers.isEmpty()) {
            return;
        }
        final Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender == null) {
            return;
        }
        for (String key : trackedServers) {
            final String serverName = serverNameMapping.getOrDefault(key, key);
            sendPlayerCountRequest(sender, serverName);
        }
    }

    private void requestPlayerCount(final String serverName) {
        final Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender == null) {
            return;
        }
        sendPlayerCountRequest(sender, serverName);
    }

    private void sendPlayerCountRequest(final Player sender, final String serverName) {
        final ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("PlayerCount");
        output.writeUTF(serverName);
        sender.sendPluginMessage(plugin, CHANNEL, output.toByteArray());
    }
}

