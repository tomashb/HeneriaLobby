package fr.heneria.lobby;

import fr.heneria.lobby.commands.LobbyCommand;
import fr.heneria.lobby.commands.ServersCommand;
import fr.heneria.lobby.commands.SpawnCommand;
import fr.heneria.lobby.listeners.PlayerListener;
import fr.heneria.lobby.messages.MessageManager;
import fr.heneria.lobby.selector.ServerSelectorManager;
import fr.heneria.lobby.spawn.SpawnManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class LobbyPlugin extends JavaPlugin {

    private SpawnManager spawnManager;
    private MessageManager messageManager;
    private ServerSelectorManager serverSelectorManager;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // not used but ensures data folder
        this.messageManager = new MessageManager(this);
        File spawnFile = new File(getDataFolder(), "locations.yml");
        this.spawnManager = new SpawnManager(spawnFile);
        this.spawnManager.load();
        this.serverSelectorManager = new ServerSelectorManager(this);
        this.serverSelectorManager.load();

        getCommand("lobby").setExecutor(new LobbyCommand(this));
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("servers").setExecutor(new ServersCommand(this));

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(serverSelectorManager, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public ServerSelectorManager getServerSelectorManager() {
        return serverSelectorManager;
    }
}
