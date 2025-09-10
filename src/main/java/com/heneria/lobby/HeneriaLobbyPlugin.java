package com.heneria.lobby;

import com.heneria.lobby.commands.FriendsCommand;
import com.heneria.lobby.commands.LobbyAdminCommand;
import com.heneria.lobby.config.ConfigManager;
import com.heneria.lobby.commands.MsgCommand;
import com.heneria.lobby.commands.OpenMenuCommand;
import com.heneria.lobby.commands.EconomyAdminCommand;
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
import com.heneria.lobby.activities.parkour.ParkourManager;
import com.heneria.lobby.activities.parkour.ParkourCommand;
import com.heneria.lobby.activities.parkour.ParkourListener;
import com.heneria.lobby.activities.football.MiniFootManager;
import com.heneria.lobby.activities.archery.ArcheryListener;
import com.heneria.lobby.economy.EconomyManager;
import com.heneria.lobby.achievements.AchievementManager;
import com.heneria.lobby.cosmetics.CosmeticsManager;
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
    private ParkourManager parkourManager;
    private ConfigManager activitiesConfigManager;
    private EconomyManager economyManager;
    private AchievementManager achievementManager;
    private CosmeticsManager cosmeticsManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("activities.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        activitiesConfigManager = new ConfigManager(this);
        FileConfiguration activitiesConfig = activitiesConfigManager.getConfig();

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.init()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Connected to the database successfully.");

        playerDataManager = new PlayerDataManager(this, databaseManager);
        economyManager = new EconomyManager(this, playerDataManager);
        achievementManager = new AchievementManager(this, databaseManager, economyManager);
        friendManager = new FriendManager(this, databaseManager, achievementManager);
        messageManager = new PrivateMessageManager();
        cosmeticsManager = new CosmeticsManager(this, economyManager, databaseManager);

        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        luckPerms = provider != null ? provider.getProvider() : null;
        scoreboardManager = new ScoreboardManager(this);
        tablistManager = new TablistManager(this, luckPerms);
        serverInfoManager = new ServerInfoManager(this);
        guiManager = new GUIManager(this, serverInfoManager);
        parkourManager = new ParkourManager(this, databaseManager, activitiesConfig, achievementManager);
        new MiniFootManager(this, activitiesConfig);

        getServer().getPluginManager().registerEvents(new PlayerListener(this, playerDataManager, friendManager, messageManager, scoreboardManager, tablistManager, achievementManager), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this, tablistManager), this);
        getServer().getPluginManager().registerEvents(new NavigationItemListener(guiManager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this, guiManager), this);
        getServer().getPluginManager().registerEvents(new ParkourListener(parkourManager), this);
        getServer().getPluginManager().registerEvents(new ArcheryListener(activitiesConfig), this);
        getServer().getPluginManager().registerEvents(cosmeticsManager, this);
        getServer().getPluginManager().registerEvents(
                new com.heneria.lobby.cosmetics.MenuListener(this, cosmeticsManager), this);
        getCommand("lobbyadmin").setExecutor(new LobbyAdminCommand(databaseManager, activitiesConfigManager));
        getCommand("friends").setExecutor(new FriendsCommand(this, friendManager));
        MsgCommand msgCommand = new MsgCommand(this, messageManager);
        getCommand("msg").setExecutor(msgCommand);
        getCommand("r").setExecutor(msgCommand);
        getCommand("games").setExecutor(new OpenMenuCommand(guiManager, "games"));
        getCommand("profil").setExecutor(new OpenMenuCommand(guiManager, "profile"));
        getCommand("shop").setExecutor(new OpenMenuCommand(guiManager, "shop"));
        getCommand("activites").setExecutor(new OpenMenuCommand(guiManager, "activities"));
        getCommand("parkour").setExecutor(new ParkourCommand(parkourManager));
        getCommand("coins").setExecutor(new com.heneria.lobby.commands.CoinsCommand(economyManager));
        getCommand("achievements").setExecutor(new com.heneria.lobby.commands.AchievementsCommand(achievementManager));
        EconomyAdminCommand ecoCommand = new EconomyAdminCommand(economyManager, playerDataManager);
        getCommand("eco").setExecutor(ecoCommand);
        getCommand("eco").setTabCompleter(ecoCommand);

        economyManager.startPassiveRewardTask(20L * 600, 5);

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

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }

    public CosmeticsManager getCosmeticsManager() {
        return cosmeticsManager;
    }
}
