package fr.heneria.lobby;

import fr.heneria.lobby.commands.LobbyCommand;
import fr.heneria.lobby.commands.SpawnCommand;
import fr.heneria.lobby.listeners.PlayerListener;
import fr.heneria.lobby.messages.MessageManager;
import fr.heneria.lobby.spawn.SpawnManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class LobbyPlugin extends JavaPlugin {

    private SpawnManager spawnManager;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // not used but ensures data folder
        this.messageManager = new MessageManager(this);
        File spawnFile = new File(getDataFolder(), "locations.yml");
        this.spawnManager = new SpawnManager(spawnFile);
        this.spawnManager.load();

        getCommand("lobby").setExecutor(new LobbyCommand(this));
        getCommand("spawn").setExecutor(new SpawnCommand(this));

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }
}
