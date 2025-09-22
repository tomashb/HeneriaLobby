package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuManager implements Listener {

    private final LobbyPlugin plugin;
    private final Map<UUID, Menu> openMenus = new ConcurrentHashMap<>();
    private final Map<String, ConfigurationSection> menuDefinitions = new ConcurrentHashMap<>();
    private final MenuDesignProvider menuDesignProvider;

    public MenuManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.menuDesignProvider = new MenuDesignProvider(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        reloadMenus();
    }

    public boolean openMenu(final Player player, final String menuId) {
        if (player == null || menuId == null || menuId.isBlank()) {
            return false;
        }
        final String normalizedId = menuId.toLowerCase(java.util.Locale.ROOT);
        ConfigurationSection menuSection = menuDefinitions.get(normalizedId);
        if (menuSection == null) {
            reloadMenus();
            menuSection = menuDefinitions.get(normalizedId);
            if (menuSection == null) {
                MessageUtils.sendConfigMessage(player, "menus.not_found", Map.of("menu", menuId));
                return false;
            }
        }

        final Menu menu = new ConfiguredMenu(plugin, normalizedId, menuSection, menuDesignProvider);
        final UUID uuid = player.getUniqueId();
        if (shouldPreloadAsync(menuSection)) {
            openMenus.put(uuid, menu);
            player.closeInventory();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                preloadMenuData(uuid, menuSection);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    final Player target = Bukkit.getPlayer(uuid);
                    if (target == null || !target.isOnline()) {
                        openMenus.remove(uuid);
                        return;
                    }
                    menu.open(target);
                    if (menu.getInventory() == null) {
                        openMenus.remove(uuid);
                    }
                });
            });
            return true;
        }

        openMenus.put(uuid, menu);
        menu.open(player);
        if (menu.getInventory() == null) {
            openMenus.remove(uuid);
            return false;
        }
        return true;
    }

    public Optional<Menu> getOpenMenu(final UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(openMenus.get(uuid));
    }

    public void closeAll() {
        final var uuids = openMenus.keySet().stream().toList();
        uuids.forEach(uuid -> {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        });
        openMenus.clear();
    }

    public void reloadMenus() {
        menuDefinitions.clear();
        loadMenusFromMainConfig();
        loadMenusFromDirectory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final Menu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            return;
        }
        event.setCancelled(true);
        final Inventory topInventory = event.getView().getTopInventory();
        if (topInventory == null || !topInventory.equals(menu.getInventory())) {
            return;
        }
        menu.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (openMenus.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        final Menu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            return;
        }
        final Inventory inventory = menu.getInventory();
        if (inventory == null || inventory.equals(event.getInventory())) {
            openMenus.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    private void loadMenusFromMainConfig() {
        final FileConfiguration menusConfig = plugin.getConfigManager().getMenusConfig();
        final ConfigurationSection menusSection = menusConfig.getConfigurationSection("menus");
        if (menusSection == null) {
            return;
        }
        for (String key : menusSection.getKeys(false)) {
            final ConfigurationSection section = menusSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            menuDefinitions.put(key.toLowerCase(java.util.Locale.ROOT), section);
        }
    }

    private void loadMenusFromDirectory() {
        final File menusDirectory = ensureMenusDirectory();
        final File[] files = menusDirectory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            return;
        }
        for (File file : files) {
            final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection menuSection = configuration.getConfigurationSection("menu");
            if (menuSection == null) {
                menuSection = configuration;
            }
            final String id = menuSection.getString("id", stripExtension(file.getName()));
            if (id == null || id.isBlank()) {
                continue;
            }
            menuDefinitions.put(id.toLowerCase(java.util.Locale.ROOT), menuSection);
        }
    }

    private boolean shouldPreloadAsync(final ConfigurationSection menuSection) {
        return menuSection != null && menuSection.getBoolean("async_preload", false);
    }

    private void preloadMenuData(final UUID uuid, final ConfigurationSection menuSection) {
        if (uuid == null || menuSection == null) {
            return;
        }
        final Set<String> sources = collectPlaceholderSources(menuSection);
        if (sources.isEmpty()) {
            return;
        }
        final Set<String> placeholders = extractPlaceholders(sources);
        if (placeholders.isEmpty()) {
            return;
        }

        preloadEconomy(uuid, placeholders);
        preloadStats(uuid, placeholders);
        preloadSettings(uuid, placeholders);
    }

    private void preloadEconomy(final UUID uuid, final Set<String> placeholders) {
        if (placeholders.stream().noneMatch(placeholder -> placeholder.startsWith("%player_")
                && (placeholder.contains("coins")
                || placeholder.contains("tokens")
                || placeholder.contains("playtime")
                || placeholder.contains("first_join")
                || placeholder.contains("last_join")))) {
            return;
        }
        if (plugin.getEconomyManager() != null) {
            plugin.getEconomyManager().getPlayerData(uuid);
        }
    }

    private void preloadStats(final UUID uuid, final Set<String> placeholders) {
        if (placeholders.stream().noneMatch(placeholder -> placeholder.startsWith("%stats_"))) {
            return;
        }
        final var statsManager = plugin.getStatsManager();
        if (statsManager == null) {
            return;
        }
        boolean globalRequested = false;
        final Set<String> gameTypes = new HashSet<>();
        for (String placeholder : placeholders) {
            if (!placeholder.startsWith("%stats_")) {
                continue;
            }
            final String trimmed = placeholder.substring(1, placeholder.length() - 1);
            final String[] parts = trimmed.split("_");
            if (parts.length < 3) {
                continue;
            }
            final String scope = parts[1];
            if ("global".equalsIgnoreCase(scope)) {
                globalRequested = true;
            } else {
                gameTypes.add(scope.toUpperCase(java.util.Locale.ROOT));
            }
        }
        if (globalRequested) {
            statsManager.getGlobalStats(uuid);
        }
        for (String gameType : gameTypes) {
            statsManager.getPlayerStats(uuid, gameType);
        }
    }

    private void preloadSettings(final UUID uuid, final Set<String> placeholders) {
        if (placeholders.stream().noneMatch(placeholder -> placeholder.startsWith("%setting_")
                || placeholder.startsWith("%lang_"))) {
            return;
        }
        if (plugin.getPlayerSettingsManager() != null) {
            plugin.getPlayerSettingsManager().getPlayerSettings(uuid);
        }
    }

    private Set<String> collectPlaceholderSources(final ConfigurationSection section) {
        final Set<String> values = new HashSet<>();
        collectValues(section, values);
        return values;
    }

    private void collectValues(final Object value, final Set<String> sink) {
        if (value == null) {
            return;
        }
        if (value instanceof ConfigurationSection configurationSection) {
            for (String key : configurationSection.getKeys(false)) {
                collectValues(configurationSection.get(key), sink);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                collectValues(element, sink);
            }
            return;
        }
        if (value instanceof String string && string.contains("%")) {
            sink.add(string);
        }
    }

    private Set<String> extractPlaceholders(final Set<String> values) {
        final Set<String> placeholders = new HashSet<>();
        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("%([^%]+)%");
        for (String value : values) {
            final java.util.regex.Matcher matcher = pattern.matcher(value);
            while (matcher.find()) {
                placeholders.add('%' + matcher.group(1) + '%');
            }
        }
        return placeholders;
    }

    private File ensureMenusDirectory() {
        final File directory = new File(plugin.getDataFolder(), "config/menus");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().severe("Unable to create config/menus directory for menu definitions.");
        }
        final Set<String> defaults = Set.of(
                "jeux_menu.yml",
                "profil_menu.yml",
                "shop_menu.yml",
                "cosmetiques_menu.yml",
                "hub_menu.yml",
                "settings_menu.yml",
                "language_menu.yml",
                "friends_menu.yml",
                "friend_management.yml",
                "groups_menu.yml",
                "clan_menu.yml",
                "stats_menu.yml",
                "notifications_menu.yml",
                "audio_settings_menu.yml"
        );
        for (String fileName : defaults) {
            final File target = new File(directory, fileName);
            if (!target.exists()) {
                plugin.saveResource("config/menus/" + fileName, false);
            }
        }
        return directory;
    }

    private String stripExtension(final String fileName) {
        final int index = fileName.lastIndexOf('.');
        if (index <= 0) {
            return fileName;
        }
        return fileName.substring(0, index);
    }
}
