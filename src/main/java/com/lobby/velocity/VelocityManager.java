package com.lobby.velocity;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.lobby.LobbyPlugin;
import com.lobby.economy.EconomyManager;
import com.lobby.velocity.message.EconomyUpdateMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class VelocityManager {

    private static final String LEGACY_CHANNEL = "BungeeCord";
    private static final String MODERN_CHANNEL = "bungeecord:main";
    private static final String DEFAULT_MESSAGING_CHANNEL = "lobbycore:sync";

    private final LobbyPlugin plugin;
    private final Map<String, VelocityServerInfo> servers = new ConcurrentHashMap<>();
    private final Map<String, VelocityServerInfo> serversByProxyName = new ConcurrentHashMap<>();
    private final Map<String, Integer> serverPlayerCounts = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> serverPlayerLists = new ConcurrentHashMap<>();
    private final Map<String, String> playerServerIndex = new ConcurrentHashMap<>();
    private final VelocityMessageListener velocityMessageListener;
    private final SyncMessageListener syncMessageListener;
    private final String messagingChannel;
    private final boolean enabled;
    private final boolean syncEconomy;
    private final boolean syncStats;
    private final int syncIntervalSeconds;
    private BukkitTask syncTask;

    public VelocityManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        final FileConfiguration configuration = Optional.ofNullable(plugin.getConfigManager())
                .map(configManager -> configManager.getVelocityConfig())
                .orElse(null);
        if (configuration == null) {
            plugin.getLogger().warning("Velocity configuration could not be loaded. Disabling proxy features.");
            messagingChannel = DEFAULT_MESSAGING_CHANNEL;
            enabled = false;
            syncEconomy = false;
            syncStats = false;
            syncIntervalSeconds = 0;
            velocityMessageListener = new VelocityMessageListener(this);
            syncMessageListener = new SyncMessageListener(this);
            return;
        }
        this.messagingChannel = resolveMessagingChannel(configuration.getString("velocity.messaging_channel"));
        this.enabled = configuration.getBoolean("velocity.enabled", true);
        this.syncEconomy = configuration.getBoolean("sync.economy", true);
        this.syncStats = configuration.getBoolean("sync.stats", false);
        this.syncIntervalSeconds = Math.max(0, configuration.getInt("sync.interval_seconds", 30));
        this.velocityMessageListener = new VelocityMessageListener(this);
        this.syncMessageListener = new SyncMessageListener(this);
        loadServerConfiguration(configuration.getConfigurationSection("servers"));
        if (enabled) {
            registerPluginChannels();
            startSyncTask();
        }
    }

    private void loadServerConfiguration(final ConfigurationSection section) {
        servers.clear();
        serversByProxyName.clear();
        serverPlayerLists.clear();
        playerServerIndex.clear();
        if (section == null) {
            plugin.getLogger().warning("No servers defined in velocity configuration.");
            return;
        }
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection serverSection = section.getConfigurationSection(key);
            if (serverSection == null) {
                continue;
            }
            final VelocityServerInfo info = new VelocityServerInfo(key, serverSection);
            servers.put(info.getId(), info);
            serversByProxyName.put(normalize(info.getName()), info);
        }
        plugin.getLogger().info("Loaded " + servers.size() + " proxy servers from velocity configuration.");
    }

    private void registerPluginChannels() {
        try {
            validateChannelFormat(messagingChannel);
        } catch (final IllegalArgumentException exception) {
            plugin.getLogger().severe("Failed to register proxy messaging channel: " + exception.getMessage());
            throw exception;
        }
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, LEGACY_CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, MODERN_CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, messagingChannel);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, LEGACY_CHANNEL, velocityMessageListener);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, messagingChannel, syncMessageListener);
    }

    private void startSyncTask() {
        if (syncIntervalSeconds <= 0) {
            return;
        }
        syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                requestServerInfo();
                syncOnlinePlayersData();
            } catch (final Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Velocity synchronization task failed", exception);
            }
        }, 20L, syncIntervalSeconds * 20L);
    }

    public void shutdown() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, LEGACY_CHANNEL, velocityMessageListener);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, messagingChannel, syncMessageListener);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, messagingChannel);
    }

    public LobbyPlugin getPlugin() {
        return plugin;
    }

    public String getMessagingChannel() {
        return messagingChannel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Collection<VelocityServerInfo> getServers() {
        return Collections.unmodifiableCollection(servers.values());
    }

    public void sendPlayerToServer(final Player player, final String serverId) {
        if (player == null) {
            return;
        }
        if (!enabled) {
            plugin.getServerManager().sendPlayerToServer(player, serverId);
            return;
        }
        final VelocityServerInfo info = servers.get(normalize(serverId));
        if (info == null) {
            player.sendMessage("§cServeur introuvable: " + serverId);
            return;
        }
        if (!info.isEnabled()) {
            player.sendMessage("§cLe serveur " + info.getDisplayName() + " §cest actuellement indisponible.");
            return;
        }
        player.sendMessage("§aTéléportation vers " + info.getDisplayName() + "§a...");
        syncPlayerDataBeforeTransfer(player, info.getName());
        final ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(info.getName());
        player.sendPluginMessage(plugin, LEGACY_CHANNEL, out.toByteArray());
        player.sendPluginMessage(plugin, MODERN_CHANNEL, out.toByteArray());
        plugin.getLogger().info("Sending player " + player.getName() + " to server: " + info.getName());
    }

    public void requestServerInfo() {
        if (!enabled || servers.isEmpty()) {
            return;
        }
        final Player anyPlayer = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (anyPlayer == null) {
            return;
        }
        final ByteArrayDataOutput serversRequest = ByteStreams.newDataOutput();
        serversRequest.writeUTF("GetServers");
        anyPlayer.sendPluginMessage(plugin, LEGACY_CHANNEL, serversRequest.toByteArray());
        for (final VelocityServerInfo info : servers.values()) {
            final ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("PlayerCount");
            out.writeUTF(info.getName());
            anyPlayer.sendPluginMessage(plugin, LEGACY_CHANNEL, out.toByteArray());

            final ByteArrayDataOutput listRequest = ByteStreams.newDataOutput();
            listRequest.writeUTF("PlayerList");
            listRequest.writeUTF(info.getName());
            anyPlayer.sendPluginMessage(plugin, LEGACY_CHANNEL, listRequest.toByteArray());
        }
    }

    public void updateServerPlayerCount(final String serverName, final int playerCount) {
        if (serverName == null) {
            return;
        }
        serverPlayerCounts.put(normalize(serverName), Math.max(0, playerCount));
        final var placeholderCache = plugin.getServerPlaceholderCache();
        if (placeholderCache != null) {
            placeholderCache.updateServerPlayerCount(serverName, playerCount);
        }
    }

    public void updateAvailableServers(final Collection<String> availableServers) {
        if (availableServers == null) {
            return;
        }
        final List<String> normalized = availableServers.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(this::normalize)
                .toList();
        for (final VelocityServerInfo info : servers.values()) {
            final boolean online = normalized.contains(normalize(info.getName()));
            info.setEnabled(online);
        }
    }

    public void updateServerPlayerList(final String serverName, final String players) {
        if (serverName == null) {
            return;
        }
        final String normalizedServer = normalize(serverName);
        final Set<String> parsedPlayers = ConcurrentHashMap.newKeySet();
        if (players != null && !players.isBlank()) {
            final String[] split = players.split(",\\s*");
            for (final String raw : split) {
                final String trimmed = raw == null ? "" : raw.trim();
                if (!trimmed.isEmpty()) {
                    parsedPlayers.add(trimmed.toLowerCase(Locale.ROOT));
                }
            }
        }
        final Set<String> previous = serverPlayerLists.get(normalizedServer);
        if (previous != null) {
            for (final String name : previous) {
                if (!parsedPlayers.contains(name)) {
                    playerServerIndex.remove(name, normalizedServer);
                }
            }
        }
        serverPlayerLists.put(normalizedServer, parsedPlayers);
        for (final String playerName : parsedPlayers) {
            playerServerIndex.put(playerName, normalizedServer);
        }
    }

    public int getServerPlayerCount(final String serverId) {
        return serverPlayerCounts.getOrDefault(normalize(serverId), 0);
    }

    public int getTotalPlayerCount() {
        return serverPlayerCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    public boolean isServerOnline(final String serverId) {
        final VelocityServerInfo info = servers.get(normalize(serverId));
        return info != null && info.isEnabled();
    }

    public Optional<String> findPlayerServerDisplayName(final UUID playerUuid, final String playerName) {
        if (!enabled) {
            return Optional.empty();
        }
        final String key = normalizePlayerKey(playerUuid, playerName);
        if (key == null) {
            return Optional.empty();
        }
        final String serverKey = playerServerIndex.get(key);
        if (serverKey == null) {
            return Optional.empty();
        }
        final VelocityServerInfo info = resolveServerInfo(serverKey);
        if (info != null) {
            return Optional.of(info.getDisplayName());
        }
        return Optional.of(serverKey);
    }

    public Optional<String> findPlayerServerId(final UUID playerUuid, final String playerName) {
        if (!enabled) {
            return Optional.empty();
        }
        final String key = normalizePlayerKey(playerUuid, playerName);
        if (key == null) {
            return Optional.empty();
        }
        final String serverKey = playerServerIndex.get(key);
        if (serverKey == null) {
            return Optional.empty();
        }
        final VelocityServerInfo info = resolveServerInfo(serverKey);
        if (info != null) {
            return Optional.of(info.getId());
        }
        return Optional.of(serverKey);
    }

    public boolean isPlayerOnlineProxy(final UUID playerUuid, final String playerName) {
        if (!enabled) {
            return false;
        }
        final String key = normalizePlayerKey(playerUuid, playerName);
        if (key == null) {
            return false;
        }
        return playerServerIndex.containsKey(key);
    }

    public void broadcastEconomyUpdate(final Player player, final long coins, final long tokens) {
        if (!enabled || player == null || !syncEconomy) {
            return;
        }
        final EconomyUpdateMessage message = new EconomyUpdateMessage(player.getUniqueId(), player.getName(), coins, tokens);
        sendSyncMessage("ECONOMY_UPDATE", message.serialize());
    }

    public void broadcastStatsUpdate(final UUID playerUuid, final String serializedData) {
        if (!enabled || !syncStats || serializedData == null) {
            return;
        }
        final String payload = playerUuid + "|" + serializedData.replace('|', ':');
        sendSyncMessage("STATS_UPDATE", payload);
    }

    private void sendSyncMessage(final String type, final String data) {
        if (!enabled) {
            return;
        }
        final Player anyPlayer = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (anyPlayer == null) {
            return;
        }
        final ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(type);
        out.writeUTF(data);
        anyPlayer.sendPluginMessage(plugin, messagingChannel, out.toByteArray());
    }

    private String resolveMessagingChannel(final String configuredChannel) {
        final String channel = configuredChannel == null ? DEFAULT_MESSAGING_CHANNEL
                : configuredChannel.trim().toLowerCase(Locale.ROOT);
        try {
            validateChannelFormat(channel);
            return channel;
        } catch (final IllegalArgumentException exception) {
            if (!DEFAULT_MESSAGING_CHANNEL.equals(channel)) {
                plugin.getLogger().warning("Invalid messaging channel '" + configuredChannel + "': "
                        + exception.getMessage() + ". Falling back to '" + DEFAULT_MESSAGING_CHANNEL + "'.");
            }
            return DEFAULT_MESSAGING_CHANNEL;
        }
    }

    private void validateChannelFormat(final String channel) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Channel name cannot be null or empty");
        }
        final String[] parts = channel.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Channel name must contain exactly one ':' separator");
        }
        final String namespace = parts[0];
        final String name = parts[1];
        if (namespace.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("Channel namespace and name must be non-empty");
        }
        if (!namespace.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid namespace '" + namespace + "'");
        }
        if (!name.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid channel name '" + name + "'");
        }
    }

    private void syncPlayerDataBeforeTransfer(final Player player, final String targetServer) {
        if (player == null) {
            return;
        }
        if (syncEconomy) {
            final EconomyManager economyManager = plugin.getEconomyManager();
            if (economyManager != null) {
                final long coins = economyManager.getCoins(player.getUniqueId());
                final long tokens = economyManager.getTokens(player.getUniqueId());
                broadcastEconomyUpdate(player, coins, tokens);
            }
        }
        plugin.getLogger().fine("Synchronized data for " + player.getName() + " before transfer to " + targetServer);
    }

    private void syncOnlinePlayersData() {
        if (!enabled) {
            return;
        }
        if (syncEconomy) {
            final EconomyManager economyManager = plugin.getEconomyManager();
            if (economyManager != null) {
                for (final Player player : Bukkit.getOnlinePlayers()) {
                    final long coins = economyManager.getCoins(player.getUniqueId());
                    final long tokens = economyManager.getTokens(player.getUniqueId());
                    broadcastEconomyUpdate(player, coins, tokens);
                }
            }
        }
    }

    private String normalize(final String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String normalizePlayerKey(final UUID playerUuid, final String playerName) {
        if (playerName != null && !playerName.isBlank()) {
            return playerName.trim().toLowerCase(Locale.ROOT);
        }
        if (playerUuid != null) {
            final Player online = Bukkit.getPlayer(playerUuid);
            if (online != null) {
                return online.getName().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private VelocityServerInfo resolveServerInfo(final String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        final String normalized = normalize(key);
        VelocityServerInfo info = servers.get(normalized);
        if (info != null) {
            return info;
        }
        info = serversByProxyName.get(normalized);
        if (info != null) {
            return info;
        }
        return servers.values().stream()
                .filter(server -> server.getName().equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
    }
}
