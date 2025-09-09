package com.heneria.lobby.config;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Handles reading and writing of the activities configuration file.
 */
public class ConfigManager {

    private final File file;
    private final FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "activities.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    private String serialize(Location loc) {
        return loc.getWorld().getName() + "," +
                loc.getBlockX() + "," +
                loc.getBlockY() + "," +
                loc.getBlockZ();
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Parkour
    public void setParkourSpawn(Location loc) {
        config.set("parkour.start", serialize(loc));
        save();
    }

    public void setParkourEnd(Location loc) {
        config.set("parkour.finish", serialize(loc));
        save();
    }

    public int addCheckpoint(Location loc) {
        List<String> list = config.getStringList("parkour.checkpoints");
        list.add(serialize(loc));
        config.set("parkour.checkpoints", list);
        save();
        return list.size();
    }

    public boolean removeCheckpoint(int index) {
        List<String> list = config.getStringList("parkour.checkpoints");
        if (index < 1 || index > list.size()) {
            return false;
        }
        list.remove(index - 1);
        config.set("parkour.checkpoints", list);
        save();
        return true;
    }

    public List<String> getCheckpoints() {
        return config.getStringList("parkour.checkpoints");
    }

    // Mini-foot
    public void setMiniFootSpawn(Location loc) {
        config.set("mini-foot.slime-spawn", serialize(loc));
        save();
    }

    public void setMiniFootGoal(String which, Location min, Location max) {
        config.set("mini-foot.goal-" + which + ".min", serialize(min));
        config.set("mini-foot.goal-" + which + ".max", serialize(max));
        save();
    }
}

