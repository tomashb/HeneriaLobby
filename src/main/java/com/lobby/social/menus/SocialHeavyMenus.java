package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
import com.lobby.menus.AssetManager;
import com.lobby.menus.ConfiguredMenu;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.menus.MenuRenderContext;
import com.lobby.social.clans.Clan;
import com.lobby.social.clans.ClanManager;
import com.lobby.social.friends.FriendManager;
import com.lobby.social.groups.GroupManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
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
        return open(menuId, menuManager, player, Map.of(), MenuRenderContext.EMPTY);
    }

    public static boolean open(final String menuId,
                               final MenuManager menuManager,
                               final Player player,
                               final Map<String, String> placeholders,
                               final MenuRenderContext context) {
        return switch (menuId) {
            case "amis_menu" -> openFriendsMenu(menuManager, player, placeholders);
            case "groupe_menu" -> openGroupMenu(menuManager, player, placeholders, context);
            case "clan_menu" -> openClanMenu(menuManager, player, placeholders, context);
            case "clan_management_menu" -> openClanManagementMenu(menuManager, player);
            default -> false;
        };
    }

    public static boolean openFriendsMenu(final MenuManager menuManager,
                                          final Player player,
                                          final Map<String, String> additionalPlaceholders) {
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
            final Map<String, String> placeholders = new HashMap<>();
            if (additionalPlaceholders != null) {
                placeholders.putAll(additionalPlaceholders);
            }
            final int requests = friendManager.getPendingRequests(uuid).size();
            final int friends = friendManager.getFriendsList(uuid).size();
            placeholders.putIfAbsent("%friend_requests_count%", Integer.toString(requests));
            placeholders.putIfAbsent("%friends_total%", Integer.toString(friends));
            final ConfiguredMenu menu = ConfiguredMenu.fromConfiguration(plugin, menuManager, assetManager,
                    "amis_menu", placeholders, MenuRenderContext.EMPTY);
            if (menu != null) {
                menuManager.displayMenu(player, menu);
            }
        });
        return true;
    }

    public static boolean openFriendsMenu(final MenuManager menuManager, final Player player, final int page) {
        return openFriendsMenu(menuManager, player, Map.of());
    }

    public static boolean openGroupMenu(final MenuManager menuManager, final Player player) {
        return openGroupMenu(menuManager, player, Map.of(), MenuRenderContext.EMPTY);
    }

    public static boolean openGroupMenu(final MenuManager menuManager,
                                        final Player player,
                                        final Map<String, String> additionalPlaceholders,
                                        final MenuRenderContext baseContext) {
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
            final var placeholders = new HashMap<String, String>();
            if (additionalPlaceholders != null) {
                placeholders.putAll(additionalPlaceholders);
            }
            final var group = groupManager.getPlayerGroup(uuid);
            final int invites = groupManager.countPendingInvitations(uuid);
            placeholders.putIfAbsent("%group_invites_count%", Integer.toString(invites));
            MenuRenderContext context = baseContext == null ? MenuRenderContext.EMPTY : baseContext;
            if (group != null) {
                placeholders.putIfAbsent("%group_name%", group.getDisplayName());
                placeholders.putIfAbsent("%group_members_count%", Integer.toString(group.getSize()));
                placeholders.putIfAbsent("%group_max_members%", Integer.toString(group.getMaxSize()));
                context = context.withGroup(true, group.isLeader(uuid));
            } else {
                context = context.withGroup(false, false);
            }
            final ConfiguredMenu menu = ConfiguredMenu.fromConfiguration(plugin, menuManager, assetManager,
                    "groupe_menu", placeholders, context);
            if (menu != null) {
                menuManager.displayMenu(player, menu);
            }
        });
        return true;
    }

    public static boolean openClanMenu(final MenuManager menuManager, final Player player) {
        return openClanMenu(menuManager, player, Map.of(), MenuRenderContext.EMPTY);
    }

    public static boolean openClanMenu(final MenuManager menuManager,
                                       final Player player,
                                       final Map<String, String> additionalPlaceholders,
                                       final MenuRenderContext baseContext) {
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
            final var placeholders = new HashMap<String, String>();
            if (additionalPlaceholders != null) {
                placeholders.putAll(additionalPlaceholders);
            }
            final Clan clan = clanManager.getPlayerClan(uuid);
            MenuRenderContext context = baseContext == null ? MenuRenderContext.EMPTY : baseContext;
            if (clan != null) {
                placeholders.putIfAbsent("%clan_name%", clan.getName());
                placeholders.putIfAbsent("%clan_members_count%", Integer.toString(clan.getMembers().size()));
                placeholders.putIfAbsent("%clan_level%", Integer.toString(clan.getLevel()));
                context = context.withClan(true, clan.isLeader(uuid));
            } else {
                context = context.withClan(false, false);
            }
            final ConfiguredMenu menu = ConfiguredMenu.fromConfiguration(plugin, menuManager, assetManager,
                    "clan_menu", placeholders, context);
            if (menu != null) {
                menuManager.displayMenu(player, menu);
            }
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
