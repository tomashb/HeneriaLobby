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
    private FileConfiguration lobbyItemsConfig;
    private File messagesFile;
    private File menusFile;
    private File lobbyItemsFile;
    private File serversFile;
    private File velocityFile;
    private FileConfiguration serversConfig;
    private FileConfiguration velocityConfig;

    public ConfigManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        loadMessages();
        loadMenus();
        loadLobbyItems();
        loadServers();
        loadVelocity();
    }

    public void reloadConfigs() {
        plugin.reloadConfig();
        loadMessages();
        loadMenus();
        loadLobbyItems();
        loadServers();
        loadVelocity();
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

    public FileConfiguration getLobbyItemsConfig() {
        if (lobbyItemsConfig == null) {
            loadLobbyItems();
        }
        return lobbyItemsConfig;
    }

    public FileConfiguration getServersConfig() {
        if (serversConfig == null) {
            loadServers();
        }
        return serversConfig;
    }

    public FileConfiguration getVelocityConfig() {
        if (velocityConfig == null) {
            loadVelocity();
        }
        return velocityConfig;
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

    private void loadLobbyItems() {
        if (lobbyItemsFile == null) {
            final File configDirectory = new File(plugin.getDataFolder(), "config");
            if (!configDirectory.exists() && !configDirectory.mkdirs()) {
                plugin.getLogger().severe("Unable to create config directory for lobby items.");
            }
            lobbyItemsFile = new File(configDirectory, "lobby-items.yml");
        }

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().severe("Unable to create plugin data folder for configuration files.");
        }

        if (!lobbyItemsFile.exists()) {
            plugin.saveResource("config/lobby-items.yml", false);
        }

        lobbyItemsConfig = new YamlConfiguration();
        try {
            lobbyItemsConfig.load(lobbyItemsFile);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Unable to load config/lobby-items.yml: " + exception.getMessage());
            loadDefaultLobbyItems();
        }
    }

    private void loadDefaultLobbyItems() {
        lobbyItemsConfig = new YamlConfiguration();
        try (InputStream inputStream = plugin.getResource("config/lobby-items.yml")) {
            if (inputStream != null) {
                Files.copy(inputStream, lobbyItemsFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                lobbyItemsConfig.load(lobbyItemsFile);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Unable to load default config/lobby-items.yml: " + exception.getMessage());
        }
    }

    private void loadServers() {
        if (serversFile == null) {
            final File configDirectory = new File(plugin.getDataFolder(), "config");
            if (!configDirectory.exists() && !configDirectory.mkdirs()) {
                plugin.getLogger().severe("Unable to create config directory for servers configuration.");
            }
            serversFile = new File(configDirectory, "servers.yml");
        }

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().severe("Unable to create plugin data folder for configuration files.");
        }

        if (!serversFile.exists()) {
            plugin.saveResource("config/servers.yml", false);
        }

        serversConfig = new YamlConfiguration();
        try {
            serversConfig.load(serversFile);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Unable to load config/servers.yml: " + exception.getMessage());
            loadDefaultServers();
        }
    }

    private void loadDefaultServers() {
        serversConfig = new YamlConfiguration();
        try (InputStream inputStream = plugin.getResource("config/servers.yml")) {
            if (inputStream != null) {
                Files.copy(inputStream, serversFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                serversConfig.load(serversFile);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Unable to load default config/servers.yml: " + exception.getMessage());
        }
    }

    private void loadVelocity() {
        if (velocityFile == null) {
            final File configDirectory = new File(plugin.getDataFolder(), "config");
            if (!configDirectory.exists() && !configDirectory.mkdirs()) {
                plugin.getLogger().severe("Unable to create config directory for velocity configuration.");
            }
            velocityFile = new File(configDirectory, "velocity.yml");
        }

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().severe("Unable to create plugin data folder for configuration files.");
        }

        if (!velocityFile.exists()) {
            plugin.saveResource("config/velocity.yml", false);
        }

        velocityConfig = new YamlConfiguration();
        try {
            velocityConfig.load(velocityFile);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Unable to load config/velocity.yml: " + exception.getMessage());
            loadDefaultVelocity();
        }
    }

    private void loadDefaultVelocity() {
        velocityConfig = new YamlConfiguration();
        try (InputStream inputStream = plugin.getResource("config/velocity.yml")) {
            if (inputStream != null) {
                Files.copy(inputStream, velocityFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                velocityConfig.load(velocityFile);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Unable to load default config/velocity.yml: " + exception.getMessage());
        }
    }
}
