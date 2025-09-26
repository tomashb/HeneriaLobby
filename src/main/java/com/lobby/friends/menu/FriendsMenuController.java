package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.FriendsDataProvider;
import com.lobby.menus.AssetManager;
import com.lobby.menus.MenuManager;
import org.bukkit.entity.Player;

/**
 * Entry point used to open the friends main menu for players.
 */
public class FriendsMenuController {

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final FriendsDataProvider dataProvider;
    private FriendsMenuActionHandler actionHandler;
    private FriendsMenuConfiguration configuration;

    public FriendsMenuController(final LobbyPlugin plugin,
                                 final MenuManager menuManager,
                                 final AssetManager assetManager,
                                 final FriendsDataProvider dataProvider,
                                 final FriendsMenuActionHandler actionHandler) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.dataProvider = dataProvider;
        this.actionHandler = actionHandler;
        reload();
    }

    public void reload() {
        configuration = FriendsMenuConfigurationLoader.load(plugin);
    }

    public void setActionHandler(final FriendsMenuActionHandler actionHandler) {
        this.actionHandler = actionHandler;
    }

    public boolean openMainMenu(final Player player) {
        if (player == null || configuration == null) {
            return false;
        }
        final FriendsMainMenu menu = new FriendsMainMenu(plugin, assetManager, configuration, dataProvider, actionHandler);
        menuManager.displayMenu(player, menu);
        return true;
    }
}

