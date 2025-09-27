package com.lobby.friends.menu.statistics;

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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility responsible for loading the statistics menu configuration file.
 */
public final class FriendStatisticsMenuConfigurationLoader {

    private static final String RESOURCE_PATH = "friends/statistics.yml";

    private FriendStatisticsMenuConfigurationLoader() {
    }

    public static FriendStatisticsMenuConfiguration load(final LobbyPlugin plugin) {
        final File directory = ensureDirectory(plugin);
        final File file = new File(directory, "statistics.yml");
        ensureResource(plugin, file);

        final YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Impossible de charger '" + file.getName() + "': " + exception.getMessage());
            return buildFallback();
        }

        final ConfigurationSection menuSection = configuration.getConfigurationSection("menu");
        final String title = menuSection != null ? menuSection.getString("title", "§8» §bStatistiques d'Amitié")
                : "§8» §bStatistiques d'Amitié";
        final int size = menuSection != null ? menuSection.getInt("size", 54) : 54;
        final boolean autoRefresh = menuSection != null && menuSection.getBoolean("auto_refresh", true);
        final int refreshInterval = menuSection != null ? menuSection.getInt("refresh_interval", 30) : 30;

        final List<FriendsMenuDecoration> decorations = parseDecorations(configuration.getConfigurationSection("decoration"));
        final List<FriendStatisticsMenuConfiguration.MenuItem> items = parseItems(configuration);
        final Map<String, String> messages = parseMessages(configuration.getConfigurationSection("messages"));
        final Map<String, Sound> sounds = parseSounds(configuration.getConfigurationSection("sounds"));

        return new FriendStatisticsMenuConfiguration(title, size, autoRefresh, refreshInterval, decorations, items, messages, sounds);
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
            // Resource not packaged; rely on fallback values.
        }
    }

    private static FriendStatisticsMenuConfiguration buildFallback() {
        return new FriendStatisticsMenuConfiguration(
                "§8» §bStatistiques d'Amitié",
                54,
                true,
                30,
                List.of(),
                List.of(),
                Map.of(
                        "calculating", "§7Calcul des statistiques en cours...",
                        "updated", "§aStatistiques mises à jour !",
                        "export_success", "§aStatistiques exportées !"
                ),
                Map.of()
        );
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
            final String materialName = decorationSection.getString("material", "BLUE_STAINED_GLASS_PANE");
            Material material = Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
            if (material == null) {
                material = Material.BLUE_STAINED_GLASS_PANE;
            }
            final String name = decorationSection.getString("name", " ");
            final List<Integer> slots = decorationSection.getIntegerList("slots");
            decorations.add(new FriendsMenuDecoration(material, name, slots));
        }
        return decorations;
    }

    private static List<FriendStatisticsMenuConfiguration.MenuItem> parseItems(final YamlConfiguration configuration) {
        final List<FriendStatisticsMenuConfiguration.MenuItem> items = new ArrayList<>();
        collectItems(configuration.getConfigurationSection("statistics_categories"), items);
        collectItems(configuration.getConfigurationSection("charts"), items);
        collectItems(configuration.getConfigurationSection("comparisons"), items);
        collectItems(configuration.getConfigurationSection("insights"), items);
        collectItems(configuration.getConfigurationSection("navigation"), items);
        return List.copyOf(items);
    }

    private static void collectItems(final ConfigurationSection section, final List<FriendStatisticsMenuConfiguration.MenuItem> items) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            final ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }
            final int slot = itemSection.getInt("slot", 0);
            final String itemKey = resolveItemKey(itemSection);
            final String name = itemSection.getString("name", "");
            final List<String> lore = itemSection.getStringList("lore");
            final Map<String, String> actions = parseActions(itemSection.getConfigurationSection("actions"));
            items.add(new FriendStatisticsMenuConfiguration.MenuItem(slot, itemKey, name, lore, actions));
        }
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

    private static Map<String, Sound> parseSounds(final ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        final Map<String, Sound> sounds = new HashMap<>();
        for (String key : section.getKeys(false)) {
            final String value = section.getString(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                sounds.put(key, Sound.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Invalid sound, skip.
            }
        }
        return Map.copyOf(sounds);
    }

    private static String resolveItemKey(final ConfigurationSection section) {
        final String hdbId = section.getString("hdb_id");
        if (hdbId != null && !hdbId.isBlank()) {
            return "hdb:" + hdbId.trim();
        }
        final String materialName = section.getString("material", "PAPER");
        if (materialName == null || materialName.isBlank()) {
            return "PAPER";
        }
        return materialName.trim();
    }
}
