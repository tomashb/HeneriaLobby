package com.lobby.servers;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.lobby.LobbyPlugin;
import com.lobby.core.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ServerManager {

    private static final String CHANNEL_LEGACY = "BungeeCord";
    private static final String CHANNEL_MODERN = "bungeecord:main";

    private final LobbyPlugin plugin;
    private final Map<String, ServerInfo> servers = new HashMap<>();

    public ServerManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        servers.clear();
        final ConfigManager configManager = plugin.getConfigManager();
        if (configManager == null) {
            plugin.getLogger().warning("ConfigManager not initialized, unable to load servers configuration.");
            return;
        }

        final FileConfiguration serversConfig = configManager.getServersConfig();
        if (serversConfig == null) {
            plugin.getLogger().warning("servers.yml configuration is missing or could not be loaded.");
            return;
        }

        final ConfigurationSection root = serversConfig.getConfigurationSection("servers");
        if (root == null) {
            plugin.getLogger().warning("No servers defined in servers.yml");
            return;
        }

        for (final String key : root.getKeys(false)) {
            final ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            final ServerInfo info = new ServerInfo(key, section);
            servers.put(key.toLowerCase(), info);
        }

        plugin.getLogger().info("Loaded " + servers.size() + " servers from configuration.");
    }

    public ServerInfo getServer(final String id) {
        if (id == null) {
            return null;
        }
        return servers.get(id.toLowerCase());
    }

    public Collection<ServerInfo> getServers() {
        return Collections.unmodifiableCollection(servers.values());
    }

    public void sendPlayerToServer(final Player player, final String serverId) {
        if (player == null) {
            return;
        }
        final ServerInfo info = getServer(serverId);
        if (info == null) {
            player.sendMessage("§cServeur introuvable: " + serverId);
            return;
        }
        if (!info.isEnabled()) {
            player.sendMessage("§cLe serveur " + info.getDisplayName() + " §cest actuellement indisponible.");
            return;
        }

        player.sendMessage("§aConnexion au serveur " + info.getDisplayName() + "§a...");
        sendToVelocityServer(player, info.getServerName());
    }

    private void sendToVelocityServer(final Player player, final String serverName) {
        try {
            final ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverName);

            player.sendPluginMessage(plugin, CHANNEL_LEGACY, out.toByteArray());
            player.sendPluginMessage(plugin, CHANNEL_MODERN, out.toByteArray());
            plugin.getLogger().info("Sending player " + player.getName() + " to server: " + serverName);
        } catch (final Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to send player " + player.getName() + " to server " + serverName, exception);
            player.sendMessage("§cImpossible de se connecter au serveur pour le moment.");
        }
    }

    public void broadcastToServer(final Collection<? extends Player> players, final String serverId) {
        final ServerInfo serverInfo = getServer(serverId);
        if (serverInfo == null || !serverInfo.isEnabled()) {
            return;
        }
        for (final Player player : players) {
            sendToVelocityServer(player, serverInfo.getServerName());
        }
    }

    public void sendAllToServer(final String serverId) {
        broadcastToServer(Bukkit.getOnlinePlayers(), serverId);
    }
}
