package fr.heneria.lobby.spawn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class SpawnStorageTest {

    @TempDir
    File tempDir;

    @Test
    public void testSaveAndLoad() {
        File file = new File(tempDir, "locations.yml");
        SpawnStorage storage = new SpawnStorage(file);
        SpawnPoint point = new SpawnPoint("world", 1.5, 64.0, -3.2, 90f, 45f);
        storage.save(point);

        SpawnStorage storage2 = new SpawnStorage(file);
        SpawnPoint loaded = storage2.load();

        assertNotNull(loaded);
        assertEquals(point.getWorld(), loaded.getWorld());
        assertEquals(point.getX(), loaded.getX());
        assertEquals(point.getY(), loaded.getY());
        assertEquals(point.getZ(), loaded.getZ());
        assertEquals(point.getYaw(), loaded.getYaw());
        assertEquals(point.getPitch(), loaded.getPitch());
    }
}
