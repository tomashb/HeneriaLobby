package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.npcs.ActionProcessor;
import com.lobby.utils.LogUtils;
import com.lobby.utils.MessageUtils;
import com.lobby.utils.PlaceholderUtils;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuManager implements Listener {

    private final LobbyPlugin plugin;
    private final Map<UUID, Menu> openMenus = new ConcurrentHashMap<>();
    private final Map<String, MenuDefinition> menuDefinitions = new ConcurrentHashMap<>();
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
        MenuDefinition definition = menuDefinitions.get(normalizedId);
        if (definition == null) {
            reloadMenus();
            definition = menuDefinitions.get(normalizedId);
            if (definition == null) {
                MessageUtils.sendConfigMessage(player, "menus.not_found", Map.of("menu", menuId));
                return false;
            }
        }

        final ConfigurationSection menuSection = definition.section();
        final Menu menu = new ConfiguredMenu(plugin, normalizedId, menuSection, menuDesignProvider);
        final UUID uuid = player.getUniqueId();
        final Set<String> placeholders = definition.placeholders();
        final boolean debugAsyncMenu = isAsyncDebugMenu(normalizedId);
        final boolean requiresAsync = definition.requiresAsyncPreload() || "profil_menu".equals(normalizedId);

        if (requiresAsync) {
            openMenuAsync(menu, uuid, placeholders, normalizedId, debugAsyncMenu);
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
        final String menuTitle = event.getView().getTitle();
        plugin.getLogger().info("[DEBUG 1] Clic détecté dans le menu: " + menuTitle);

        final int slot = event.getSlot();
        final List<String> actions = menu.getActionsForSlot(event.getRawSlot());
        final String actionDescription;
        if (actions.isEmpty()) {
            actionDescription = "aucune action";
        } else if (actions.size() == 1) {
            actionDescription = actions.get(0);
        } else {
            actionDescription = String.join(", ", actions);
        }
        plugin.getLogger().info("[DEBUG 2] Joueur: " + player.getName() + " | Slot: " + slot + " | Action: '" + actionDescription + "'");

        if (actions.isEmpty()) {
            return;
        }

        final var npcManager = plugin.getNpcManager();
        final ActionProcessor actionProcessor = npcManager != null ? npcManager.getActionProcessor() : null;

        final List<String> nonMenuActions = new ArrayList<>();
        for (String rawAction : actions) {
            if (rawAction == null) {
                continue;
            }
            final String trimmedAction = rawAction.trim();
            if (trimmedAction.isEmpty()) {
                continue;
            }
            if (trimmedAction.regionMatches(true, 0, "[MENU]", 0, 6)) {
                final String targetArgument = trimmedAction.substring(6).trim();
                final String resolvedTarget = PlaceholderUtils.applyPlaceholders(plugin, targetArgument, player);
                final String menuId = resolvedTarget != null ? resolvedTarget.trim() : "";
                if (menuId.isEmpty()) {
                    LogUtils.warning(plugin, "Menu action triggered without a valid target for player '" + player.getName() + "'.");
                    continue;
                }
        plugin.getLogger().info("[DEBUG 3] Tentative d'ouverture du sous-menu: " + menuId);
        if (isAsyncDebugMenu(menuId)) {
            plugin.getLogger().info("[DEBUG A] Clic sur " + menuId + " reçu. Démarrage de la TÂCHE ASYNCHRONE.");
        }
        Bukkit.getScheduler().runTask(plugin, () -> openMenu(player, menuId));
        continue;
            }
            nonMenuActions.add(trimmedAction);
        }

        if (nonMenuActions.isEmpty()) {
            return;
        }
        if (actionProcessor == null) {
            LogUtils.warning(plugin, "Attempted to execute menu actions but no ActionProcessor is available.");
            return;
        }
        actionProcessor.processActions(nonMenuActions, player, null);
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
            storeMenuDefinition(key, section);
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
            storeMenuDefinition(id, menuSection);
        }
    }

    private void storeMenuDefinition(final String id, final ConfigurationSection section) {
        if (id == null || id.isBlank() || section == null) {
            return;
        }
        final Set<String> extracted = extractPlaceholders(collectPlaceholderSources(section));
        final Set<String> placeholders = extracted.isEmpty() ? Set.of() : Set.copyOf(extracted);
        final boolean asyncPreload = shouldPreloadAsync(section, placeholders);
        final String normalized = id.toLowerCase(java.util.Locale.ROOT);
        menuDefinitions.put(normalized, new MenuDefinition(section, placeholders, asyncPreload));
    }

    private boolean shouldPreloadAsync(final ConfigurationSection menuSection, final Set<String> placeholders) {
        if (menuSection != null && menuSection.getBoolean("async_preload", false)) {
            return true;
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return false;
        }
        for (String placeholder : placeholders) {
            if (requiresAsyncPreload(placeholder)) {
                return true;
            }
        }
        return false;
    }

    private boolean requiresAsyncPreload(final String placeholder) {
        if (placeholder == null || placeholder.isBlank()) {
            return false;
        }
        final String normalized = placeholder.toLowerCase(Locale.ROOT);
        return normalized.startsWith("%player_")
                || normalized.startsWith("%stats_")
                || normalized.startsWith("%setting_")
                || normalized.startsWith("%lang_")
                || normalized.startsWith("%friend")
                || normalized.startsWith("%friends")
                || normalized.startsWith("%group")
                || normalized.startsWith("%clan");
    }

    private void preloadMenuData(final UUID uuid, final Set<String> placeholders) {
        if (uuid == null || placeholders == null || placeholders.isEmpty()) {
            return;
        }

        preloadEconomy(uuid, placeholders);
        preloadStats(uuid, placeholders);
        preloadSettings(uuid, placeholders);
        preloadSocial(uuid, placeholders);
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

    private void preloadSocial(final UUID uuid, final Set<String> placeholders) {
        boolean requiresFriends = false;
        boolean requiresGroups = false;
        boolean requiresClans = false;
        for (String placeholder : placeholders) {
            if (placeholder == null) {
                continue;
            }
            final String normalized = placeholder.toLowerCase(Locale.ROOT);
            if (!requiresFriends && (normalized.startsWith("%friend") || normalized.startsWith("%friends"))) {
                requiresFriends = true;
            }
            if (!requiresGroups && normalized.startsWith("%group")) {
                requiresGroups = true;
            }
            if (!requiresClans && normalized.startsWith("%clan")) {
                requiresClans = true;
            }
        }

        if (requiresFriends) {
            final var friendManager = plugin.getFriendManager();
            if (friendManager != null) {
                friendManager.getFriendsList(uuid);
                friendManager.getPendingRequests(uuid);
                friendManager.countSentRequests(uuid);
                friendManager.getFriendSettings(uuid);
            }
        }

        if (requiresGroups) {
            final var groupManager = plugin.getGroupManager();
            if (groupManager != null) {
                groupManager.getGroupSettings(uuid);
                groupManager.getPlayerGroup(uuid);
                groupManager.countPendingInvitations(uuid);
                groupManager.countSentInvitations(uuid);
                groupManager.countCachedOpenGroups();
            }
        }

        if (requiresClans) {
            final var clanManager = plugin.getClanManager();
            if (clanManager != null) {
                clanManager.getPlayerClan(uuid);
                clanManager.countPendingInvitations(uuid);
                clanManager.countCachedOpenClans();
            }
        }
    }

    private void openMenuAsync(final Menu menu,
                               final UUID uuid,
                               final Set<String> placeholders,
                               final String normalizedId,
                               final boolean debugAsyncMenu) {
        openMenus.put(uuid, menu);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (debugAsyncMenu) {
                plugin.getLogger().info("[DEBUG B] Tâche ASYNC démarrée. Récupération des données BDD pour '" + normalizedId + "'...");
            }
            preloadMenuData(uuid, placeholders);
            if (debugAsyncMenu) {
                plugin.getLogger().info("[DEBUG C] Données BDD récupérées pour '" + normalizedId + "'. Retour au THREAD PRINCIPAL.");
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (debugAsyncMenu) {
                    plugin.getLogger().info("[DEBUG D] Tâche SYNC démarrée. Construction du menu '" + normalizedId + "'...");
                }
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
    }

    private boolean isAsyncDebugMenu(final String menuId) {
        if (menuId == null) {
            return false;
        }
        final String normalized = menuId.toLowerCase(Locale.ROOT);
        return "stats_menu".equals(normalized) || "profil_menu".equals(normalized);
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

    private record MenuDefinition(ConfigurationSection section,
                                  Set<String> placeholders,
                                  boolean requiresAsyncPreload) {
    }
}
