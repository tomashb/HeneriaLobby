package com.lobby.tablist;

import com.lobby.LobbyPlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight cache around LuckPerms to resolve prefixes, suffixes and group weights
 * for players displayed in the tablist. Heavy calls to the LuckPerms API are throttled
 * and executed asynchronously to avoid impacting the main thread.
 */
final class LuckPermsTablistResolver {

    private static final long REFRESH_INTERVAL_MILLIS = 30_000L;

    private final LobbyPlugin plugin;
    private final LuckPerms luckPerms;
    private final QueryOptions queryOptions;
    private final Map<UUID, PlayerTablistData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRefresh = new ConcurrentHashMap<>();
    private final PlayerTablistData defaultData;

    LuckPermsTablistResolver(final LobbyPlugin plugin, final String defaultPrefix) {
        this.plugin = plugin;
        this.luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
        this.queryOptions = luckPerms != null ? luckPerms.getContextManager().getStaticQueryOptions() : null;
        final String resolvedPrefix = Objects.requireNonNullElse(defaultPrefix, ChatColor.GRAY + "Joueur");
        this.defaultData = new PlayerTablistData(resolvedPrefix, "", 0);
    }

    PlayerTablistData getMeta(final UUID uuid, final String username) {
        if (uuid == null) {
            return defaultData;
        }
        if (shouldRefresh(uuid)) {
            refresh(uuid, username);
        }
        return cache.getOrDefault(uuid, defaultData);
    }

    void forceRefresh(final UUID uuid, final String username) {
        if (uuid == null) {
            return;
        }
        lastRefresh.put(uuid, System.currentTimeMillis());
        refresh(uuid, username);
    }

    void invalidate(final UUID uuid) {
        if (uuid == null) {
            return;
        }
        cache.remove(uuid);
        lastRefresh.remove(uuid);
    }

    void clear() {
        cache.clear();
        lastRefresh.clear();
    }

    private boolean shouldRefresh(final UUID uuid) {
        final long now = System.currentTimeMillis();
        final Long last = lastRefresh.get(uuid);
        if (last == null || (now - last) >= REFRESH_INTERVAL_MILLIS) {
            lastRefresh.put(uuid, now);
            return true;
        }
        return false;
    }

    private void refresh(final UUID uuid, final String username) {
        if (uuid == null) {
            return;
        }
        if (luckPerms == null) {
            cache.put(uuid, defaultData);
            return;
        }
        final CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(uuid, username);
        userFuture.thenCompose(user -> {
            final PlayerTablistData previous = cache.getOrDefault(uuid, defaultData);
            final PlayerTablistData resolved = resolveFromUser(user, previous);
            final CompletableFuture<PlayerTablistData> metaFuture;
            if (user == null) {
                metaFuture = CompletableFuture.completedFuture(resolved);
            } else {
                final String primaryGroup = user.getPrimaryGroup();
                if (primaryGroup == null || primaryGroup.isBlank()) {
                    metaFuture = CompletableFuture.completedFuture(resolved);
                } else {
                    metaFuture = luckPerms.getGroupManager().loadGroup(primaryGroup)
                            .exceptionally(throwable -> {
                                plugin.getLogger().warning("Failed to load LuckPerms group '" + primaryGroup + "' for "
                                        + username + ": " + throwable.getMessage());
                                return null;
                            })
                            .thenApply(group -> mergeGroupData(resolved, group, previous));
                }
            }
            return metaFuture.whenComplete((data, throwable) -> {
                if (throwable != null) {
                    plugin.getLogger().warning("Failed to resolve LuckPerms data for " + username + ": "
                            + throwable.getMessage());
                }
                if (user != null) {
                    luckPerms.getUserManager().saveUser(user);
                }
            });
        }).thenAccept(data -> cache.put(uuid, Objects.requireNonNullElse(data, defaultData)))
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to load LuckPerms data for " + username + ": "
                            + throwable.getMessage());
                    cache.put(uuid, defaultData);
                    return null;
                });
    }

    private PlayerTablistData resolveFromUser(final User user, final PlayerTablistData previous) {
        if (user == null || queryOptions == null) {
            return previous;
        }
        final var metaData = user.getCachedData().getMetaData(queryOptions);
        if (metaData == null) {
            return previous;
        }
        final String prefix = colorize(metaData.getPrefix());
        final String suffix = colorize(metaData.getSuffix());
        final String resolvedPrefix = prefix.isBlank() ? previous.prefix() : prefix;
        return new PlayerTablistData(resolvedPrefix, suffix, previous.weight());
    }

    private PlayerTablistData mergeGroupData(final PlayerTablistData base,
                                            final Group group,
                                            final PlayerTablistData previous) {
        if (group == null) {
            return new PlayerTablistData(base.prefix(), base.suffix(), previous.weight());
        }
        final int weight = group.getWeight().orElse(previous.weight());
        return new PlayerTablistData(base.prefix(), base.suffix(), weight);
    }

    private String colorize(final String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
