package com.lobby.scoreboard;

import com.lobby.LobbyPlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LuckPermsPrefixResolver {

    private static final long REFRESH_INTERVAL_MILLIS = 30_000L;

    private final LobbyPlugin plugin;
    private final LuckPerms luckPerms;
    private final QueryOptions queryOptions;
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRequest = new ConcurrentHashMap<>();
    private final String defaultPrefix;

    public LuckPermsPrefixResolver(final LobbyPlugin plugin, final String defaultPrefix) {
        this.plugin = plugin;
        this.luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
        this.queryOptions = luckPerms != null ? luckPerms.getContextManager().getStaticQueryOptions() : null;
        this.defaultPrefix = Objects.requireNonNullElse(defaultPrefix, "§7Joueur");
    }

    public String getPrefix(final UUID uuid, final String username) {
        if (uuid == null) {
            return defaultPrefix;
        }
        if (shouldRefresh(uuid)) {
            refresh(uuid, username);
        }
        final String cached = cache.get(uuid);
        if (cached == null || cached.isBlank()) {
            return defaultPrefix;
        }
        return cached;
    }

    public void forceRefresh(final UUID uuid, final String username) {
        if (uuid == null) {
            return;
        }
        lastRequest.put(uuid, System.currentTimeMillis());
        refresh(uuid, username);
    }

    public CompletableFuture<String> fetchPrefixAsync(final UUID uuid, final String username) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(defaultPrefix);
        }
        if (luckPerms == null) {
            cache.put(uuid, defaultPrefix);
            lastRequest.put(uuid, System.currentTimeMillis());
            return CompletableFuture.completedFuture(defaultPrefix);
        }
        return luckPerms.getUserManager().loadUser(uuid, username)
                .thenApply(user -> {
                    try {
                        final String resolved = resolvePrefix(user);
                        cache.put(uuid, resolved);
                        return resolved;
                    } finally {
                        if (user != null) {
                            luckPerms.getUserManager().saveUser(user);
                        }
                    }
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to load LuckPerms data for " + username + ": "
                            + throwable.getMessage());
                    cache.put(uuid, defaultPrefix);
                    return defaultPrefix;
                })
                .whenComplete((prefix, throwable) -> lastRequest.put(uuid, System.currentTimeMillis()));
    }

    private boolean shouldRefresh(final UUID uuid) {
        final long now = System.currentTimeMillis();
        final Long last = lastRequest.get(uuid);
        if (last == null || (now - last) >= REFRESH_INTERVAL_MILLIS) {
            lastRequest.put(uuid, now);
            return true;
        }
        return false;
    }

    private void refresh(final UUID uuid, final String username) {
        if (uuid == null) {
            return;
        }
        fetchPrefixAsync(uuid, username);
    }

    private String resolvePrefix(final User user) {
        if (user == null || queryOptions == null) {
            return defaultPrefix;
        }
        final CachedMetaData metaData = user.getCachedData().getMetaData(queryOptions);
        if (metaData == null) {
            return defaultPrefix;
        }
        final String prefix = metaData.getPrefix();
        if (prefix == null || prefix.isBlank()) {
            return defaultPrefix;
        }
        return ChatColor.translateAlternateColorCodes('&', prefix);
    }

    public void invalidate(final UUID uuid) {
        if (uuid == null) {
            return;
        }
        cache.remove(uuid);
        lastRequest.remove(uuid);
    }

    public void clear() {
        cache.clear();
        lastRequest.clear();
    }
}
