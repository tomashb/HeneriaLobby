package com.lobby.friends.menu.list;

import com.lobby.LobbyPlugin;
import com.lobby.friends.menu.FriendsMenuDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Utility in charge of loading the friends list configuration from the plugin
 * data folder. The loader is defensive against missing or malformed entries
 * and guarantees that the resulting {@link FriendsListMenuConfiguration}
 * instance always exposes sane defaults.
 */
public final class FriendsListMenuConfigurationLoader {

    private static final String RESOURCE_PATH = "friends/friends_list.yml";

    private FriendsListMenuConfigurationLoader() {
    }

    public static FriendsListMenuConfiguration load(final LobbyPlugin plugin) {
        final File directory = ensureDirectory(plugin);
        final File file = new File(directory, "friends_list.yml");
        ensureResource(plugin, file);

        final YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Impossible de charger '" + file.getName() + "': " + exception.getMessage());
            return buildFallback();
        }

        final FriendsListMenuConfiguration.Settings settings = parseSettings(configuration.getConfigurationSection("menu"));
        final List<FriendsMenuDecoration> decorations = parseDecorations(configuration.getConfigurationSection("decoration"));
        final List<Integer> friendSlots = parseFriendSlots(configuration.getIntegerList("friend_slots"), settings.size());
        final FriendsListMenuConfiguration.Navigation navigation = parseNavigation(configuration.getConfigurationSection("navigation"));
        final FriendsListMenuConfiguration.Sorting sorting = parseSorting(configuration.getConfigurationSection("sorting"));
        final FriendsListMenuConfiguration.Filters filters = parseFilters(configuration.getConfigurationSection("filters"));
        final FriendsListMenuConfiguration.FriendTemplate onlineTemplate = parseFriendTemplate(configuration.getConfigurationSection("friend_online"));
        final FriendsListMenuConfiguration.FriendTemplate offlineTemplate = parseFriendTemplate(configuration.getConfigurationSection("friend_offline"));
        final FriendsListMenuConfiguration.FavoriteDecorations favoriteDecorations = parseFavoriteDecorations(configuration.getConfigurationSection("friend_favorite"));
        final Map<String, FriendsListMenuConfiguration.StatusDecoration> statuses = parseStatuses(configuration.getConfigurationSection("friend_statuses"));
        final FriendsListMenuConfiguration.Sounds sounds = parseSounds(configuration.getConfigurationSection("sounds"));
        final Map<String, String> messages = parseMessages(configuration.getConfigurationSection("messages"));
        final List<String> placeholders = parseStringList(configuration.getStringList("placeholders"));
        final FriendsListMenuConfiguration.AdvancedSettings advanced = parseAdvancedSettings(configuration.getConfigurationSection("advanced"), settings.itemsPerPage());
        final FriendsListMenuConfiguration.Integrations integrations = parseIntegrations(configuration.getConfigurationSection("integrations"));

