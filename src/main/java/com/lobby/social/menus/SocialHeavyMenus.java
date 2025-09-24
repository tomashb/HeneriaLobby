package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.social.clans.Clan;
import com.lobby.social.clans.ClanManager;
import com.lobby.social.friends.FriendInfo;
import com.lobby.social.friends.FriendManager;
import com.lobby.social.groups.Group;
import com.lobby.social.groups.GroupManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entry point for every heavy social menu. Each menu collects all required
 * information asynchronously before delegating to its concrete {@link Menu}
 * implementation on the main thread.
 */
public final class SocialHeavyMenus {

    private SocialHeavyMenus() {
    }

    public static boolean open(final String menuId, final MenuManager menuManager, final Player player) {
        return switch (menuId) {
            case "amis_menu" -> openFriendsMenu(menuManager, player, 0);
            case "groupe_menu" -> openGroupMenu(menuManager, player);
            case "clan_menu" -> openClanMenu(menuManager, player);
            case "clan_management_menu" -> openClanManagementMenu(menuManager, player);
            default -> false;
        };
    }

    public static boolean openFriendsMenu(final MenuManager menuManager, final Player player, final int page) {
        if (menuManager == null || player == null) {
            return false;
        }
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        if (plugin == null) {
            return false;
        }
        final FriendManager friendManager = plugin.getFriendManager();
        final AssetManager assetManager = menuManager.getAssetManager();
        if (friendManager == null || assetManager == null) {
            return false;
        }
        final UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final List<FriendInfo> friends = new ArrayList<>(friendManager.getFriendsList(uuid));
            final int requests = friendManager.getPendingRequests(uuid).size();
            final FriendsMainMenu menu = new FriendsMainMenu(plugin, menuManager, assetManager, friendManager,
                    friends, requests, page);
            menuManager.displayMenu(player, menu);
        });
        return true;
    }

    public static boolean openGroupMenu(final MenuManager menuManager, final Player player) {
        if (menuManager == null || player == null) {
            return false;
        }
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        if (plugin == null) {
            return false;
        }
        final GroupManager groupManager = plugin.getGroupManager();
        final AssetManager assetManager = menuManager.getAssetManager();
        if (groupManager == null || assetManager == null) {
            return false;
        }
        final UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final Group group = groupManager.getPlayerGroup(uuid);
            final int invites = groupManager.countPendingInvitations(uuid);
            final GroupMenu menu = new GroupMenu(plugin, menuManager, assetManager, groupManager, group, invites, uuid);
            menuManager.displayMenu(player, menu);
        });
        return true;
    }

    public static boolean openClanMenu(final MenuManager menuManager, final Player player) {
        if (menuManager == null || player == null) {
            return false;
        }
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        if (plugin == null) {
            return false;
        }
        final ClanManager clanManager = plugin.getClanManager();
        final AssetManager assetManager = menuManager.getAssetManager();
        if (clanManager == null || assetManager == null) {
            return false;
        }
        final UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final Clan clan = clanManager.getPlayerClan(uuid);
            final ClanMenu menu = new ClanMenu(plugin, menuManager, assetManager, clanManager, clan, uuid);
            menuManager.displayMenu(player, menu);
        });
        return true;
    }

    public static boolean openClanManagementMenu(final MenuManager menuManager, final Player player) {
        if (menuManager == null || player == null) {
            return false;
        }
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        if (plugin == null) {
            return false;
        }
        final ClanManager clanManager = plugin.getClanManager();
        final AssetManager assetManager = menuManager.getAssetManager();
        if (clanManager == null || assetManager == null) {
            return false;
        }
        final UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final Clan clan = clanManager.getPlayerClan(uuid);
            final ClanManagementMenu menu = new ClanManagementMenu(plugin, menuManager, assetManager, clan, uuid);
            if (menu.isAccessible()) {
                menuManager.displayMenu(player, menu);
            } else {
                Bukkit.getScheduler().runTask(plugin,
                        () -> player.sendMessage("§cVous devez être chef de clan pour accéder à cette interface."));
            }
        });
        return true;
    }
}
