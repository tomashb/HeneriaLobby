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

final class FriendsMenuConfig {

    private final String title;
    private final int size;
    private final List<MenuItemDefinition> designItems;
    private final MenuItemDefinition addFriendButton;
    private final MenuItemDefinition requestsButton;
    private final MenuItemDefinition friendTemplate;
    private final MenuItemDefinition previousPageButton;
    private final MenuItemDefinition nextPageButton;
    private final MenuItemDefinition returnButton;

    private FriendsMenuConfig(final String title,
                              final int size,
                              final List<MenuItemDefinition> designItems,
                              final MenuItemDefinition addFriendButton,
                              final MenuItemDefinition requestsButton,
                              final MenuItemDefinition friendTemplate,
                              final MenuItemDefinition previousPageButton,
                              final MenuItemDefinition nextPageButton,
                              final MenuItemDefinition returnButton) {
        this.title = title;
        this.size = size;
        this.designItems = designItems;
        this.addFriendButton = addFriendButton;
        this.requestsButton = requestsButton;
        this.friendTemplate = friendTemplate;
        this.previousPageButton = previousPageButton;
        this.nextPageButton = nextPageButton;
        this.returnButton = returnButton;
    }

    public String title() {
        return title;
    }

    public int size() {
        return size;
    }

    public List<MenuItemDefinition> designItems() {
        return designItems;
    }

    public MenuItemDefinition addFriendButton() {
        return addFriendButton;
    }

    public MenuItemDefinition requestsButton() {
        return requestsButton;
    }

    public MenuItemDefinition friendTemplate() {
        return friendTemplate;
    }

    public MenuItemDefinition previousPageButton() {
        return previousPageButton;
    }

    public MenuItemDefinition nextPageButton() {
        return nextPageButton;
    }

    public MenuItemDefinition returnButton() {
        return returnButton;
    }

    static FriendsMenuConfig load(final LobbyPlugin plugin) {
        final YamlConfiguration configuration = loadConfiguration(plugin, "amis_menu");
        final String title = configuration.getString("title", "&8Menu");
        final int size = normalizeSize(configuration.getInt("size", 54));
        final List<MenuItemDefinition> designItems = new ArrayList<>();
        final ConfigurationSection designSection = configuration.getConfigurationSection("design");
        if (designSection != null) {
            final ConfigurationSection primary = designSection.getConfigurationSection("primary_border");
            if (primary != null) {
                final MenuItemDefinition definition = MenuItemDefinition.fromSection("design-primary", primary, true);
                if (definition != null) {
                    designItems.add(definition);
                }
            }
            final ConfigurationSection secondary = designSection.getConfigurationSection("secondary_border");
            if (secondary != null) {
                final MenuItemDefinition definition = MenuItemDefinition.fromSection("design-secondary", secondary, true);
                if (definition != null) {
                    designItems.add(definition);
                }
            }
        }

        final ConfigurationSection itemsSection = configuration.getConfigurationSection("items");
        if (itemsSection == null) {
            throw new IllegalStateException("Menu amis_menu missing items section");
        }

        final MenuItemDefinition addFriend = MenuItemDefinition.fromSection("add-friend",
                itemsSection.getConfigurationSection("add-friend"), true);
        final MenuItemDefinition requests = MenuItemDefinition.fromSection("requests",
                itemsSection.getConfigurationSection("requests"), true);
        final MenuItemDefinition template = MenuItemDefinition.fromSection("friend-list-template",
                itemsSection.getConfigurationSection("friend-list-template"), false);
        final MenuItemDefinition prev = MenuItemDefinition.fromSection("prev-page",
                itemsSection.getConfigurationSection("prev-page"), true);
        final MenuItemDefinition next = MenuItemDefinition.fromSection("next-page",
                itemsSection.getConfigurationSection("next-page"), true);
        final MenuItemDefinition back = MenuItemDefinition.fromSection("return-to-profile",
                itemsSection.getConfigurationSection("return-to-profile"), true);

        if (addFriend == null || requests == null || template == null || prev == null || next == null || back == null) {
            throw new IllegalStateException("Menu amis_menu missing required items");
        }

        return new FriendsMenuConfig(title, size, List.copyOf(designItems), addFriend, requests, template, prev, next, back);
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
            // Custom file only
        }
        return menuFile.exists() ? menuFile : null;
    }

    record MenuItemDefinition(String id,
                              List<Integer> slots,
                              String material,
                              String name,
                              List<String> lore,
                              String action,
                              String skullOwner,
                              boolean playerHead,
                              String enchantExpression,
                              int amount) {

        static MenuItemDefinition fromSection(final String id,
                                              final ConfigurationSection section,
                                              final boolean requireSlot) {
            if (section == null) {
                return null;
            }
            final List<Integer> slots = new ArrayList<>();
            if (section.isList("slots")) {
                section.getIntegerList("slots").forEach(slot -> slots.add(Math.max(0, slot)));
            }
            if (section.contains("slot")) {
                slots.add(section.getInt("slot"));
            }
            if (requireSlot && slots.isEmpty()) {
                return null;
            }
            final String material = section.getString("material", "BARRIER");
            final String name = section.getString("name", "&r");
            final List<String> lore = section.getStringList("lore");
            final String action = section.getString("action", "");
            final String skullOwner = section.getString("skull-owner");
            final boolean playerHead = section.getBoolean("player-head", false);
            final String enchant = section.getString("enchanted");
            final int amount = Math.max(1, section.getInt("amount", 1));
            return new MenuItemDefinition(id, List.copyOf(slots), material, name, List.copyOf(lore), action,
                    skullOwner, playerHead, enchant, amount);
        }
    }
}
