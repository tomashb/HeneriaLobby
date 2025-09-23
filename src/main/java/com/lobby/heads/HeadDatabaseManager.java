package com.lobby.heads;

import com.lobby.LobbyPlugin;
import com.lobby.utils.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for interactions with the HeadDatabase plugin.
 * <p>
 * The implementation relies on reflection to avoid a hard dependency on the HeadDatabase API while still
 * providing caching, fallback materials and optional debug logging.
 */
public class HeadDatabaseManager {

    private static final String HDB_PLUGIN_NAME = "HeadDatabase";
    private static final String HDB_API_CLASS = "me.arcaniax.hdb.api.HeadDatabaseAPI";

    private final LobbyPlugin plugin;
    private final Map<String, ItemStack> headCache = new ConcurrentHashMap<>();
    private final Set<String> headBlacklist = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedBlacklistedHeads = ConcurrentHashMap.newKeySet();

    private volatile Object apiInstance;
    private volatile Method getItemHeadMethod;
    private volatile boolean headDatabaseEnabled;
    private volatile boolean debugLogging;
    private volatile boolean missingPluginLogged;

    public HeadDatabaseManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reload the manager by clearing the cache and re-evaluating the HeadDatabase availability.
     */
    public synchronized void reload() {
        headCache.clear();
        apiInstance = null;
        getItemHeadMethod = null;
        headDatabaseEnabled = false;
        missingPluginLogged = false;
        loadConfigurationOptions();
        initializeHeadDatabase();
    }

    /**
     * Toggle verbose logging for debugging purposes.
     *
     * @param enabled true to enable verbose logging, false otherwise
     */
    public void setDebugLogging(final boolean enabled) {
        debugLogging = enabled;
    }

    /**
     * Returns whether the HeadDatabase plugin is currently available.
     *
     * @return true if the plugin is enabled and the API could be accessed, false otherwise
     */
    public boolean isHeadDatabaseEnabled() {
        ensureInitialized();
        return headDatabaseEnabled;
    }

    /**
     * Obtain a custom head for the given identifier. The identifier can either be a HeadDatabase id prefixed with
     * {@code hdb:} or the name of a vanilla {@link Material}. When the head cannot be retrieved, the provided
     * fallback material (or a player head by default) is used instead.
     *
     * @param headId           the identifier of the head to fetch
     * @param fallbackMaterial optional fallback material when the head cannot be resolved
     * @return a clone-safe {@link ItemStack} representing the requested head or fallback
     */
    public ItemStack getHead(final String headId, final Material fallbackMaterial) {
        final String trimmedId = headId == null ? "" : headId.trim();
        final Material resolvedFallback = fallbackMaterial != null ? fallbackMaterial : Material.PLAYER_HEAD;
        final String cacheKey = createCacheKey(trimmedId, resolvedFallback);

        final ItemStack cached = headCache.get(cacheKey);
        if (cached != null) {
            debug("Cache hit for head " + trimmedId + " (fallback=" + resolvedFallback + ")");
            return cached.clone();
        }

        ItemStack resolved = null;

        if (!trimmedId.isEmpty() && trimmedId.toLowerCase(Locale.ROOT).startsWith("hdb:")) {
            if (isHeadBlacklisted(trimmedId)) {
                handleBlacklistedHead(trimmedId);
            } else {
                resolved = getHeadFromDatabase(trimmedId.substring(4));
            }
        } else if (!trimmedId.isEmpty()) {
            resolved = resolveVanillaMaterial(trimmedId);
        }

        if (resolved == null) {
            debug("Using fallback material for head " + trimmedId + " -> " + resolvedFallback);
            resolved = createFallbackHead(resolvedFallback);
        }

        headCache.put(cacheKey, resolved.clone());
        return resolved;
    }

    /**
     * Shortcut for fetching a HeadDatabase head with a default player head fallback.
     *
     * @param headId identifier of the head
     * @return the resolved head item stack
     */
    public ItemStack getHead(final String headId) {
        return getHead(headId, Material.PLAYER_HEAD);
    }

    /**
     * Fetch a player head for the provided player. The result is cached by player name to improve performance.
     *
     * @param player the player for which to build a head item
     * @return a player specific head
     */
    public ItemStack getPlayerHead(final Player player) {
        if (player == null) {
            return createFallbackHead(Material.PLAYER_HEAD);
        }
        final String cacheKey = "player:" + player.getUniqueId();
        final ItemStack cached = headCache.get(cacheKey);
        if (cached != null) {
            return cached.clone();
        }

        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }

