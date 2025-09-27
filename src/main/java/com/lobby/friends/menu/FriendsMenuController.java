package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.FriendsDataProvider;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.menus.AssetManager;
import com.lobby.menus.MenuManager;
import org.bukkit.entity.Player;

/**
 * Entry point used to open the redesigned friends main menu for players. The
 * controller keeps the previous API surface so existing code paths continue to
 * work while delegating the actual menu rendering to the new inventory classes.
 */
public class FriendsMenuController {

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final FriendsMenuManager friendsMenuManager;

    public FriendsMenuController(final LobbyPlugin plugin,
                                 final MenuManager menuManager,
                                 final AssetManager assetManager,
                                 final FriendsDataProvider dataProvider,
                                 final FriendsManager friendsManager,
                                 final FriendsMenuManager friendsMenuManager,
                                 final FriendsMenuActionHandler actionHandler) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.friendsMenuManager = friendsMenuManager;
    }

    public void reload() {
        // Nothing to reload with the new inventory-driven implementation.
    }

    public void setActionHandler(final FriendsMenuActionHandler actionHandler) {
        // Kept for API compatibility; no longer used by the simplified menus.
    }

    public boolean openMainMenu(final Player player) {
        if (player == null) {
            return false;
        }
        new FriendsMainMenu(plugin, friendsManager, friendsMenuManager, player).open();
        return true;
    }
}
