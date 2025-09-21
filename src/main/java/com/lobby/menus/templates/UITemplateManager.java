package com.lobby.menus.templates;

import com.lobby.LobbyPlugin;
import com.lobby.utils.LogUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UITemplateManager {

    private static final Set<String> DEFAULT_FILES = Set.of(
            "social_heads.yml",
            "social_designs.yml"
    );

    private final LobbyPlugin plugin;
    private final Map<String, DesignTemplate> designTemplates = new ConcurrentHashMap<>();
    private final Map<String, String> headTemplates = new ConcurrentHashMap<>();

    public UITemplateManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        loadTemplates();
    }

    public DesignTemplate getDesignTemplate(final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return designTemplates.get(name.toLowerCase(Locale.ROOT));
    }

    public String resolveHead(final String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        final String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        String value = headTemplates.get(normalized);
        if (value != null) {
            return value;
        }
        final int separator = normalized.lastIndexOf('.') + 1;
        if (separator > 0 && separator < normalized.length()) {
            final String simpleKey = normalized.substring(separator);
            value = headTemplates.get(simpleKey);
            if (value != null) {
                return value;
            }
        }
        return headTemplates.get(identifier.trim());
    }

    public Map<String, String> getHeadTemplates() {
        return Collections.unmodifiableMap(headTemplates);
    }

    private void loadTemplates() {
        designTemplates.clear();
        headTemplates.clear();

        final File directory = ensureTemplateDirectory();
        final File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            return;
        }
        for (File file : files) {
            final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            loadHeadTemplates(configuration);
            loadDesignTemplates(configuration);
        }
    }

    private void loadHeadTemplates(final YamlConfiguration configuration) {
        final ConfigurationSection headsRoot = configuration.getConfigurationSection("ui_heads");
        if (headsRoot == null) {
            return;
        }
        collectHeadEntries(headsRoot, "ui_heads");
    }

    private void collectHeadEntries(final ConfigurationSection section, final String pathPrefix) {
        for (String key : section.getKeys(false)) {
            if (key == null) {
                continue;
            }
            final Object value = section.get(key);
            final String path = pathPrefix == null || pathPrefix.isBlank()
                    ? key
                    : pathPrefix + "." + key;
            if (value instanceof ConfigurationSection nested) {
                collectHeadEntries(nested, path);
                continue;
            }
            if (value instanceof String stringValue) {
                final String normalizedPath = path.toLowerCase(Locale.ROOT);
                headTemplates.put(normalizedPath, stringValue);
                headTemplates.putIfAbsent(key.toLowerCase(Locale.ROOT), stringValue);
            }
        }
    }

    private void loadDesignTemplates(final YamlConfiguration configuration) {
        final ConfigurationSection designsSection = configuration.getConfigurationSection("designs");
        if (designsSection == null) {
            return;
        }
        for (String key : designsSection.getKeys(false)) {
            if (key == null) {
                continue;
            }
            final ConfigurationSection designSection = designsSection.getConfigurationSection(key);
            if (designSection == null) {
                LogUtils.warning(plugin, "Design template '" + key + "' is invalid or empty.");
                continue;
            }
            final String normalized = key.toLowerCase(Locale.ROOT);
            designTemplates.put(normalized, new DesignTemplate(normalized, designSection));
        }
    }

    private File ensureTemplateDirectory() {
        final File directory = new File(plugin.getDataFolder(), "config/ui_templates");
        if (!directory.exists() && !directory.mkdirs()) {
            LogUtils.severe(plugin, "Unable to create config/ui_templates directory for UI templates.");
        }
        for (String fileName : DEFAULT_FILES) {
            final File target = new File(directory, fileName);
            if (!target.exists()) {
                plugin.saveResource("config/ui_templates/" + fileName, false);
            }
        }
        return directory;
    }
}
