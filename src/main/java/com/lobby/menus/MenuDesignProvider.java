package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.utils.LogUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuDesignProvider {

    private final LobbyPlugin plugin;
    private final Map<String, MenuDesign> cache = new ConcurrentHashMap<>();

    public MenuDesignProvider(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<MenuDesign> getDesign(final String templateName) {
        if (templateName == null || templateName.isBlank()) {
            return Optional.empty();
        }
        final String normalized = templateName.toLowerCase(Locale.ROOT);
        final MenuDesign cached = cache.get(normalized);
        if (cached != null) {
            return Optional.of(cached);
        }
        final MenuDesign loaded = loadDesign(normalized);
        if (loaded != null) {
            cache.put(normalized, loaded);
            return Optional.of(loaded);
        }
        return Optional.empty();
    }

    public void reload() {
        cache.clear();
    }

    private MenuDesign loadDesign(final String templateName) {
        final String fileName = templateName + "_design_template.yml";
        final File file = new File(plugin.getDataFolder(), "config/menus/" + fileName);
        if (!file.exists()) {
            plugin.saveResource("config/menus/" + fileName, false);
        }
        if (!file.exists()) {
            LogUtils.warning(plugin, "Unable to locate design template '" + fileName + "'.");
            return null;
        }
        final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection designSection = configuration.getConfigurationSection("design");
        if (designSection == null) {
            LogUtils.warning(plugin, "Design template '" + fileName + "' is missing a 'design' section.");
            return null;
        }

        final Material decorative = parseMaterial(designSection, "decorative_glass", Material.BLUE_STAINED_GLASS_PANE);
        final Material border = parseMaterial(designSection, "info_glass", Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        final Material confirm = parseMaterial(designSection, "confirm_glass", Material.GREEN_STAINED_GLASS_PANE);
        final Material cancel = parseMaterial(designSection, "cancel_glass", Material.RED_STAINED_GLASS_PANE);
        final List<Integer> decorationSlots = designSection.getIntegerList("decoration_slots");
        final List<Integer> borderSlots = designSection.getIntegerList("border_slots");
        return new MenuDesign(decorative, border, confirm, cancel, decorationSlots, borderSlots);
    }

    private Material parseMaterial(final ConfigurationSection section, final String key, final Material fallback) {
        final String value = section.getString(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        final Material material = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
        if (material == null) {
            LogUtils.warning(plugin, "Invalid material '" + value + "' in design template '" + section.getCurrentPath() + "'.");
            return fallback;
        }
        return material;
    }
}

