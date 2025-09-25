package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.heads.HeadDatabaseManager;
import com.lobby.servers.ServerPlaceholderCache;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles every expensive asset lookup required by menus. Heads from the Head
 * Database plugin are cached asynchronously while global placeholder values are
 * refreshed on a lightweight repeating task.
 */
public class AssetManager {

    private static final Set<String> PRELOADED_HEADS = Set.of(
            "hdb:14138",
            "hdb:12822",
            "hdb:32038",
            "hdb:23959",
            "hdb:35472",
            "hdb:9334",
            "hdb:9945",
            "hdb:9723",
            "hdb:8971",
            "hdb:12654",
            "hdb:25442",
            "hdb:2736",
            "hdb:1218",
            "hdb:18351",
            "hdb:23533",
            "hdb:23534",
            "hdb:23528",
            "hdb:31405",
            "hdb:31406",
            "hdb:31408",
            "hdb:47365",
            "hdb:47366",
            "hdb:52000",
            "hdb:13389",
            "hdb:1455",
            "hdb:32010"
    );
    private static final Map<String, String> SERVER_PLACEHOLDER_KEYS = Map.of(
            "%lobby_online_bedwars%", "bedwars",
            "%lobby_online_nexus%", "nexus",
            "%lobby_online_zombie%", "zombie",
            "%lobby_online_custom%", "custom"
    );
    private static final Map<String, String> STATIC_PLACEHOLDERS = Map.of(
            "%daily_reward_status%", "Disponible"
    );
    private static final String DEFAULT_PLACEHOLDER_VALUE = "0";

    private final LobbyPlugin plugin;
    private final Map<String, ItemStack> headCache = new ConcurrentHashMap<>();
    private final Map<String, String> globalPlaceholderCache = new ConcurrentHashMap<>();

    private BukkitTask headPreloadTask;
    private BukkitTask placeholderTask;

    public AssetManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        startPreloadTasks();
    }

    public void reload() {
        startPreloadTasks();
    }

    public void shutdown() {
        cancelTasks();
        headCache.clear();
        globalPlaceholderCache.clear();
    }

    public ItemStack getHead(final String id) {
        if (id == null || id.isBlank()) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
        final String normalized = id.trim().toLowerCase(Locale.ROOT);
        final ItemStack cached = headCache.computeIfAbsent(normalized, this::loadHeadInternal);
        return cached.clone();
    }

    public String getGlobalPlaceholder(final String key) {
        if (key == null || key.isBlank()) {
            return DEFAULT_PLACEHOLDER_VALUE;
        }
        return globalPlaceholderCache.getOrDefault(key, DEFAULT_PLACEHOLDER_VALUE);
    }

    public Map<String, String> snapshotPlaceholders() {
        return Collections.unmodifiableMap(globalPlaceholderCache);
    }

    private void startPreloadTasks() {
        cancelTasks();
        globalPlaceholderCache.putAll(STATIC_PLACEHOLDERS);
        preloadHeadsAsync();
        startOnlinePlayersTask();
    }

    private void cancelTasks() {
        if (headPreloadTask != null) {
            headPreloadTask.cancel();
            headPreloadTask = null;
        }
        if (placeholderTask != null) {
            placeholderTask.cancel();
            placeholderTask = null;
        }
    }

    private void preloadHeadsAsync() {
        final Set<String> headIds = discoverHeadIds();
        if (headIds.isEmpty()) {
            return;
        }
        headPreloadTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final HeadDatabaseManager headDatabaseManager = plugin.getHeadDatabaseManager();
            if (headDatabaseManager == null) {
                return;
            }
            for (String headId : headIds) {
                if (headId == null || headId.isBlank()) {
                    continue;
                }
                headCache.put(headId.toLowerCase(Locale.ROOT),
                        headDatabaseManager.getHead(headId).clone());
            }
        });
    }

    private void startOnlinePlayersTask() {
        final ServerPlaceholderCache placeholderCache = plugin.getServerPlaceholderCache();
        if (placeholderCache == null) {
            SERVER_PLACEHOLDER_KEYS.keySet()
                    .forEach(key -> globalPlaceholderCache.put(key, DEFAULT_PLACEHOLDER_VALUE));
            return;
        }
        SERVER_PLACEHOLDER_KEYS.keySet()
                .forEach(key -> globalPlaceholderCache.putIfAbsent(key, DEFAULT_PLACEHOLDER_VALUE));
        placeholderTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                SERVER_PLACEHOLDER_KEYS.forEach((placeholder, serverId) -> {
                    final int count = placeholderCache.getServerPlayerCount(serverId);
                    globalPlaceholderCache.put(placeholder, Integer.toString(Math.max(0, count)));
                });
            } catch (final Exception exception) {
                plugin.getLogger().warning("Failed to refresh menu placeholder cache: " + exception.getMessage());
            }
        }, 20L, 40L);
    }

    private Set<String> discoverHeadIds() {
        final Set<String> headIds = new HashSet<>(PRELOADED_HEADS);
        headIds.addAll(scanConfiguredHeadIds());
        return headIds;
    }

    private Set<String> scanConfiguredHeadIds() {
        final Set<String> collected = new HashSet<>();
        final File menusDirectory = new File(plugin.getDataFolder(), "config/menus");
        if (!menusDirectory.exists() || !menusDirectory.isDirectory()) {
            return collected;
        }
        final File[] files = menusDirectory.listFiles((dir, name) -> name != null && name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            return collected;
        }
        for (File file : files) {
            final YamlConfiguration configuration = new YamlConfiguration();
            try {
                configuration.load(file);
                collectHeadIdentifiers(configuration, collected);
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().warning("Unable to parse menu configuration '" + file.getName() + "': "
                        + exception.getMessage());
            }
        }
        return collected;
    }

    private void collectHeadIdentifiers(final ConfigurationSection section, final Set<String> sink) {
        if (section == null || sink == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            final Object value = section.get(key);
            if (value instanceof ConfigurationSection nested) {
                collectHeadIdentifiers(nested, sink);
            } else if (value instanceof String stringValue) {
                addHeadIfValid(stringValue, sink);
            } else if (value instanceof Iterable<?> iterable) {
                for (Object element : iterable) {
                    if (element instanceof String stringElement) {
                        addHeadIfValid(stringElement, sink);
                    }
                }
            }
        }
    }

    private void addHeadIfValid(final String rawValue, final Set<String> sink) {
        if (rawValue == null || sink == null) {
            return;
        }
        final String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        final String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("hdb:")) {
            sink.add(normalized);
        }
    }

    private ItemStack loadHeadInternal(final String identifier) {
        final HeadDatabaseManager headDatabaseManager = plugin.getHeadDatabaseManager();
        if (headDatabaseManager == null) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
        final ItemStack resolved = headDatabaseManager.getHead(identifier);
        return Objects.requireNonNullElseGet(resolved, () -> new ItemStack(Material.PLAYER_HEAD));
    }
}
