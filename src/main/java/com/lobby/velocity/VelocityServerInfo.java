package com.lobby.velocity;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

public class VelocityServerInfo {

    private final String id;
    private final String name;
    private final String displayName;
    private final String description;
    private boolean enabled;

    public VelocityServerInfo(final String id, final ConfigurationSection section) {
        this(id,
                section.getString("name", id),
                section.getString("display_name", id),
                section.getString("description", ""),
                section.getBoolean("enabled", true));
    }

    public VelocityServerInfo(final String id, final String name, final String displayName,
                               final String description, final boolean enabled) {
        this.id = id.toLowerCase();
        this.name = name;
        this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        this.description = description == null ? "" : ChatColor.translateAlternateColorCodes('&', description);
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

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
