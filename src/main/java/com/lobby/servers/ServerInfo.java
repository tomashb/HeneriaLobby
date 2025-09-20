package com.lobby.servers;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

public class ServerInfo {

    private final String id;
    private final String name;
    private final String displayName;
    private final String serverName;
    private final boolean enabled;

    public ServerInfo(final String id, final ConfigurationSection section) {
        this.id = id;
        this.name = section.getString("name", id);
        this.displayName = ChatColor.translateAlternateColorCodes('&', section.getString("display_name", name));
        this.serverName = section.getString("server_name", id);
        this.enabled = section.getBoolean("enabled", true);
    }

    public ServerInfo(final String id, final String name, final String displayName, final String serverName, final boolean enabled) {
        this.id = id;
        this.name = name;
        this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        this.serverName = serverName;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getServerName() {
        return serverName;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
