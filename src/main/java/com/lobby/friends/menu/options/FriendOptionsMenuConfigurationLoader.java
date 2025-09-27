package com.lobby.friends.menu.options;

import com.lobby.LobbyPlugin;
import com.lobby.friends.menu.FriendsMenuDecoration;
import org.bukkit.Material;
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
 * Loader responsible for the {@code friend_options.yml} configuration file.
 */
public final class FriendOptionsMenuConfigurationLoader {

    private static final String RESOURCE_PATH = "friends/friend_options.yml";

    private FriendOptionsMenuConfigurationLoader() {
    }

    public static FriendOptionsMenuConfiguration load(final LobbyPlugin plugin) {
        final File directory = ensureDirectory(plugin);
        final File file = new File(directory, "friend_options.yml");
        ensureResource(plugin, file);

        final YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Impossible de charger '" + file.getName() + "': " + exception.getMessage());
            return buildFallback();
        }

        final ConfigurationSection menuSection = configuration.getConfigurationSection("menu");
        final String title = menuSection != null ? menuSection.getString("title") : null;
        final int size = menuSection != null ? menuSection.getInt("size", 27) : 27;
        final List<FriendsMenuDecoration> decorations = parseDecorations(configuration.getConfigurationSection("decoration"));
        final List<FriendOptionsMenuConfiguration.MenuItem> items = parseItems(configuration.getConfigurationSection("items"));
        final Map<String, FriendOptionsMenuConfiguration.ConfirmationPrompt> confirmations = parseConfirmations(configuration.getConfigurationSection("confirmations"));

        return new FriendOptionsMenuConfiguration(title, size, decorations, items, confirmations);
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

    private static FriendOptionsMenuConfiguration buildFallback() {
        return new FriendOptionsMenuConfiguration("§8» §7Options d'Ami", 27, List.of(), List.of(), Map.of());
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
            final String materialName = decorationSection.getString("material", "GRAY_STAINED_GLASS_PANE");
            Material material = Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
            if (material == null) {
                material = Material.GRAY_STAINED_GLASS_PANE;
            }
            final String name = decorationSection.getString("name", " ");
            final List<Integer> slots = decorationSection.getIntegerList("slots");
            decorations.add(new FriendsMenuDecoration(material, name, slots));
        }
        return decorations;
    }

    private static List<FriendOptionsMenuConfiguration.MenuItem> parseItems(final ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        final List<FriendOptionsMenuConfiguration.MenuItem> items = new ArrayList<>();
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
            items.add(new FriendOptionsMenuConfiguration.MenuItem(slot, itemKey, name, lore, actions));
        }
        return items;
    }

    private static Map<String, FriendOptionsMenuConfiguration.ConfirmationPrompt> parseConfirmations(final ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        final Map<String, FriendOptionsMenuConfiguration.ConfirmationPrompt> confirmations = new HashMap<>();
        for (String key : section.getKeys(false)) {
            final ConfigurationSection confirmationSection = section.getConfigurationSection(key);
            if (confirmationSection == null) {
                continue;
            }
            final String title = confirmationSection.getString("title", "");
            final String message = confirmationSection.getString("message", "");
            final String confirm = confirmationSection.getString("confirm_button", "");
            final String cancel = confirmationSection.getString("cancel_button", "");
            confirmations.put(key, new FriendOptionsMenuConfiguration.ConfirmationPrompt(title, message, confirm, cancel));
        }
        return Map.copyOf(confirmations);
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

    private static String resolveItemKey(final ConfigurationSection section) {
        final String headId = section.getString("head_id");
        if (headId != null && !headId.isBlank()) {
            return "hdb:" + headId.trim();
        }
        final String hdbId = section.getString("hdb_id");
        if (hdbId != null && !hdbId.isBlank()) {
            return "hdb:" + hdbId.trim();
        }
        final String materialName = section.getString("material", "BARRIER");
        if (materialName == null || materialName.isBlank()) {
            return "BARRIER";
        }
        return materialName.trim();
    }
}