        headCache.put(cacheKey, head.clone());
        return head;
    }

    /**
     * Fetch a player head for the provided offline player.
     *
     * @param player the offline player reference
     * @return a head item for the given player or a generic fallback
     */
    public ItemStack getPlayerHead(final OfflinePlayer player) {
        if (player == null) {
            return createFallbackHead(Material.PLAYER_HEAD);
        }
        final UUID uniqueId = player.getUniqueId();
        final String cacheKey = uniqueId != null ? "player:" + uniqueId : "player:" + player.getName();
        final ItemStack cached = headCache.get(cacheKey);
        if (cached != null) {
            return cached.clone();
        }

        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }

        headCache.put(cacheKey, head.clone());
        return head;
    }

    /**
     * Clear the internal head cache.
     */
    public void clearCache() {
        headCache.clear();
    }

    private void loadConfigurationOptions() {
        headBlacklist.clear();
        warnedBlacklistedHeads.clear();
        debugLogging = false;
        if (plugin == null) {
            return;
        }
        try {
            final var config = plugin.getConfig();
            if (config != null) {
                debugLogging = config.getBoolean("head_database.debug", false);
                final List<String> blacklistEntries = config.getStringList("head_database.blacklist");
                for (String entry : blacklistEntries) {
                    final String normalized = normalizeHeadId(entry);
                    if (!normalized.isEmpty()) {
                        headBlacklist.add(normalized);
                    }
                }
            }
        } catch (final Exception exception) {
            LogUtils.warning(plugin, "Unable to load HeadDatabase settings: " + exception.getMessage());
        }
    }

    private boolean isHeadBlacklisted(final String headId) {
        final String normalized = normalizeHeadId(headId);
        return !normalized.isEmpty() && headBlacklist.contains(normalized);
    }

    private void handleBlacklistedHead(final String headId) {
        final String normalized = normalizeHeadId(headId);
        if (normalized.isEmpty()) {
            return;
        }
        if (warnedBlacklistedHeads.add(normalized)) {
            LogUtils.warning(plugin, "HeadDatabase id '" + normalized + "' is blacklisted. Using fallback head.");
        }
    }

    private void addRuntimeBlacklist(final String headId) {
        final String normalized = normalizeHeadId(headId);
        if (normalized.isEmpty()) {
            return;
        }
        if (headBlacklist.add(normalized)) {
            debug("Runtime blacklisted head " + normalized);
        }
    }

    private String normalizeHeadId(final String headId) {
        if (headId == null) {
            return "";
        }
        final String normalized = headId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.startsWith("hdb:") ? normalized.substring(4) : normalized;
    }

    private String createCacheKey(final String headId, final Material fallbackMaterial) {
        return headId.toLowerCase(Locale.ROOT) + '|' + fallbackMaterial.name();
    }

    private ItemStack resolveVanillaMaterial(final String materialName) {
        final Material material = Material.matchMaterial(materialName);
        if (material == null) {
            LogUtils.warning(plugin, "Unknown material '" + materialName + "' requested from HeadDatabaseManager.");
            return null;
        }
        return new ItemStack(material);
    }

    private ItemStack getHeadFromDatabase(final String rawId) {
        if (!ensureInitialized()) {
            debug("HeadDatabase unavailable when requesting id " + rawId);
            return null;
        }

        if (rawId == null || rawId.isBlank()) {
            return null;
        }

        if (isHeadBlacklisted(rawId)) {
            handleBlacklistedHead(rawId);
            return null;
        }

        try {
            final Object result = getItemHeadMethod.invoke(apiInstance, rawId);
            if (result instanceof ItemStack itemStack) {
                debug("Fetched head from HeadDatabase id=" + rawId);
                return itemStack;
            }
            LogUtils.warning(plugin, "HeadDatabase returned an unexpected result type for id " + rawId + '.');
            addRuntimeBlacklist(rawId);
            handleBlacklistedHead(rawId);
        } catch (final Exception exception) {
            LogUtils.warning(plugin, "Failed to fetch head '" + rawId + "' from HeadDatabase: " + exception.getMessage());
            addRuntimeBlacklist(rawId);
            handleBlacklistedHead(rawId);
        }

        return null;
    }

    private ItemStack createFallbackHead(final Material fallbackMaterial) {
        final ItemStack fallback = new ItemStack(fallbackMaterial);
        if (fallbackMaterial == Material.PLAYER_HEAD) {
            final SkullMeta meta = (SkullMeta) fallback.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cTête non disponible");
                fallback.setItemMeta(meta);
            }
        }
        return fallback;
    }

    private boolean ensureInitialized() {
        if (headDatabaseEnabled && apiInstance != null && getItemHeadMethod != null) {
            return true;
        }
        initializeHeadDatabase();
        return headDatabaseEnabled && apiInstance != null && getItemHeadMethod != null;
    }

    private synchronized void initializeHeadDatabase() {
        if (headDatabaseEnabled && apiInstance != null && getItemHeadMethod != null) {
            return;
        }

        final Plugin headDatabasePlugin = Bukkit.getPluginManager().getPlugin(HDB_PLUGIN_NAME);
        if (headDatabasePlugin == null || !headDatabasePlugin.isEnabled()) {
            if (!missingPluginLogged) {
                LogUtils.warning(plugin, "HeadDatabase plugin not found or disabled. Falling back to default heads.");
                missingPluginLogged = true;
            }
            headDatabaseEnabled = false;
            apiInstance = null;
            getItemHeadMethod = null;
            return;
        }

        missingPluginLogged = false;

        try {
            final Class<?> apiClass = Class.forName(HDB_API_CLASS);
            try {
                final Method getApiMethod = apiClass.getMethod("getAPI");
                apiInstance = getApiMethod.invoke(null);
            } catch (final NoSuchMethodException ignored) {
                final Constructor<?> constructor = apiClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                apiInstance = constructor.newInstance();
            }

            if (apiInstance == null) {
                LogUtils.warning(plugin, "Unable to obtain HeadDatabase API instance. Heads will use fallbacks.");
                headDatabaseEnabled = false;
                return;
            }

            getItemHeadMethod = apiInstance.getClass().getMethod("getItemHead", String.class);
            headDatabaseEnabled = true;
            LogUtils.info(plugin, "HeadDatabase integration initialised (" + headDatabasePlugin.getDescription().getVersion()
                    + ")");
        } catch (final Exception exception) {
            LogUtils.warning(plugin, "Failed to initialize HeadDatabase integration: " + exception.getMessage());
            apiInstance = null;
            getItemHeadMethod = null;
            headDatabaseEnabled = false;
        }
    }

    private void debug(final String message) {
        if (debugLogging) {
            LogUtils.info(plugin, "[HeadDatabase] " + Objects.requireNonNullElse(message, ""));
        }
    }
}

