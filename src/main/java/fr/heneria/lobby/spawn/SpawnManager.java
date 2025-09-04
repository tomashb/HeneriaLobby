package fr.heneria.lobby.spawn;

import org.bukkit.Location;

import java.io.File;

public class SpawnManager {

    private final SpawnStorage storage;
    private SpawnPoint spawnPoint;

    public SpawnManager(File file) {
        this.storage = new SpawnStorage(file);
    }

    public void setSpawn(Location location) {
        this.spawnPoint = SpawnPoint.fromLocation(location);
        storage.save(spawnPoint);
    }

    public Location getSpawn() {
        if (spawnPoint == null) {
            return null;
        }
        return spawnPoint.toLocation();
    }

    public void load() {
        this.spawnPoint = storage.load();
    }
}
