package fr.heneria.lobby.messages;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class MessageManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String key) {
        return ChatColor.translateAlternateColorCodes('&', config.getString(key, key));
    }

    public void reload() {
        load();
    }
}
