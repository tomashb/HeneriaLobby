package com.lobby.menus.friends;

import com.lobby.LobbyPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FriendRequestsMenuConfig {

    private final String title;
    private final int size;
    private final List<FriendsMenuConfig.MenuItemDefinition> designItems;
    private final FriendsMenuConfig.MenuItemDefinition returnButton;

    private FriendRequestsMenuConfig(final String title,
                                     final int size,
                                     final List<FriendsMenuConfig.MenuItemDefinition> designItems,
                                     final FriendsMenuConfig.MenuItemDefinition returnButton) {
        this.title = title;
        this.size = size;
        this.designItems = designItems;
        this.returnButton = returnButton;
    }

    public String title() {
        return title;
    }

    public int size() {
        return size;
    }

    public List<FriendsMenuConfig.MenuItemDefinition> designItems() {
        return designItems;
    }

    public FriendsMenuConfig.MenuItemDefinition returnButton() {
        return returnButton;
    }

    static FriendRequestsMenuConfig load(final LobbyPlugin plugin) {
        final YamlConfiguration configuration = loadConfiguration(plugin, "amis_requests_menu");
        final String title = configuration.getString("title", "&8Menu");
        final int size = normalizeSize(configuration.getInt("size", 54));
        final List<FriendsMenuConfig.MenuItemDefinition> designItems = new ArrayList<>();
        final ConfigurationSection designSection = configuration.getConfigurationSection("design");
        if (designSection != null) {
            final ConfigurationSection primary = designSection.getConfigurationSection("primary_border");
            if (primary != null) {
                final FriendsMenuConfig.MenuItemDefinition definition = FriendsMenuConfig.MenuItemDefinition.fromSection(
                        "design-primary", primary, true);
                if (definition != null) {
                    designItems.add(definition);
                }
            }
            final ConfigurationSection secondary = designSection.getConfigurationSection("secondary_border");
            if (secondary != null) {
                final FriendsMenuConfig.MenuItemDefinition definition = FriendsMenuConfig.MenuItemDefinition.fromSection(
                        "design-secondary", secondary, true);
                if (definition != null) {
                    designItems.add(definition);
                }
            }
        }
        final ConfigurationSection itemsSection = configuration.getConfigurationSection("items");
        if (itemsSection == null) {
            throw new IllegalStateException("Menu amis_requests_menu missing items section");
        }
        final FriendsMenuConfig.MenuItemDefinition returnButton = FriendsMenuConfig.MenuItemDefinition.fromSection(
                "return-to-friends", itemsSection.getConfigurationSection("return-to-friends"), true);
        if (returnButton == null) {
            throw new IllegalStateException("Menu amis_requests_menu missing return-to-friends item");
        }
        return new FriendRequestsMenuConfig(title, size, List.copyOf(designItems), returnButton);
    }

    private static int normalizeSize(final int requested) {
        final int clamped = Math.max(9, Math.min(54, requested));
        return (clamped % 9 == 0) ? clamped : ((clamped / 9) + 1) * 9;
    }

    private static YamlConfiguration loadConfiguration(final LobbyPlugin plugin, final String menuId) {
        final File file = resolveMenuFile(plugin, menuId.toLowerCase(Locale.ROOT));
        if (file == null) {
            throw new IllegalStateException("Unable to resolve menu configuration for " + menuId);
        }
        final YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Unable to load menu configuration " + menuId, exception);
        }
        return configuration;
    }

    private static File resolveMenuFile(final LobbyPlugin plugin, final String menuId) {
        final File menusDirectory = new File(plugin.getDataFolder(), "menus");
        if (!menusDirectory.exists() && !menusDirectory.mkdirs()) {
            return null;
        }
        final File menuFile = new File(menusDirectory, menuId + ".yml");
        if (menuFile.exists()) {
            return menuFile;
        }
        try {
            plugin.saveResource("menus/" + menuId + ".yml", false);
        } catch (final IllegalArgumentException ignored) {
        }
        return menuFile.exists() ? menuFile : null;
    }
}
