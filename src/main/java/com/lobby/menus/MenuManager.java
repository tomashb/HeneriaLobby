package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.heads.HeadDatabaseManager;
import com.lobby.servers.ServerPlaceholderCache;
import com.lobby.stats.GameStats;
import com.lobby.stats.GlobalStats;
import com.lobby.stats.StatsManager;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class MenuManager {

    private static final Set<String> SIMPLE_MENUS = Set.of("jeux_menu", "profil_menu");
    private static final Set<String> HEAVY_MENUS = Set.of("stats_detailed_menu");
    private static final DecimalFormat RATIO_FORMAT = new DecimalFormat("0.00");

    private final LobbyPlugin plugin;
    private final LegacyMenuManager legacyMenuManager;
    private final Map<String, CachedMenu> managedMenus = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> headCache = new ConcurrentHashMap<>();
    private final Map<String, String> globalPlaceholderCache = new ConcurrentHashMap<>();
    private final Map<UUID, SimpleMenuSession> openMenus = new ConcurrentHashMap<>();
    private final AtomicBoolean headPreloadScheduled = new AtomicBoolean(false);

    private BukkitTask placeholderRefreshTask;

    public MenuManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.legacyMenuManager = new LegacyMenuManager(plugin);
        this.globalPlaceholderCache.put("%lobby_online_bedwars%", "0");
        reloadMenus();
        preloadAllHeadsAsync();
        startGlobalPlaceholderTask();
    }

    public LegacyMenuManager getLegacyMenuManager() {
        return legacyMenuManager;
    }

    public boolean openMenu(final Player player, final String rawMenuId) {
        if (player == null || rawMenuId == null || rawMenuId.isBlank()) {
            return false;
        }
        final String menuId = rawMenuId.toLowerCase(Locale.ROOT);
        openMenus.remove(player.getUniqueId());
        if (SIMPLE_MENUS.contains(menuId)) {
            return buildAndOpenSimpleMenu(player, menuId);
        }
        if (HEAVY_MENUS.contains(menuId)) {
            return buildAndOpenHeavyMenu(player, menuId);
        }
        return legacyMenuManager.openMenu(player, rawMenuId);
    }

    public Optional<Menu> getOpenMenu(final UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        final SimpleMenuSession session = openMenus.get(uuid);
        if (session != null) {
            return Optional.of(session);
        }
        return legacyMenuManager.getOpenMenu(uuid);
    }

    public void closeAll() {
        for (SimpleMenuSession session : new ArrayList<>(openMenus.values())) {
            final Player player = Bukkit.getPlayer(session.owner());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        openMenus.clear();
        legacyMenuManager.closeAll();
    }

    public void reloadMenus() {
        loadManagedMenus();
        legacyMenuManager.reloadMenus();
        preloadAllHeadsAsync();
    }

    public boolean consumeFailureNotification(final UUID uuid) {
        return legacyMenuManager.consumeFailureNotification(uuid);
    }

    public void shutdown() {
        closeAll();
        if (placeholderRefreshTask != null) {
            placeholderRefreshTask.cancel();
            placeholderRefreshTask = null;
        }
        headCache.clear();
        globalPlaceholderCache.clear();
    }

    public Map<Integer, String> getActions(final UUID uuid) {
        final SimpleMenuSession session = openMenus.get(uuid);
        if (session == null) {
            return Map.of();
        }
        return session.actions();
    }

    public boolean isManagedMenuTitle(final String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        for (CachedMenu menu : managedMenus.values()) {
            if (menu.title().equals(title)) {
                return true;
            }
        }
        return false;
    }

    public boolean isMenuInventory(final UUID uuid, final Inventory inventory) {
        if (uuid == null || inventory == null) {
            return false;
        }
        final SimpleMenuSession session = openMenus.get(uuid);
        return session != null && session.getInventory().equals(inventory);
    }

    public void clearSession(final UUID uuid) {
        if (uuid != null) {
            openMenus.remove(uuid);
        }
    }

    private void loadManagedMenus() {
        managedMenus.clear();
        final File dataFolder = plugin.getDataFolder();
        final File menusFolder = new File(dataFolder, "config/menus");
        if (!menusFolder.exists()) {
            menusFolder.mkdirs();
        }
        for (String menuId : union(SIMPLE_MENUS, HEAVY_MENUS)) {
            final File file = new File(menusFolder, menuId + ".yml");
            final YamlConfiguration configuration = new YamlConfiguration();
            try {
                if (file.exists()) {
                    configuration.load(file);
                } else {
                    try (InputStream inputStream = plugin.getResource("config/menus/" + menuId + ".yml")) {
                        if (inputStream == null) {
                            continue;
                        }
                        configuration.load(new java.io.InputStreamReader(inputStream));
                    }
                }
            } catch (final Exception exception) {
                plugin.getLogger().warning("Impossible de charger le menu " + menuId + ": " + exception.getMessage());
                continue;
            }
            final ConfigurationSection section = configuration.getConfigurationSection("menu");
            if (section == null) {
                continue;
            }
            final CachedMenu cachedMenu = parseMenu(section);
            if (cachedMenu != null) {
                managedMenus.put(menuId, cachedMenu);
            }
        }
    }

    private CachedMenu parseMenu(final ConfigurationSection section) {
        final String id = section.getString("id");
        final String rawTitle = section.getString("title", "Menu");
        final String title = MessageUtils.colorize(rawTitle);
        final int size = Math.max(9, section.getInt("size", 27));
        final ConfigurationSection itemsSection = section.getConfigurationSection("items");
        final Map<Integer, MenuItemDefinition> items = new HashMap<>();
        final Map<Integer, String> actions = new HashMap<>();
        final List<Integer> decorSlots = new ArrayList<>();
        MenuItemDefinition decorItem = null;

        if (itemsSection != null) {
            final Object decorSlotObject = itemsSection.get("decor_slots");
            decorSlots.addAll(parseSlots(decorSlotObject));
            final ConfigurationSection decorSection = itemsSection.getConfigurationSection("decor_item");
            if (decorSection != null) {
                decorItem = parseItemDefinition(decorSection, decorSlots.stream().findFirst().orElse(-1));
            }

            for (String key : itemsSection.getKeys(false)) {
                if (key == null || key.equalsIgnoreCase("decor_slots") || key.equalsIgnoreCase("decor_item")) {
                    continue;
                }
                final ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection == null) {
                    continue;
                }
                final int slot = resolveSlot(key, itemSection.getInt("slot", -1));
                if (slot < 0) {
                    continue;
                }
                final MenuItemDefinition definition = parseItemDefinition(itemSection, slot);
                if (definition == null) {
                    continue;
                }
                items.put(slot, definition);
                final String action = resolvePrimaryAction(itemSection.getString("action"), itemSection.getStringList("actions"));
                if (action != null && !action.equalsIgnoreCase("[NONE]")) {
                    actions.put(slot, action);
                }
            }
        }
        return new CachedMenu(id, title, size, items, actions, decorSlots, decorItem);
    }

    private MenuItemDefinition parseItemDefinition(final ConfigurationSection section, final int slot) {
        final String material = section.getString("material", "STONE");
        final String head = section.getString("head");
        final String name = section.getString("name");
        final List<String> lore = section.getStringList("lore");
        final String action = resolvePrimaryAction(section.getString("action"), section.getStringList("actions"));
        final String storedAction = action != null && !action.equalsIgnoreCase("[NONE]") ? action : null;
        return new MenuItemDefinition(slot, material, head, name, lore, storedAction);
    }

    private String resolvePrimaryAction(final String single, final List<String> list) {
        if (single != null && !single.isBlank()) {
            return single.trim();
        }
        if (list != null) {
            for (String entry : list) {
                if (entry != null && !entry.isBlank()) {
                    return entry.trim();
                }
            }
        }
        return null;
    }

    private boolean buildAndOpenSimpleMenu(final Player player, final String menuId) {
        final CachedMenu menu = managedMenus.get(menuId);
        if (menu == null) {
            return legacyMenuManager.openMenu(player, menuId);
        }
        final Inventory inventory = buildInventory(menu, player, Map.of());
        if (inventory == null) {
            return false;
        }
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), new SimpleMenuSession(player.getUniqueId(), menuId, inventory, menu.actions()));
        return true;
    }

    private boolean buildAndOpenHeavyMenu(final Player player, final String menuId) {
        final CachedMenu menu = managedMenus.get(menuId);
        if (menu == null) {
            return legacyMenuManager.openMenu(player, menuId);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final Map<String, String> playerData = loadPlayerSpecificData(player);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player == null || !player.isOnline()) {
                    return;
                }
                final Inventory inventory = buildInventory(menu, player, playerData);
                if (inventory == null) {
                    return;
                }
                player.openInventory(inventory);
                openMenus.put(player.getUniqueId(), new SimpleMenuSession(player.getUniqueId(), menuId, inventory, menu.actions()));
            });
        });
        return true;
    }

    private Inventory buildInventory(final CachedMenu menu,
                                     final Player player,
                                     final Map<String, String> extraPlaceholders) {
        final Inventory inventory = Bukkit.createInventory(null, menu.size(), menu.title());
        final Map<String, String> placeholders = mergePlaceholders(player, extraPlaceholders);
        if (menu.decorItem() != null) {
            final ItemStack decor = resolveItem(menu.decorItem(), player, placeholders);
            for (Integer slot : menu.decorSlots()) {
                if (slot == null) {
                    continue;
                }
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, decor.clone());
                }
            }
        }
        for (Map.Entry<Integer, MenuItemDefinition> entry : menu.items().entrySet()) {
            final int slot = entry.getKey();
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            final ItemStack item = resolveItem(entry.getValue(), player, placeholders);
            inventory.setItem(slot, item);
        }
        return inventory;
    }

    private ItemStack resolveItem(final MenuItemDefinition definition,
                                  final Player player,
                                  final Map<String, String> placeholders) {
        ItemStack item = createBaseItem(definition, player);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (definition.name() != null) {
                meta.setDisplayName(applyPlaceholders(definition.name(), placeholders));
            }
            if (!definition.lore().isEmpty()) {
                final List<String> resolved = new ArrayList<>();
                for (String line : definition.lore()) {
                    resolved.add(applyPlaceholders(line, placeholders));
                }
                meta.setLore(resolved);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBaseItem(final MenuItemDefinition definition, final Player player) {
        final HeadDatabaseManager headManager = plugin.getHeadDatabaseManager();
        final String material = definition.material();
        if (definition.head() != null && !definition.head().isBlank()) {
            return resolveHead(definition.head(), headManager);
        }
        if (material != null && material.toLowerCase(Locale.ROOT).startsWith("hdb:")) {
            return resolveHead(material, headManager);
        }
        final Material vanilla = Material.matchMaterial(material == null ? "PLAYER_HEAD" : material, true);
        if (vanilla == null) {
            return new ItemStack(Material.STONE);
        }
        final ItemStack base = new ItemStack(vanilla);
        if (vanilla == Material.PLAYER_HEAD && player != null) {
            final ItemMeta meta = base.getItemMeta();
            if (meta instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(player);
                base.setItemMeta(skullMeta);
            }
        }
        return base;
    }

    private ItemStack resolveHead(final String rawId, final HeadDatabaseManager headManager) {
        final String id = rawId.trim();
        final ItemStack cached = headCache.get(id);
        if (cached != null) {
            return cached.clone();
        }
        if (headManager != null) {
            final ItemStack head = headManager.getHead(id);
            headCache.put(id, head.clone());
            return head;
        }
        return new ItemStack(Material.PLAYER_HEAD);
    }

    private Map<String, String> mergePlaceholders(final Player player, final Map<String, String> extra) {
        final Map<String, String> merged = new HashMap<>(globalPlaceholderCache);
        if (extra != null) {
            merged.putAll(extra);
        }
        if (player != null) {
            merged.putIfAbsent("%player_name%", player.getName());
        }
        return merged;
    }

    private String applyPlaceholders(final String input, final Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String result = input;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        result = ChatColor.translateAlternateColorCodes('&', result);
        return result;
    }

    private Map<String, String> loadPlayerSpecificData(final Player player) {
        final Map<String, String> data = new HashMap<>();
        if (player == null) {
            return data;
        }
        final StatsManager statsManager = plugin.getStatsManager();
        if (statsManager == null) {
            return data;
        }
        final UUID uuid = player.getUniqueId();
        final GlobalStats global = statsManager.getGlobalStats(uuid);
        data.put("%stats_global_games%", Integer.toString(global.getTotalGames()));
        data.put("%stats_global_wins%", Integer.toString(global.getTotalWins()));
        data.put("%stats_global_losses%", Integer.toString(global.getTotalLosses()));
        data.put("%stats_global_kills%", Integer.toString(global.getTotalKills()));
        data.put("%stats_global_deaths%", Integer.toString(global.getTotalDeaths()));
        data.put("%stats_global_ratio%", RATIO_FORMAT.format(global.getRatio()));
        data.put("%stats_global_playtime%", global.getFormattedPlaytime());

        loadGameData(statsManager, uuid, "BEDWARS", data, "%stats_bedwars_");
        loadGameData(statsManager, uuid, "NEXUS", data, "%stats_nexus_");
        loadGameData(statsManager, uuid, "ZOMBIE", data, "%stats_zombie_");
        loadGameData(statsManager, uuid, "CUSTOM", data, "%stats_custom_");

        return data;
    }

    private void loadGameData(final StatsManager statsManager,
                              final UUID uuid,
                              final String gameType,
                              final Map<String, String> output,
                              final String placeholderPrefix) {
        final GameStats stats = statsManager.getPlayerStats(uuid, gameType);
        output.put(placeholderPrefix + "games%", Integer.toString(stats.getGamesPlayed()));
        output.put(placeholderPrefix + "wins%", Integer.toString(stats.getWins()));
        output.put(placeholderPrefix + "losses%", Integer.toString(stats.getLosses()));
        output.put(placeholderPrefix + "kills%", Integer.toString(stats.getKills()));
        output.put(placeholderPrefix + "deaths%", Integer.toString(stats.getDeaths()));
        output.put(placeholderPrefix + "ratio%", RATIO_FORMAT.format(stats.getRatio()));
        output.put(placeholderPrefix + "playtime%", stats.getFormattedPlaytime());
        output.put(placeholderPrefix + "beds%", Integer.toString(stats.getSpecialStat1()));
        output.put(placeholderPrefix + "record%", Integer.toString(stats.getSpecialStat1()));
        if (placeholderPrefix.contains("nexus")) {
            output.put("%stats_nexus_destroyed%", Integer.toString(stats.getSpecialStat1()));
        }
        if (placeholderPrefix.contains("zombie")) {
            output.put("%stats_zombie_record%", Integer.toString(stats.getSpecialStat1()));
        }
    }

    private void preloadAllHeadsAsync() {
        if (!headPreloadScheduled.compareAndSet(false, true)) {
            return;
        }
        headCache.clear();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final Set<String> ids = collectHeadIds();
                final HeadDatabaseManager headManager = plugin.getHeadDatabaseManager();
                if (headManager == null || ids.isEmpty()) {
                    return;
                }
                int count = 0;
                for (String id : ids) {
                    if (id == null || id.isBlank()) {
                        continue;
                    }
                    final ItemStack head = headManager.getHead(id);
                    if (head != null) {
                        headCache.put(id.trim(), head.clone());
                        count++;
                    }
                }
                plugin.getLogger().info(count + " têtes HeadDatabase pré-chargées.");
            } finally {
                headPreloadScheduled.set(false);
            }
        });
    }

    private Set<String> collectHeadIds() {
        final Set<String> ids = new HashSet<>();
        for (CachedMenu menu : managedMenus.values()) {
            if (menu.decorItem() != null) {
                final String head = menu.decorItem().head();
                if (head != null && !head.isBlank()) {
                    ids.add(head.trim());
                }
                final String material = menu.decorItem().material();
                if (material != null && material.toLowerCase(Locale.ROOT).startsWith("hdb:")) {
                    ids.add(material.trim());
                }
            }
            for (MenuItemDefinition definition : menu.items().values()) {
                if (definition.head() != null && !definition.head().isBlank()) {
                    ids.add(definition.head().trim());
                }
                if (definition.material() != null && definition.material().toLowerCase(Locale.ROOT).startsWith("hdb:")) {
                    ids.add(definition.material().trim());
                }
            }
        }
        return ids;
    }

    private void startGlobalPlaceholderTask() {
        if (placeholderRefreshTask != null) {
            placeholderRefreshTask.cancel();
        }
        placeholderRefreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            final ServerPlaceholderCache cache = plugin.getServerPlaceholderCache();
            if (cache == null) {
                globalPlaceholderCache.put("%lobby_online_bedwars%", "0");
                return;
            }
            final int bedwars = cache.getServerPlayerCount("bedwars");
            globalPlaceholderCache.put("%lobby_online_bedwars%", Integer.toString(bedwars));
        }, 20L, 20L * 5);
    }

    private List<Integer> parseSlots(final Object object) {
        if (object == null) {
            return Collections.emptyList();
        }
        final List<Integer> slots = new ArrayList<>();
        if (object instanceof String string) {
            final String[] parts = string.split(",");
            for (String part : parts) {
                try {
                    slots.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        } else if (object instanceof Collection<?> collection) {
            for (Object value : collection) {
                if (value == null) {
                    continue;
                }
                if (value instanceof Number number) {
                    slots.add(number.intValue());
                } else {
                    try {
                        slots.add(Integer.parseInt(value.toString()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return slots;
    }

    private int resolveSlot(final String key, final int explicitSlot) {
        if (explicitSlot >= 0) {
            return explicitSlot;
        }
        if (key == null) {
            return -1;
        }
        final String[] parts = key.split("_");
        for (String part : parts) {
            try {
                return Integer.parseInt(part.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private Set<String> union(final Set<String> first, final Set<String> second) {
        final Set<String> merged = new HashSet<>(first);
        merged.addAll(second);
        return merged;
    }

    private record CachedMenu(String id,
                              String title,
                              int size,
                              Map<Integer, MenuItemDefinition> items,
                              Map<Integer, String> actions,
                              List<Integer> decorSlots,
                              MenuItemDefinition decorItem) {
    }

    private record MenuItemDefinition(int slot,
                                      String material,
                                      String head,
                                      String name,
                                      List<String> lore,
                                      String action) {
        private MenuItemDefinition {
            lore = lore == null ? List.of() : List.copyOf(lore);
        }
    }

    private static final class SimpleMenuSession implements Menu {

        private final UUID owner;
        private final String menuId;
        private final Inventory inventory;
        private final Map<Integer, String> actions;

        private SimpleMenuSession(final UUID owner,
                                  final String menuId,
                                  final Inventory inventory,
                                  final Map<Integer, String> actions) {
            this.owner = owner;
            this.menuId = menuId;
            this.inventory = inventory;
            this.actions = new HashMap<>(actions);
        }

        @Override
        public void open(final Player player) {
            if (player != null) {
                player.openInventory(inventory);
            }
        }

        @Override
        public void handleClick(final org.bukkit.event.inventory.InventoryClickEvent event) {
            // No-op. Actions are handled by MenuListener.
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public List<String> getActionsForSlot(final int slot) {
            final String action = actions.get(slot);
            if (action == null || action.isBlank()) {
                return List.of();
            }
            return List.of(action);
        }

        public UUID owner() {
            return owner;
        }

        public String menuId() {
            return menuId;
        }

        public Map<Integer, String> actions() {
            return Collections.unmodifiableMap(actions);
        }
    }
}