        return new FriendsListMenuConfiguration(settings, decorations, friendSlots, navigation, sorting, filters,
                onlineTemplate, offlineTemplate, favoriteDecorations, statuses, sounds, messages,
                placeholders, advanced, integrations);
    }

    private static File ensureDirectory(final LobbyPlugin plugin) {
        final File dataFolder = plugin.getDataFolder();
        final File directory = new File(dataFolder, "friends");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Impossible de créer le dossier des amis: " + directory.getAbsolutePath());
        }
        return directory;
    }

    private static void ensureResource(final LobbyPlugin plugin, final File file) {
        if (file.exists()) {
            return;
        }
        try {
            plugin.saveResource(RESOURCE_PATH, false);
        } catch (final IllegalArgumentException ignored) {
            // No default resource packaged, continue with fallback values.
        }
    }

    private static FriendsListMenuConfiguration buildFallback() {
        final FriendsListMenuConfiguration.Settings settings = new FriendsListMenuConfiguration.Settings(
                "§8» §aListe des Amis", 54, 28, true, 10);
        return new FriendsListMenuConfiguration(settings, List.of(), List.of(),
                FriendsListMenuConfiguration.Navigation.empty(), FriendsListMenuConfiguration.Sorting.empty(),
                FriendsListMenuConfiguration.Filters.empty(), FriendsListMenuConfiguration.FriendTemplate.empty(),
                FriendsListMenuConfiguration.FriendTemplate.empty(), FriendsListMenuConfiguration.FavoriteDecorations.empty(),
                Map.of(), FriendsListMenuConfiguration.Sounds.empty(), Map.of(), List.of(),
                FriendsListMenuConfiguration.AdvancedSettings.empty(), FriendsListMenuConfiguration.Integrations.empty());
    }

    private static FriendsListMenuConfiguration.Settings parseSettings(final ConfigurationSection section) {
        if (section == null) {
            return new FriendsListMenuConfiguration.Settings("§8» §aListe des Amis", 54, 28, true, 10);
        }
        final String title = section.getString("title", "§8» §aListe des Amis");
        final int size = section.getInt("size", 54);
        final int itemsPerPage = section.getInt("items_per_page", 28);
        final boolean autoRefresh = section.getBoolean("auto_refresh", true);
        final int refreshInterval = section.getInt("refresh_interval", 10);
        return new FriendsListMenuConfiguration.Settings(title, size, itemsPerPage, autoRefresh, refreshInterval);
    }

    private static List<FriendsMenuDecoration> parseDecorations(final ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        final List<FriendsMenuDecoration> decorations = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            final ConfigurationSection decorationSection = section.getConfigurationSection(key);
            if (decorationSection == null) {
                continue;
            }
            final String materialName = decorationSection.getString("material", "BLACK_STAINED_GLASS_PANE");
            Material material = Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
            if (material == null) {
                material = Material.BLACK_STAINED_GLASS_PANE;
            }
            final String name = decorationSection.getString("name", " ");
            final List<Integer> slots = decorationSection.getIntegerList("slots");
            decorations.add(new FriendsMenuDecoration(material, name, slots));
        }
        return decorations;
    }

    private static List<Integer> parseFriendSlots(final List<Integer> slots, final int menuSize) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        final List<Integer> sanitized = new ArrayList<>(slots.size());
        for (Integer slot : slots) {
            if (slot == null) {
                continue;
            }
            final int value = slot;
            if (value < 0 || value >= menuSize) {
                continue;
            }
            if (!sanitized.contains(value)) {
                sanitized.add(value);
            }
        }
        Collections.sort(sanitized);
        return List.copyOf(sanitized);
    }

    private static FriendsListMenuConfiguration.Navigation parseNavigation(final ConfigurationSection section) {
        if (section == null) {
            return FriendsListMenuConfiguration.Navigation.empty();
        }
        final FriendsListMenuConfiguration.Button previous = parseButton(section.getConfigurationSection("previous_page"));
        final FriendsListMenuConfiguration.Button back = parseButton(section.getConfigurationSection("back_to_main"));
        final FriendsListMenuConfiguration.Button next = parseButton(section.getConfigurationSection("next_page"));
        return new FriendsListMenuConfiguration.Navigation(previous, back, next);
    }

    private static FriendsListMenuConfiguration.Button parseButton(final ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        final int slot = section.getInt("slot", 0);
        final String itemKey = resolveItemKey(section);
        final String name = section.getString("name", "");
        final List<String> lore = section.getStringList("lore");
        final Map<String, String> actions = parseActions(section.getConfigurationSection("actions"));
        final String visibleWhen = section.getString("visible_when", "");
        return new FriendsListMenuConfiguration.Button(slot, itemKey, name, lore, actions, visibleWhen);
    }

    private static Map<String, String> parseActions(final ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        final Map<String, String> actions = new HashMap<>();
        for (String key : section.getKeys(false)) {
            final String action = section.getString(key);
            if (action == null || action.isBlank()) {
                continue;
            }
            actions.put(key.toLowerCase(Locale.ROOT), action.trim());
        }
        return Map.copyOf(actions);
    }

    private static FriendsListMenuConfiguration.Sorting parseSorting(final ConfigurationSection section) {
        if (section == null) {
            return FriendsListMenuConfiguration.Sorting.empty();
        }
        final String currentSort = section.getString("current_sort", "online_first");
        final FriendsListMenuConfiguration.Button sortButton = parseButton(section.getConfigurationSection("sort_button"));
        final Map<String, FriendsListMenuConfiguration.SortingOption> options = new HashMap<>();
        final ConfigurationSection optionsSection = section.getConfigurationSection("options");
        if (optionsSection != null) {
            for (String key : optionsSection.getKeys(false)) {
                final ConfigurationSection optionSection = optionsSection.getConfigurationSection(key);
                if (optionSection == null) {
                    continue;
                }
                final String name = optionSection.getString("name", key);
                final String description = optionSection.getString("description", "");
                options.put(key, new FriendsListMenuConfiguration.SortingOption(key, name, description));
            }
        }
        return new FriendsListMenuConfiguration.Sorting(currentSort, sortButton, options);
    }

    private static FriendsListMenuConfiguration.Filters parseFilters(final ConfigurationSection section) {
        if (section == null) {
            return FriendsListMenuConfiguration.Filters.empty();
        }
        final boolean showOnlineOnly = section.getBoolean("show_online_only", false);
        final boolean showFavoritesOnly = section.getBoolean("show_favorites_only", false);
        final boolean hideAway = section.getBoolean("hide_away", false);
        final boolean hideBusy = section.getBoolean("hide_busy", false);
        final FriendsListMenuConfiguration.Button filterButton = parseButton(section.getConfigurationSection("filter_button"));
        return new FriendsListMenuConfiguration.Filters(showOnlineOnly, showFavoritesOnly, hideAway, hideBusy, filterButton);
    }

    private static FriendsListMenuConfiguration.FriendTemplate parseFriendTemplate(final ConfigurationSection section) {
        if (section == null) {
            return FriendsListMenuConfiguration.FriendTemplate.empty();
        }
        final String itemKey = resolveItemKey(section, "item");
        final String name = section.getString("name", "");
        final List<String> lore = section.getStringList("lore");
        final Map<String, String> actions = parseActions(section.getConfigurationSection("actions"));
        return new FriendsListMenuConfiguration.FriendTemplate(itemKey, name, lore, actions);
    }

    private static FriendsListMenuConfiguration.FavoriteDecorations parseFavoriteDecorations(final ConfigurationSection section) {
        if (section == null) {
            return FriendsListMenuConfiguration.FavoriteDecorations.empty();
        }
        final boolean enchanted = section.getBoolean("enchanted", false);
        final String particles = section.getString("particles", "");
        final String namePrefix = section.getString("name_prefix", "");
        final List<String> loreAddition = section.getStringList("lore_addition");
        return new FriendsListMenuConfiguration.FavoriteDecorations(enchanted, particles, namePrefix, loreAddition);
    }

    private static Map<String, FriendsListMenuConfiguration.StatusDecoration> parseStatuses(final ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        final Map<String, FriendsListMenuConfiguration.StatusDecoration> statuses = new HashMap<>();
        for (String key : section.getKeys(false)) {
            final ConfigurationSection statusSection = section.getConfigurationSection(key);
            if (statusSection == null) {
                continue;
            }
            final String suffix = statusSection.getString("name_suffix", "");
            final String color = statusSection.getString("status_color", "");
            statuses.put(key, new FriendsListMenuConfiguration.StatusDecoration(suffix, color));
        }
        return Map.copyOf(statuses);
    }

    private static FriendsListMenuConfiguration.Sounds parseSounds(final ConfigurationSection section) {
        if (section == null) {
            return FriendsListMenuConfiguration.Sounds.empty();
        }
        final Sound open = resolveSound(section.getString("open_list"));
        final Sound pageTurn = resolveSound(section.getString("page_turn"));
        final Sound friendClick = resolveSound(section.getString("friend_click"));
        final Sound teleportSuccess = resolveSound(section.getString("teleport_success"));
        final Sound teleportFailed = resolveSound(section.getString("teleport_failed"));
        final Sound messageSent = resolveSound(section.getString("message_sent"));
        return new FriendsListMenuConfiguration.Sounds(open, pageTurn, friendClick, teleportSuccess, teleportFailed, messageSent);
    }

    private static Map<String, String> parseMessages(final ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        final Map<String, String> messages = new HashMap<>();
        for (String key : section.getKeys(false)) {
            final String value = section.getString(key);
            if (value == null) {
                continue;
            }
            messages.put(key, value);
        }
        return Map.copyOf(messages);
    }

    private static List<String> parseStringList(final List<String> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return List.copyOf(list);
    }

    private static FriendsListMenuConfiguration.AdvancedSettings parseAdvancedSettings(final ConfigurationSection section,
                                                                                      final int defaultItemsPerPage) {
        if (section == null) {
            return new FriendsListMenuConfiguration.AdvancedSettings(defaultItemsPerPage, 300, 5, true, true, true);
        }
        final int maxPerPage = section.getInt("max_friends_per_page", defaultItemsPerPage);
        final int headCacheDuration = section.getInt("head_cache_duration", 300);
        final int statusInterval = section.getInt("status_update_interval", 5);
        final boolean offlineGrayscale = section.getBoolean("offline_head_grayscale", true);
        final boolean favoriteGlow = section.getBoolean("favorite_glow_effect", true);
        final boolean animateStatus = section.getBoolean("animate_status_changes", true);
        return new FriendsListMenuConfiguration.AdvancedSettings(maxPerPage, headCacheDuration, statusInterval,
                offlineGrayscale, favoriteGlow, animateStatus);
    }

    private static FriendsListMenuConfiguration.Integrations parseIntegrations(final ConfigurationSection section) {
        if (section == null) {
            return FriendsListMenuConfiguration.Integrations.empty();
        }
        final boolean placeholderApi = section.getBoolean("placeholderapi", true);
        final boolean headDatabase = section.getBoolean("headdatabase", true);
        final boolean bungeeMessaging = section.getBoolean("bungee_messaging", true);
        return new FriendsListMenuConfiguration.Integrations(placeholderApi, headDatabase, bungeeMessaging);
    }

    private static String resolveItemKey(final ConfigurationSection section) {
        return resolveItemKey(section, "material");
    }

    private static String resolveItemKey(final ConfigurationSection section, final String materialKey) {
        if (section == null) {
            return "BARRIER";
        }
        final String hdbId = section.getString("hdb_id");
        if (hdbId != null && !hdbId.isBlank()) {
            return "hdb:" + hdbId.trim();
        }
        final String materialName = section.getString(materialKey, "BARRIER");
        if (materialName == null || materialName.isBlank()) {
            return "BARRIER";
        }
        return materialName.trim();
    }

    private static Sound resolveSound(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

