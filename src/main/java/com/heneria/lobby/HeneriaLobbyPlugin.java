package com.heneria.lobby;

import com.heneria.lobby.commands.FriendsCommand;
import com.heneria.lobby.commands.LobbyAdminCommand;
import com.heneria.lobby.commands.MsgCommand;
import com.heneria.lobby.commands.MenuCommand;
import com.heneria.lobby.database.DatabaseManager;
import com.heneria.lobby.listeners.PlayerListener;
import com.heneria.lobby.listeners.ChatListener;
import com.heneria.lobby.listeners.NavigationItemListener;
import com.heneria.lobby.listeners.MenuListener;
import com.heneria.lobby.player.PlayerDataManager;
import com.heneria.lobby.friends.FriendManager;
import com.heneria.lobby.friends.PrivateMessageManager;
import com.heneria.lobby.ui.ScoreboardManager;
import com.heneria.lobby.ui.TablistManager;
import com.heneria.lobby.menu.GUIManager;
import com.heneria.lobby.menu.ServerInfoManager;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;

public class HeneriaLobbyPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private FriendManager friendManager;
    private PrivateMessageManager messageManager;
    private FileConfiguration messages;
    private ScoreboardManager scoreboardManager;
    private TablistManager tablistManager;
    private LuckPerms luckPerms;
    private GUIManager guiManager;
    private ServerInfoManager serverInfoManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.init()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Connected to the database successfully.");

        playerDataManager = new PlayerDataManager(this, databaseManager);
        friendManager = new FriendManager(this, databaseManager);
        messageManager = new PrivateMessageManager();

        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        luckPerms = provider != null ? provider.getProvider() : null;
        scoreboardManager = new ScoreboardManager(this);
        tablistManager = new TablistManager(this, luckPerms);
        serverInfoManager = new ServerInfoManager(this);
        guiManager = new GUIManager(this, serverInfoManager);

        getServer().getPluginManager().registerEvents(new PlayerListener(this, playerDataManager, friendManager, messageManager, scoreboardManager, tablistManager), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this, tablistManager), this);
        getServer().getPluginManager().registerEvents(new NavigationItemListener(guiManager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this, guiManager), this);
        getCommand("lobbyadmin").setExecutor(new LobbyAdminCommand(databaseManager));
        getCommand("friends").setExecutor(new FriendsCommand(this, friendManager));
        getCommand("menu").setExecutor(new MenuCommand(guiManager));
        MsgCommand msgCommand = new MsgCommand(this, messageManager);
        getCommand("msg").setExecutor(msgCommand);
        getCommand("r").setExecutor(msgCommand);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "heneria:friends");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "heneria:msg");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", serverInfoManager);
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public ServerInfoManager getServerInfoManager() {
        return serverInfoManager;
    }
}
