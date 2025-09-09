package com.heneria.lobby.ui;

import com.heneria.lobby.HeneriaLobbyPlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * Handles tab list formatting and header/footer.
 */
public class TablistManager {

    private final HeneriaLobbyPlugin plugin;
    private final LuckPerms luckPerms;
    private String header;
    private String footer;
    private String nameFormat;

    public TablistManager(HeneriaLobbyPlugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        loadConfig();
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    private void loadConfig() {
        plugin.saveResource("tablist.yml", false);
        File file = new File(plugin.getDataFolder(), "tablist.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.header = ChatColor.translateAlternateColorCodes('&', config.getString("header", ""));
        this.footer = ChatColor.translateAlternateColorCodes('&', config.getString("footer", ""));
        this.nameFormat = config.getString("name-format", "{prefix}{name}");
    }

    public void update(Player player) {
        String prefix = getPrefix(player);
        String formatted = nameFormat
                .replace("{prefix}", prefix == null ? "" : prefix)
                .replace("{name}", player.getName())
                .replace("{ping}", String.valueOf(player.getPing()));
        player.setPlayerListHeaderFooter(header, footer);
        player.setPlayerListName(ChatColor.translateAlternateColorCodes('&', formatted));
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public String getPrefix(Player player) {
        if (luckPerms == null) {
            return "";
        }
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        CachedMetaData meta = user.getCachedData().getMetaData();
        String prefix = meta.getPrefix();
        return prefix != null ? ChatColor.translateAlternateColorCodes('&', prefix) : "";
    }
}
