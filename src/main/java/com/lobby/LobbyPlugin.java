package com.lobby;

import com.lobby.commands.AdminCommands;
import com.lobby.commands.ClanCommand;
import com.lobby.commands.EconomyCommands;
import com.lobby.commands.FriendCommand;
import com.lobby.commands.GroupCommand;
import com.lobby.commands.NPCCommands;
import com.lobby.commands.PlayerCommands;
import com.lobby.commands.ShopCommands;
import com.lobby.core.ConfigManager;
import com.lobby.core.DatabaseManager;
import com.lobby.core.PlayerDataManager;
import com.lobby.economy.EconomyManager;
import com.lobby.heads.HeadDatabaseManager;
import com.lobby.holograms.HologramManager;
import com.lobby.menus.MenuManager;
import com.lobby.menus.confirmation.ConfirmationManager;
import com.lobby.menus.templates.UITemplateManager;
import com.lobby.npcs.NPCInteractionHandler;
import com.lobby.npcs.NPCManager;
import com.lobby.events.PlayerJoinLeaveEvent;
import com.lobby.lobby.LobbyManager;
import com.lobby.lobby.listeners.LobbyItemListener;
import com.lobby.lobby.listeners.LobbyPlayerListener;
import com.lobby.lobby.listeners.LobbyProtectionListener;
import com.lobby.servers.ServerManager;
import com.lobby.shop.ShopManager;
import com.lobby.social.ChatInputManager;
import com.lobby.social.SocialPlaceholderManager;
import com.lobby.social.menus.MenuClickHandler;
import com.lobby.social.clans.ClanManager;
import com.lobby.social.friends.FriendManager;
import com.lobby.social.groups.GroupManager;
import com.lobby.velocity.VelocityManager;
import com.lobby.utils.LogUtils;
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
    private MenuManager menuManager;
    private UITemplateManager uiTemplateManager;
    private ConfirmationManager confirmationManager;
    private HeadDatabaseManager headDatabaseManager;
    private ShopManager shopManager;
    private ShopCommands shopCommands;
    private ServerManager serverManager;
    private FriendManager friendManager;
    private GroupManager groupManager;
    private ClanManager clanManager;
    private VelocityManager velocityManager;
    private SocialPlaceholderManager socialPlaceholderManager;
    private ChatInputManager chatInputManager;

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
        economyManager = new EconomyManager(this);
        velocityManager = new VelocityManager(this);
        friendManager = new FriendManager(this);
        groupManager = new GroupManager(this);
        clanManager = new ClanManager(this);
        socialPlaceholderManager = new SocialPlaceholderManager(this);
        hologramManager = new HologramManager(this);
        hologramManager.initialize();
        npcManager = new NPCManager(this);
        npcManager.initialize();
        lobbyManager = new LobbyManager(this);
        lobbyManager.applyWorldSettings();
        uiTemplateManager = new UITemplateManager(this);
        menuManager = new MenuManager(this);
        confirmationManager = new ConfirmationManager(this);
        shopManager = new ShopManager(this);
        shopManager.initialize();
        shopCommands = new ShopCommands(this, shopManager);
        chatInputManager = new ChatInputManager(this);

        registerCommands();

        getServer().getPluginManager().registerEvents(new PlayerJoinLeaveEvent(this, playerDataManager, economyManager), this);
        getServer().getPluginManager().registerEvents(new LobbyPlayerListener(lobbyManager), this);
        getServer().getPluginManager().registerEvents(new LobbyItemListener(lobbyManager, lobbyManager.getItemManager()), this);
        getServer().getPluginManager().registerEvents(new LobbyProtectionListener(lobbyManager), this);
        getServer().getPluginManager().registerEvents(new NPCInteractionHandler(npcManager), this);
        getServer().getPluginManager().registerEvents(new MenuClickHandler(this), this);

        LogUtils.info(this, "LobbyCore activé !");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
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
        if (velocityManager != null) {
            velocityManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        if (lobbyManager != null) {
            lobbyManager.shutdown();
        }
        if (menuManager != null) {
            menuManager.closeAll();
        }
        if (confirmationManager != null) {
            confirmationManager.clearAll();
        }
        instance = null;
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

    public UITemplateManager getUiTemplateManager() {
        return uiTemplateManager;
    }

    public HeadDatabaseManager getHeadDatabaseManager() {
        return headDatabaseManager;
    }

    public ConfirmationManager getConfirmationManager() {
        return confirmationManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }

    public FriendManager getFriendManager() {
        return friendManager;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    public VelocityManager getVelocityManager() {
        return velocityManager;
    }

    public SocialPlaceholderManager getSocialPlaceholderManager() {
        return socialPlaceholderManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
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
        if (serverManager != null) {
            serverManager.reload();
        }
        if (friendManager != null) {
            friendManager.reload();
        }
        if (groupManager != null) {
            groupManager.reload();
        }
        if (clanManager != null) {
            clanManager.reload();
        }
    }

    private void registerCommands() {
        final PlayerCommands playerCommands = new PlayerCommands(lobbyManager, menuManager);
        registerCommand("lobby", playerCommands);
        registerCommand("serveurs", playerCommands);
        registerCommand("profil", playerCommands);
        registerCommand("discord", playerCommands);

        if (shopCommands != null) {
            registerCommand("shop", shopCommands);
        }

        final EconomyCommands economyCommands = new EconomyCommands(this, economyManager);
        registerCommand("coins", economyCommands);
        registerCommand("tokens", economyCommands);
        registerCommand("pay", economyCommands);
        registerCommand("top", economyCommands);

        final FriendCommand friendCommand = new FriendCommand(friendManager);
        registerCommand("friend", friendCommand);

        final GroupCommand groupCommand = new GroupCommand(groupManager);
        registerCommand("group", groupCommand);

        final ClanCommand clanCommand = new ClanCommand(clanManager);
        registerCommand("clan", clanCommand);

        final AdminCommands adminCommands = new AdminCommands(this, economyManager, hologramManager, npcManager, lobbyManager, shopCommands);
        registerCommand("lobbyadmin", adminCommands);

        final NPCCommands npcCommands = new NPCCommands(this);
        registerCommand("npc", npcCommands);
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
