package fr.heneria.lobby.spawn;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class SpawnStorage {

    private final File file;

    public SpawnStorage(File file) {
        this.file = file;
    }

    public void save(SpawnPoint point) {
        FileConfiguration config = new YamlConfiguration();
        config.set("spawn.world", point.getWorld());
        config.set("spawn.x", point.getX());
        config.set("spawn.y", point.getY());
        config.set("spawn.z", point.getZ());
        config.set("spawn.yaw", point.getYaw());
        config.set("spawn.pitch", point.getPitch());
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SpawnPoint load() {
        if (!file.exists()) {
            return null;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String world = config.getString("spawn.world");
        if (world == null) {
            return null;
        }
        double x = config.getDouble("spawn.x");
        double y = config.getDouble("spawn.y");
        double z = config.getDouble("spawn.z");
        float yaw = (float) config.getDouble("spawn.yaw");
        float pitch = (float) config.getDouble("spawn.pitch");
        return new SpawnPoint(world, x, y, z, yaw, pitch);
    }
}
