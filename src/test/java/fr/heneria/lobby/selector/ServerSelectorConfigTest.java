package fr.heneria.lobby.selector;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class ServerSelectorConfigTest {

    @Test
    public void testBedwarsConfigurationExists() {
        FileConfiguration config = load();
        assertEquals("&6Menu des jeux", config.getString("menu-title"));
        assertEquals(3, config.getInt("menu-size"));
        assertNotNull(config.getConfigurationSection("items.bedwars"));
        assertEquals(13, config.getInt("items.bedwars.slot"));
        assertEquals("HAY_BLOCK", config.getString("items.bedwars.material"));
    }

    private FileConfiguration load() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("server-selector.yml"))));
    }
}
