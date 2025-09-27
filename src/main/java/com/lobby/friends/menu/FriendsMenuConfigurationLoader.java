package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
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

public final class FriendsMenuConfigurationLoader {

    private static final String RESOURCE_PATH = "friends/friends_main_menu.yml";

    private FriendsMenuConfigurationLoader() {
    }

    public static FriendsMenuConfiguration load(final LobbyPlugin plugin) {
        final File dataFolder = plugin.getDataFolder();
        final File directory = new File(dataFolder, "friends");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Impossible de créer le dossier des menus d'amis.");
        }
        final File file = new File(directory, "friends_main_menu.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource(RESOURCE_PATH, false);
            } catch (final IllegalArgumentException ignored) {
                // No default resource packaged
            }
        }
        if (!file.exists()) {
            plugin.getLogger().warning("La configuration du menu d'amis est introuvable: " + file.getAbsolutePath());
            return buildFallback();
        }

        final YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Impossible de charger '" + file.getName() + "': " + exception.getMessage());
            return buildFallback();
        }

        final ConfigurationSection menuSection = configuration.getConfigurationSection("menu");
        if (menuSection == null) {
            plugin.getLogger().warning("La section 'menu' est absente du fichier " + file.getName());
            return buildFallback();
        }

        final String title = menuSection.getString("title", "§8» §aMenu des Amis");
        final int size = menuSection.getInt("size", 54);
        final int updateInterval = menuSection.getInt("update_interval", 5);

        final Map<String, Sound> sounds = parseSounds(configuration.getConfigurationSection("sounds"));
        final List<FriendsMenuDecoration> decorations = parseDecorations(configuration.getConfigurationSection("decoration"));
        final List<FriendsMenuItem> items = parseItems(configuration.getConfigurationSection("items"));

        return new FriendsMenuConfiguration(title, size, updateInterval,
                sounds.get("open_menu"), sounds.get("click_item"), sounds.get("error"),
                decorations, items);
    }

    private static Map<String, Sound> parseSounds(final ConfigurationSection section) {
        final Map<String, Sound> sounds = new HashMap<>();
        if (section == null) {
            return sounds;
        }
        for (String key : section.getKeys(false)) {
            final String value = section.getString(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                sounds.put(key, Sound.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (final IllegalArgumentException exception) {
                // ignore invalid sound names but keep log for debugging
                final LobbyPlugin plugin = LobbyPlugin.getInstance();
                if (plugin != null) {
                    plugin.getLogger().warning("Son invalide pour le menu d'amis ('" + key + "'): " + value);
                }
            }
        }
        return sounds;
    }

    private static List<FriendsMenuDecoration> parseDecorations(final ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        final List<FriendsMenuDecoration> list = new ArrayList<>();
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
            list.add(new FriendsMenuDecoration(material, name, slots));
        }
        return list;
    }

    private static List<FriendsMenuItem> parseItems(final ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        final List<FriendsMenuItem> items = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            final ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }
            final int slot = itemSection.getInt("slot", -1);
            if (slot < 0 || slot >= 54) {
                continue;
            }
            final String material = resolveMaterialKey(itemSection);
            final String name = itemSection.getString("name", " ");
            final List<String> lore = itemSection.getStringList("lore");
            final String action = itemSection.getString("action", "");
            final boolean enchanted = itemSection.getBoolean("enchanted", false);
            items.add(new FriendsMenuItem(slot, material, name, lore, action, enchanted));
        }
        return items;
    }

    private static String resolveMaterialKey(final ConfigurationSection section) {
        final String headId = section.getString("head_id");
        if (headId != null && !headId.isBlank()) {
            return "hdb:" + headId.trim();
        }
        final String hdb = section.getString("hdb_id");
        if (hdb != null && !hdb.isBlank()) {
            return "hdb:" + hdb.trim();
        }
        final String material = section.getString("material", "BARRIER");
        if (material == null) {
            return "BARRIER";
        }
        return material.trim();
    }

    private static FriendsMenuConfiguration buildFallback() {
        return new FriendsMenuConfiguration("§8» §aMenu des Amis", 54, 5,
                null, null, null, List.of(), List.of());
    }
}

