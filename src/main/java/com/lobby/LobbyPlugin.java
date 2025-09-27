package com.lobby;

import com.lobby.commands.AdminCommands;
import com.lobby.commands.EconomyCommands;
import com.lobby.commands.NPCCommands;
import com.lobby.commands.PlayerCommands;
import com.lobby.commands.ShopCommands;
import com.lobby.core.ConfigManager;
import com.lobby.core.DatabaseManager;
import com.lobby.core.PlayerDataManager;
import com.lobby.economy.EconomyManager;
import com.lobby.heads.HeadDatabaseManager;
import com.lobby.holograms.HologramManager;
import com.lobby.menus.AssetManager;
import com.lobby.menus.GlobalListener;
import com.lobby.menus.MenuManager;
import com.lobby.menus.prompt.ChatPromptManager;
import com.lobby.menus.confirmation.ConfirmationManager;
import com.lobby.friends.DefaultFriendsDataProvider;
import com.lobby.friends.commands.FriendsTestCommand;
import com.lobby.friends.listeners.FriendAddChatListener;
import com.lobby.friends.manager.BlockedPlayersManager;
import com.lobby.friends.manager.FriendCodeManager;
import com.lobby.friends.manager.FriendsConfigGenerator;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.menu.DefaultFriendsMenuActionHandler;
import com.lobby.friends.menu.FriendsMenuController;
import com.lobby.friends.menu.FriendsMenuManager;
import com.lobby.npcs.NPCInteractionHandler;
import com.lobby.npcs.NPCManager;
import com.lobby.events.PlayerJoinLeaveEvent;
import com.lobby.lobby.LobbyManager;
import com.lobby.lobby.listeners.LobbyItemListener;
import com.lobby.lobby.listeners.LobbyPlayerListener;
import com.lobby.lobby.listeners.LobbyProtectionListener;
import com.lobby.scoreboard.ScoreboardManager;
import com.lobby.servers.ServerManager;
import com.lobby.servers.ServerPlaceholderCache;
import com.lobby.settings.PlayerSettingsManager;
import com.lobby.shop.ShopManager;
import com.lobby.stats.StatsManager;
import com.lobby.tablist.TablistManager;
import com.lobby.tablist.NametagManager;
import com.lobby.velocity.VelocityManager;
import com.lobby.utils.LogUtils;
import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyPlugin extends JavaPlugin {

    private static LobbyPlugin instance;
    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private EconomyManager economyManager;
    private HologramManager hologramManager;
    private NPCManager npcManager;
    private LobbyManager lobbyManager;
    private AssetManager assetManager;
    private MenuManager menuManager;
    private ChatPromptManager chatPromptManager;
    private ConfirmationManager confirmationManager;
    private HeadDatabaseManager headDatabaseManager;
    private ShopManager shopManager;
    private ShopCommands shopCommands;
    private ServerManager serverManager;
    private VelocityManager velocityManager;
    private ServerPlaceholderCache serverPlaceholderCache;
    private StatsManager statsManager;
    private PlayerSettingsManager playerSettingsManager;
    private ScoreboardManager scoreboardManager;
    private NametagManager nametagManager;
    private TablistManager tablistManager;
    private DefaultFriendsDataProvider friendsDataProvider;
    private FriendCodeManager friendCodeManager;
    private FriendsManager friendsManager;
    private BlockedPlayersManager blockedPlayersManager;
    private FriendsMenuController friendsMenuController;
    private FriendsMenuManager friendsMenuManager;
    private FriendAddChatListener friendAddChatListener;

    public static LobbyPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:main");

        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        headDatabaseManager = new HeadDatabaseManager(this);

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            LogUtils.severe(this, "Database initialization failed. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        serverManager = new ServerManager(this);

        playerDataManager = new PlayerDataManager(this, databaseManager);
        statsManager = new StatsManager(this);
        playerSettingsManager = new PlayerSettingsManager(this);
        economyManager = new EconomyManager(this);
        velocityManager = new VelocityManager(this);
        serverPlaceholderCache = new ServerPlaceholderCache(this);
        serverPlaceholderCache.start();
        hologramManager = new HologramManager(this);
        hologramManager.initialize();
        npcManager = new NPCManager(this);
        npcManager.initialize();
        lobbyManager = new LobbyManager(this);
        lobbyManager.applyWorldSettings();
        assetManager = new AssetManager(this);
        chatPromptManager = new ChatPromptManager(this);
        menuManager = new MenuManager(this, assetManager);
        getServer().getPluginManager().registerEvents(new GlobalListener(menuManager), this);
        getServer().getPluginManager().registerEvents(chatPromptManager, this);
        confirmationManager = new ConfirmationManager(this);
        friendsDataProvider = new DefaultFriendsDataProvider();
        friendCodeManager = new FriendCodeManager(this);
        getLogger().info("Gestionnaire de codes d'amis initialisé !");
        blockedPlayersManager = new BlockedPlayersManager(this);
        friendsManager = new FriendsManager(this);
        friendAddChatListener = new FriendAddChatListener(this);
        getServer().getPluginManager().registerEvents(friendAddChatListener, this);
        getLogger().info("Listener d'ajout d'amis enregistré !");
        new FriendsConfigGenerator(this).generate();
        friendsMenuManager = new FriendsMenuManager(this);
        getServer().getPluginManager().registerEvents(friendsMenuManager, this);
        friendsMenuController = new FriendsMenuController(this, menuManager, assetManager, friendsDataProvider,
                friendsManager, friendsMenuManager, new DefaultFriendsMenuActionHandler(this, friendsManager));
        shopManager = new ShopManager(this);
        shopManager.initialize();
        shopCommands = new ShopCommands(this, shopManager);

        scoreboardManager = new ScoreboardManager(this);
        getServer().getPluginManager().registerEvents(scoreboardManager, this);

        nametagManager = new NametagManager(this);

        tablistManager = new TablistManager(this);
        getServer().getPluginManager().registerEvents(tablistManager, this);

        registerCommands();

        getServer().getPluginManager().registerEvents(new PlayerJoinLeaveEvent(this, playerDataManager, economyManager), this);
        getServer().getPluginManager().registerEvents(new LobbyPlayerListener(lobbyManager), this);
        getServer().getPluginManager().registerEvents(new LobbyItemListener(lobbyManager, lobbyManager.getItemManager()), this);
        getServer().getPluginManager().registerEvents(new LobbyProtectionListener(lobbyManager), this);
        getServer().getPluginManager().registerEvents(new NPCInteractionHandler(npcManager), this);

        Material.matchMaterial("STONE");

        LogUtils.info(this, "LobbyCore activé !");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (scoreboardManager != null) {
            scoreboardManager.shutdown();
        }
        if (nametagManager != null) {
            nametagManager.shutdown();
        }
        if (tablistManager != null) {
            tablistManager.shutdown();
        }
        if (hologramManager != null) {
            hologramManager.shutdown();
        }
        if (npcManager != null) {
            npcManager.shutdown();
        }
        if (economyManager != null) {
            economyManager.shutdown();
        }
        if (shopManager != null) {
            shopManager.shutdown();
        }
        if (headDatabaseManager != null) {
            headDatabaseManager.clearCache();
        }
        if (serverPlaceholderCache != null) {
            serverPlaceholderCache.shutdown();
        }
        if (velocityManager != null) {
            velocityManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        if (statsManager != null) {
            statsManager.clearCache();
        }
        if (playerSettingsManager != null) {
            playerSettingsManager.clearCache();
        }
        if (lobbyManager != null) {
            lobbyManager.shutdown();
        }
        if (menuManager != null) {
            menuManager.shutdown();
        }
        if (assetManager != null) {
            assetManager.shutdown();
        }
        chatPromptManager = null;
        if (confirmationManager != null) {
            confirmationManager.clearAll();
        }
        if (friendsManager != null) {
            friendsManager.shutdown();
        }
        if (blockedPlayersManager != null) {
            blockedPlayersManager.shutdown();
        }
        friendsMenuController = null;
        friendsDataProvider = null;
        friendAddChatListener = null;
        instance = null;
    }

    public NametagManager getNametagManager() {
        return nametagManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public NPCManager getNpcManager() {
        return npcManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public ChatPromptManager getChatPromptManager() {
        return chatPromptManager;
    }

    public AssetManager getAssetManager() {
        return assetManager;
    }

    public ConfirmationManager getConfirmationManager() {
        return confirmationManager;
    }

    public HeadDatabaseManager getHeadDatabaseManager() {
        return headDatabaseManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }

    public VelocityManager getVelocityManager() {
        return velocityManager;
    }

    public ServerPlaceholderCache getServerPlaceholderCache() {
        return serverPlaceholderCache;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public PlayerSettingsManager getPlayerSettingsManager() {
        return playerSettingsManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public TablistManager getTablistManager() {
        return tablistManager;
    }

    public FriendsMenuController getFriendsMenuController() {
        return friendsMenuController;
    }

    public FriendsManager getFriendsManager() {
        return friendsManager;
    }

    public BlockedPlayersManager getBlockedPlayersManager() {
        return blockedPlayersManager;
    }

    public DefaultFriendsDataProvider getFriendsDataProvider() {
        return friendsDataProvider;
    }

    public FriendsMenuManager getFriendsMenuManager() {
        return friendsMenuManager;
    }

    public FriendAddChatListener getFriendAddChatListener() {
        return friendAddChatListener;
    }

    public FriendCodeManager getFriendCodeManager() {
        return friendCodeManager;
    }

    public void reloadLobbyConfig() {
        if (configManager != null) {
            configManager.reloadConfigs();
        }
        if (playerDataManager != null) {
            playerDataManager.reload();
        }
        if (economyManager != null) {
            economyManager.reload();
        }
        if (hologramManager != null) {
            hologramManager.reload();
        }
        if (headDatabaseManager != null) {
            headDatabaseManager.reload();
        }
        if (npcManager != null) {
            npcManager.reload();
        }
        if (lobbyManager != null) {
            lobbyManager.reload();
        }
        if (menuManager != null) {
            menuManager.closeAll();
            menuManager.reloadMenus();
        }
        if (friendsMenuController != null) {
            friendsMenuController.reload();
        }
        if (serverManager != null) {
            serverManager.reload();
        }
        if (statsManager != null) {
            statsManager.clearCache();
        }
        if (playerSettingsManager != null) {
            playerSettingsManager.clearCache();
        }
        if (scoreboardManager != null) {
            scoreboardManager.reload();
        }
        if (tablistManager != null) {
            tablistManager.reload();
        }
    }

    private void registerCommands() {
        final PlayerCommands playerCommands = new PlayerCommands(lobbyManager, menuManager, friendsMenuController);
        registerCommand("lobby", playerCommands);
        registerCommand("serveurs", playerCommands);
        registerCommand("profil", playerCommands);
        registerCommand("discord", playerCommands);
        registerCommand("jeux", playerCommands);

        if (shopCommands != null) {
            registerCommand("shop", shopCommands);
        }

        final EconomyCommands economyCommands = new EconomyCommands(this, economyManager);
        registerCommand("coins", economyCommands);
        registerCommand("tokens", economyCommands);
        registerCommand("pay", economyCommands);
        registerCommand("top", economyCommands);

        final AdminCommands adminCommands = new AdminCommands(this, economyManager, hologramManager, npcManager, lobbyManager, shopCommands);
        registerCommand("lobbyadmin", adminCommands);

        final NPCCommands npcCommands = new NPCCommands(this);
        registerCommand("npc", npcCommands);

        registerCommand("friendstest", new FriendsTestCommand(this, friendsManager));
    }

    private void registerCommand(final String name, final CommandExecutor executor) {
        final PluginCommand command = getCommand(name);
        if (command == null) {
            LogUtils.warning(this, "Commande '" + name + "' introuvable dans plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }
}
