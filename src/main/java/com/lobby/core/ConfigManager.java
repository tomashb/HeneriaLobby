package com.lobby.core;

import com.lobby.LobbyPlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class ConfigManager {

    private final LobbyPlugin plugin;
    private FileConfiguration messagesConfig;
    private FileConfiguration menusConfig;
    private File messagesFile;
    private File menusFile;

    public ConfigManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        loadMessages();
        loadMenus();
    }

    public void reloadConfigs() {
        plugin.reloadConfig();
        loadMessages();
        loadMenus();
    }

    public FileConfiguration getMainConfig() {
        return plugin.getConfig();
    }

    public FileConfiguration getMessagesConfig() {
        if (messagesConfig == null) {
            loadMessages();
        }
        return messagesConfig;
    }

    public FileConfiguration getMenusConfig() {
        if (menusConfig == null) {
            loadMenus();
        }
        return menusConfig;
    }

    public boolean isDebugEnabled() {
        return plugin.getConfig().getBoolean("plugin.debug", false);
    }

    private void loadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().severe("Unable to create plugin data folder for configuration files.");
        }

        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = new YamlConfiguration();
        try {
            messagesConfig.load(messagesFile);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Unable to load messages.yml: " + exception.getMessage());
            loadDefaultMessages();
        }
    }

    private void loadDefaultMessages() {
        messagesConfig = new YamlConfiguration();
        try (InputStream inputStream = plugin.getResource("messages.yml")) {
            if (inputStream != null) {
                Files.copy(inputStream, messagesFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                messagesConfig.load(messagesFile);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Unable to load default messages.yml: " + exception.getMessage());
        }
    }

    private void loadMenus() {
        if (menusFile == null) {
            final File configDirectory = new File(plugin.getDataFolder(), "config");
            if (!configDirectory.exists() && !configDirectory.mkdirs()) {
                plugin.getLogger().severe("Unable to create config directory for menus.");
            }
            menusFile = new File(configDirectory, "menus.yml");
        }

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().severe("Unable to create plugin data folder for configuration files.");
        }

        if (!menusFile.exists()) {
            plugin.saveResource("config/menus.yml", false);
        }

        menusConfig = new YamlConfiguration();
        try {
            menusConfig.load(menusFile);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Unable to load config/menus.yml: " + exception.getMessage());
            loadDefaultMenus();
        }
    }

    private void loadDefaultMenus() {
        menusConfig = new YamlConfiguration();
        try (InputStream inputStream = plugin.getResource("config/menus.yml")) {
            if (inputStream != null) {
                Files.copy(inputStream, menusFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                menusConfig.load(menusFile);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Unable to load default config/menus.yml: " + exception.getMessage());
        }
    }
}
