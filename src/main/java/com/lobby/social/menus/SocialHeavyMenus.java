package com.lobby.social.menus;

import com.lobby.LobbyPlugin;
import com.lobby.economy.EconomyManager;
import com.lobby.menus.AssetManager;
import com.lobby.menus.ConfiguredMenu;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.menus.MenuRenderContext;
import com.lobby.social.clans.Clan;
import com.lobby.social.clans.ClanManager;
import com.lobby.social.clans.ClanMember;
import com.lobby.social.clans.ClanSummary;
import com.lobby.social.friends.FriendInfo;
import com.lobby.social.friends.FriendManager;
import com.lobby.social.friends.FriendRequest;
import com.lobby.social.groups.GroupManager;
import com.lobby.social.menus.friends.FriendGiftMenu;
import com.lobby.social.menus.friends.FriendRequestsMenu;
import com.lobby.social.menus.friends.FriendsMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

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
        final String searchTerm = placeholders == null ? null
                : placeholders.getOrDefault("%friends_search%", placeholders.getOrDefault("friends_search", null));
        return switch (menuId) {
            case "amis_menu" -> openFriendsMenu(menuManager, player, 0, searchTerm);
            case "amis_requests_menu" -> openFriendRequestsMenu(menuManager, player, 0);
            case "amis_settings_menu" -> openFriendSettingsMenu(menuManager, player);
            case "amis_gift_menu" -> openFriendGiftMenu(menuManager, player, placeholders);
            case "groupe_menu" -> openGroupMenu(menuManager, player, placeholders, context);
            case "party_invites_menu" -> openPartyInvitesMenu(menuManager, player, 0);
            case "clan_menu" -> openClanMenu(menuManager, player, placeholders, context);
            case "clan_list_menu" -> openClanListMenu(menuManager, player, 0);
            case "clan_members_menu" -> openClanMembersMenu(menuManager, player);
            case "clan_bank_menu" -> openClanBankMenu(menuManager, player);
            case "clan_management_menu" -> openClanManagementMenu(menuManager, player);
            default -> false;
        };
    }

    public static boolean openFriendsMenu(final MenuManager menuManager, final Player player, final int page) {
        return openFriendsMenu(menuManager, player, page, null);
    }

    public static boolean openFriendsMenu(final MenuManager menuManager,
                                          final Player player,
                                          final int page,
                                          final String rawSearchTerm) {
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
            final List<FriendInfo> allFriends = friendManager.getFriendsList(uuid);
            final String searchTerm = sanitizeSearchTerm(rawSearchTerm);
            final List<FriendInfo> filteredFriends;
            if (searchTerm == null) {
                filteredFriends = allFriends;
            } else {
                final String lower = searchTerm.toLowerCase(Locale.ROOT);
                final List<FriendInfo> matches = new ArrayList<>();
                for (FriendInfo info : allFriends) {
                    if (info.getName().toLowerCase(Locale.ROOT).contains(lower)) {
                        matches.add(info);
                    }
                }
                filteredFriends = matches;
            }
            final int requests = friendManager.getPendingRequests(uuid).size();
            final String sortMode = searchTerm == null ? "§aPriorité intelligente" : "§dRecherche";
            final FriendsMenu menu = new FriendsMenu(plugin, menuManager, assetManager, friendManager,
                    filteredFriends, requests, sortMode, searchTerm, Math.max(0, page));
            menuManager.displayMenu(player, menu);
        });
        return true;
    }

    private static String sanitizeSearchTerm(final String input) {
        if (input == null) {
            return null;
        }
        final String trimmed = ChatColor.stripColor(input).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean openFriendRequestsMenu(final MenuManager menuManager, final Player player, final int page) {
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
            final List<FriendRequestsMenu.FriendRequestEntry> entries = new ArrayList<>();
            for (FriendRequest request : friendManager.getPendingRequestsDetailed(uuid)) {
                entries.add(FriendRequestsMenu.FriendRequestEntry.from(request));
            }
            final FriendRequestsMenu menu = new FriendRequestsMenu(plugin, menuManager, assetManager,
                    friendManager, entries, Math.max(0, page));
            menuManager.displayMenu(player, menu);
        });
        return true;
    }

    public static boolean openFriendSettingsMenu(final MenuManager menuManager, final Player player) {
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
            final var settings = friendManager.getFriendSettings(uuid);
            final Map<String, String> placeholders = new HashMap<>();
            final String requestStatus = switch (settings.getAcceptRequests()) {
                case ALL -> "§aTous";
                case FRIENDS_OF_FRIENDS -> "§eAmis d'amis";
                case NONE -> "§cPersonne";
            };
            placeholders.put("%friend_requests_status%", requestStatus);
            placeholders.put("%messaging_status%",
                    settings.isAllowPrivateMessages() ? "§aAutorisés" : "§cBloqués");
            placeholders.put("%visibility_status%",
                    settings.isShowOnlineStatus() ? "§aVisible" : "§cInvisible");
            placeholders.put("%friend_notifications_status%",
                    settings.isAllowNotifications() ? "§aActivées" : "§cDésactivées");
            final ConfiguredMenu menu = ConfiguredMenu.fromConfiguration(plugin, menuManager, assetManager,
                    "amis_settings_menu", placeholders, MenuRenderContext.EMPTY);
            if (menu != null) {
                menuManager.displayMenu(player, menu);
            }
        });
        return true;
    }

    public static boolean openFriendGiftMenu(final MenuManager menuManager,
                                             final Player player,
                                             final Map<String, String> placeholders) {
        if (menuManager == null || player == null) {
            return false;
        }
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        if (plugin == null) {
            return false;
        }
        final AssetManager assetManager = menuManager.getAssetManager();
        if (assetManager == null) {
            return false;
        }
        final String rawUuid = placeholders == null ? null
                : placeholders.getOrDefault("%target_uuid%", placeholders.get("target_uuid"));
        if (rawUuid == null || rawUuid.isBlank()) {
            player.sendMessage("§cDestinataire inconnu.");
            return false;
        }
        final UUID targetUuid;
        try {
            targetUuid = UUID.fromString(rawUuid);
        } catch (final IllegalArgumentException exception) {
            player.sendMessage("§cDestinataire invalide.");
            return false;
        }
        final String targetName = placeholders == null ? null
                : placeholders.getOrDefault("%target_name%", placeholders.getOrDefault("target_name", "Inconnu"));
        final EconomyManager economyManager = plugin.getEconomyManager();
        Bukkit.getScheduler().runTask(plugin, () -> {
            final FriendGiftMenu menu = new FriendGiftMenu(plugin, menuManager, assetManager,
                    economyManager, targetUuid, targetName);
            menuManager.displayMenu(player, menu);
        });
        return true;
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

    public static boolean openPartyInvitesMenu(final MenuManager menuManager, final Player player, final int page) {
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
            final var invites = groupManager.getPendingInvitations(uuid).stream()
                    .map(invite -> {
                        final var leaderName = Bukkit.getOfflinePlayer(invite.getInviter()).getName();
                        final var group = groupManager.getGroupSnapshot(invite.getGroupId());
                        final int size = group != null ? group.getSize() : 1;
                        final String displayLeader = leaderName != null ? leaderName : invite.getInviter().toString().substring(0, 8).toUpperCase(Locale.ROOT);
                        return new PartyInvitesMenu.PartyInviteEntry(invite.getInviter(), displayLeader, size);
                    })
                    .collect(Collectors.toList());
            final PartyInvitesMenu menu = new PartyInvitesMenu(menuManager, assetManager, invites, page);
            menuManager.displayMenu(player, menu);
        });
        return true;
    }

    public static boolean openClanMenu(final MenuManager menuManager, final Player player) {
        return openClanMenu(menuManager, player, Map.of(), MenuRenderContext.EMPTY);
    }

    public static boolean openClanListMenu(final MenuManager menuManager, final Player player, final int page) {
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
        final int requestedPage = Math.max(0, page);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final int total = clanManager.countPublicClans();
            final int pageSize = ClanListMenu.CLANS_PER_PAGE;
            final int maxPage = total <= 0 ? 0 : Math.max(0, (int) Math.ceil((double) total / pageSize) - 1);
            final int targetPage = Math.min(requestedPage, maxPage);
            final int offset = targetPage * pageSize;
            final List<ClanSummary> clans = clanManager.listPublicClans(pageSize, offset);
            final ClanListMenu menu = new ClanListMenu(menuManager, assetManager, clans, targetPage, total, pageSize);
            menuManager.displayMenu(player, menu);
        });
        return true;
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

    public static boolean openClanMembersMenu(final MenuManager menuManager, final Player player) {
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
            if (clan == null) {
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§cVous n'avez pas de clan."));
                return;
            }
            final List<ClanMember> members = new ArrayList<>(clanManager.getClanMembers(clan.getId()));
            final ClanMembersMenu menu = new ClanMembersMenu(menuManager, assetManager, clan, uuid, members);
            menuManager.displayMenu(player, menu);
        });
        return true;
    }

    public static boolean openClanBankMenu(final MenuManager menuManager, final Player player) {
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
            if (clan == null) {
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§cVous n'avez pas de clan."));
                return;
            }
            final ClanBankMenu menu = new ClanBankMenu(menuManager, assetManager, clanManager, clan, uuid);
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
