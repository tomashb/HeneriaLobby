package com.lobby.servers;

import com.lobby.LobbyPlugin;
import com.lobby.velocity.VelocityManager;
import com.lobby.velocity.VelocityServerInfo;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight cache for server related placeholder values. The cache keeps track of
 * player counts and active game counts for every configured proxy server. Values
 * are refreshed on a short asynchronous schedule to avoid expensive placeholder
 * evaluations on the main thread.
 */
public class ServerPlaceholderCache {

    private static final long REFRESH_INTERVAL_TICKS = 100L; // 5 seconds

    private final LobbyPlugin plugin;
    private final Map<String, Integer> playerCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> activeGames = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;

    public ServerPlaceholderCache(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the asynchronous refresh task. The task periodically asks the
     * {@link VelocityManager} for the latest proxy information which in turn
     * updates the cache through the public update methods.
     */
    public void start() {
        stop();
        final VelocityManager velocityManager = plugin.getVelocityManager();
        if (velocityManager == null) {
            return;
        }
        initialiseKnownServers(velocityManager.getServers());
        if (!velocityManager.isEnabled()) {
            return;
        }
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                velocityManager.requestServerInfo();
                velocityManager.getServers().forEach(info -> updateServerPlayerCount(info.getId(),
                        velocityManager.getServerPlayerCount(info.getId())));
            } catch (final Exception exception) {
                plugin.getLogger().warning("Failed to refresh server placeholder cache: " + exception.getMessage());
            }
        }, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    }

    /**
     * Cancels the scheduled refresh task and clears cached data.
     */
    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public void shutdown() {
        stop();
        playerCounts.clear();
        activeGames.clear();
    }

    public int getServerPlayerCount(final String serverId) {
        if (serverId == null) {
            return 0;
        }
        return playerCounts.getOrDefault(normalize(serverId), 0);
    }

    public int getActiveGames(final String serverId) {
        if (serverId == null) {
            return 0;
        }
        return activeGames.getOrDefault(normalize(serverId), 0);
    }

    public void updateServerPlayerCount(final String serverId, final int count) {
        if (serverId == null) {
            return;
        }
        playerCounts.put(normalize(serverId), Math.max(0, count));
    }

    public void updateActiveGames(final String serverId, final int count) {
        if (serverId == null) {
            return;
        }
        activeGames.put(normalize(serverId), Math.max(0, count));
    }

    private void initialiseKnownServers(final Collection<?> servers) {
        if (servers == null || servers.isEmpty()) {
            return;
        }
        servers.stream()
                .filter(Objects::nonNull)
                .map(object -> {
                    if (object instanceof VelocityServerInfo info) {
                        return info.getId();
                    }
                    return object.toString();
                })
                .map(this::normalize)
                .forEach(id -> {
                    playerCounts.putIfAbsent(id, 0);
                    activeGames.putIfAbsent(id, 0);
                });
    }

    private String normalize(final String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
