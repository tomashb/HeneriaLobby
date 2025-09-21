package com.lobby.social;

import com.lobby.LobbyPlugin;
import com.lobby.economy.EconomyManager;
import com.lobby.social.clans.Clan;
import com.lobby.social.clans.ClanManager;
import com.lobby.social.clans.ClanMember;
import com.lobby.social.clans.ClanPermission;
import com.lobby.social.clans.ClanRank;
import com.lobby.social.friends.AcceptMode;
import com.lobby.social.friends.FriendInfo;
import com.lobby.social.friends.FriendManager;
import com.lobby.social.friends.FriendSettings;
import com.lobby.social.groups.Group;
import com.lobby.social.groups.GroupManager;
import com.lobby.social.groups.GroupSettings;
import com.lobby.social.groups.GroupVisibility;
import com.lobby.velocity.VelocityManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SocialPlaceholderManager {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withLocale(Locale.FRENCH);

    private final LobbyPlugin plugin;
    private final Map<UUID, UUID> clanPermissionTargets = new ConcurrentHashMap<>();

    public SocialPlaceholderManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public String replacePlaceholders(final Player player, final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String replaced = text;
        replaced = applyEconomyPlaceholders(player, replaced);
        replaced = applyStatisticPlaceholders(player, replaced);
        replaced = applyFriendPlaceholders(player, replaced);
        replaced = applyGroupPlaceholders(player, replaced);
        replaced = applyClanPlaceholders(player, replaced);
        replaced = applyServerPlaceholders(replaced);
        return replaced;
    }

    private String applyStatisticPlaceholders(final Player player, final String text) {
        final Map<String, String> replacements = new HashMap<>();
        replacements.put("%stats_pvp_wins%", "0");
        replacements.put("%stats_pvp_losses%", "0");
        replacements.put("%stats_pvp_ratio%", "0.0");
        replacements.put("%stats_bedwars_wins%", "0");
        replacements.put("%stats_skywars_wins%", "0");
        replacements.put("%stats_arcade_score%", "0");
        replacements.put("%stats_achievements_unlocked%", "0");
        replacements.put("%stats_achievements_remaining%", "0");
        replacements.put("%player_language%", "Français");
        replacements.put("%daily_reward_cooldown%", "Disponible");
        replacements.put("%daily_reward_name%", "Mystery Box");
        replacements.put("%daily_reward_streak%", "0 jour");
        replacements.put("%player_level%", "1");
        replacements.put("%player_experience%", "0");
        replacements.put("%player_playtime_total%", "0s");
        replacements.put("%luckperms_prefix%", "&7Joueur");
        return replaceAll(text, replacements);
    }

    private String applyEconomyPlaceholders(final Player player, final String text) {
        if (player == null) {
            return text;
        }
        final EconomyManager economyManager = plugin.getEconomyManager();
        if (economyManager == null) {
            return text;
        }
        final Map<String, String> replacements = new HashMap<>();
        replacements.put("%player_coins%", String.valueOf(economyManager.getCoins(player.getUniqueId())));
        replacements.put("%player_tokens%", String.valueOf(economyManager.getTokens(player.getUniqueId())));
        return replaceAll(text, replacements);
    }

    private String applyFriendPlaceholders(final Player player, final String text) {
        final Map<String, String> replacements = new HashMap<>();
        replacements.put("%friends_total%", "0");
        replacements.put("%friends_total_count%", "0");
        replacements.put("%friends_current_count%", "0");
        replacements.put("%friends_online%", "0");
        replacements.put("%friends_online_count%", "0");
        replacements.put("%friends_popular_servers%", "Aucun");
        replacements.put("%friends_recent_activity%", "Aucune activité");
        replacements.put("%friend_requests_received%", "0");
        replacements.put("%friend_requests_sent%", "0");
        replacements.put("%friend_requests_oldest%", "Aucune");
        replacements.put("%friends_favorites%", "0");
        replacements.put("%friends_favorites_count%", "0");
        replacements.put("%friends_last_seen%", "Aucune donnée");
        replacements.put("%friend_limits%", "Illimité");
        replacements.put("%friend_request_status%", "Disponibles");
        replacements.put("%friend_auto_accept%", "Tous");
        replacements.put("%friend_notifications%", "Activées");
        replacements.put("%friend_notifications_status%", "Activées");
        replacements.put("%friend_visibility%", "Visible");
        replacements.put("%friend_visibility_mode%", "Visible");
        replacements.put("%friend_auto_favorites%", "Désactivée");
        replacements.put("%friend_auto_favorites_status%", "Désactivée");
        replacements.put("%friend_request_mode%", "Tous");
        replacements.put("%friend_private_messages_status%", "Autorisés");
        replacements.put("%friend_max_friends%", "Illimité");
        replacements.put("%friends_free_slots%", "Illimité");
        replacements.put("%friend_requests%", "0");
        replacements.put("%friend_status%", "Disponible");

        if (player == null) {
            return replaceAll(text, replacements);
        }

        final FriendManager friendManager = plugin.getFriendManager();
        if (friendManager == null) {
            return replaceAll(text, replacements);
        }

        final List<FriendInfo> friends = friendManager.getFriendsList(player.getUniqueId());
        final long online = friends.stream().filter(FriendInfo::isOnline).count();
        final long favoritesCount = friends.stream().filter(FriendInfo::isFavorite).count();
        final int totalFriends = friends.size();
        replacements.put("%friends_total%", String.valueOf(totalFriends));
        replacements.put("%friends_total_count%", String.valueOf(totalFriends));
        replacements.put("%friends_current_count%", String.valueOf(totalFriends));
        replacements.put("%friends_online%", String.valueOf(online));
        replacements.put("%friends_online_count%", String.valueOf(online));
        replacements.put("%friends_favorites_count%", String.valueOf(favoritesCount));
        if (favoritesCount > 0) {
            final String favoritesNames = friends.stream()
                    .filter(FriendInfo::isFavorite)
                    .map(FriendInfo::getName)
                    .collect(Collectors.joining(", "));
            replacements.put("%friends_favorites%", favoritesNames);
        }

        final String popularServers = friends.stream()
                .filter(FriendInfo::isOnline)
                .map(FriendInfo::getServer)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(server -> server, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(2)
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));
        if (!popularServers.isEmpty()) {
            replacements.put("%friends_popular_servers%", popularServers);
        }

        final Optional<FriendInfo> mostRecentFriend = friends.stream()
                .max(Comparator.comparingLong(FriendInfo::getLastSeen));
        mostRecentFriend.ifPresent(friend -> {
            final String relative = formatRelativeTime(friend.getLastSeen());
            replacements.put("%friends_recent_activity%", friend.getName() + " - " + relative);
            replacements.put("%friends_last_seen%", friend.getName() + " - " + relative);
        });

        final int requestsReceived = friendManager.getPendingRequests(player.getUniqueId()).size();
        replacements.put("%friend_requests_received%", String.valueOf(requestsReceived));
        replacements.put("%friend_requests%", String.valueOf(requestsReceived));

        final int requestsSent = friendManager.countSentRequests(player.getUniqueId());
        replacements.put("%friend_requests_sent%", String.valueOf(requestsSent));

        final long oldestRequest = friendManager.getOldestPendingRequestTimestamp(player.getUniqueId());
        if (oldestRequest > 0L) {
            replacements.put("%friend_requests_oldest%", formatRelativeTime(oldestRequest));
        }

        final FriendSettings settings = friendManager.getFriendSettings(player.getUniqueId());
        replacements.put("%friend_auto_accept%", settings.isAutoAcceptFavorites()
                ? "Favoris auto"
                : formatAcceptMode(settings.getAcceptRequests()));
        replacements.put("%friend_request_mode%", formatDetailedAcceptMode(settings.getAcceptRequests()));

        final boolean notificationsEnabled = settings.isAllowNotifications();
        replacements.put("%friend_notifications%", notificationsEnabled ? "Activées" : "Désactivées");
        replacements.put("%friend_notifications_status%", notificationsEnabled ? "Activées" : "Désactivées");

        final boolean visible = settings.isShowOnlineStatus();
        replacements.put("%friend_visibility%", visible ? "Visible" : "Caché");
        replacements.put("%friend_visibility_mode%", formatVisibilityMode(visible));
        replacements.put("%friend_status%", visible ? "Disponible" : "Invisible");

        final boolean autoFavorites = settings.isAutoAcceptFavorites();
        replacements.put("%friend_auto_favorites%", autoFavorites ? "Activée" : "Désactivée");
        replacements.put("%friend_auto_favorites_status%", autoFavorites ? "Activée" : "Désactivée");

        final boolean pmEnabled = settings.isAllowPrivateMessages();
        replacements.put("%friend_private_messages_status%", pmEnabled ? "Autorisés" : "Désactivés");

        final int maxFriends = settings.getMaxFriends();
        if (maxFriends <= 0) {
            replacements.put("%friend_limits%", "Illimité");
            replacements.put("%friend_max_friends%", "Illimité");
            replacements.put("%friends_free_slots%", "Illimité");
        } else {
            replacements.put("%friend_limits%", maxFriends + " slots");
            replacements.put("%friend_max_friends%", String.valueOf(maxFriends));
            final int freeSlots = Math.max(0, maxFriends - totalFriends);
            replacements.put("%friends_free_slots%", String.valueOf(freeSlots));
        }

        if (settings.getAcceptRequests() == AcceptMode.NONE) {
            replacements.put("%friend_request_status%", "Demandes désactivées");
        } else if (requestsSent > 0) {
            replacements.put("%friend_request_status%", requestsSent + " en attente");
        } else {
            replacements.put("%friend_request_status%", "Ouvert");
        }

        return replaceAll(text, replacements);
    }

    private String applyGroupPlaceholders(final Player player, final String text) {
        final Map<String, String> replacements = new HashMap<>();
        replacements.put("%group_name%", "Aucun");
        replacements.put("%group_leader%", "Aucun");
        replacements.put("%group_members%", "0");
        replacements.put("%group_max%", "0");
        replacements.put("%group_gamemode%", "Libre");
        replacements.put("%group_status%", "Aucun");
        replacements.put("%group_slots_free%", "0");
        replacements.put("%group_auto_accept%", "Désactivé");
        replacements.put("%group_preferred_mode%", "Automatique");
        replacements.put("%group_visibility%", "Public");
        replacements.put("%group_role%", "Aucun");
        replacements.put("%group_invitations%", "0");
        replacements.put("%group_invites_sent%", "0");
        replacements.put("%groups_open%", "0");
        replacements.put("%groups_friends%", "0");
        replacements.put("%friends_online_count%", "0");
        replacements.put("%queue_available%", "0");
        replacements.put("%queue_wait_time%", "N/A");
        replacements.put("%queue_players%", "0");

        final GroupManager groupManager = plugin.getGroupManager();
        final FriendManager friendManager = plugin.getFriendManager();

        if (player != null && friendManager != null) {
            final List<FriendInfo> friends = friendManager.getFriendsList(player.getUniqueId());
            final long online = friends.stream().filter(FriendInfo::isOnline).count();
            replacements.put("%friends_online_count%", String.valueOf(online));
            if (groupManager != null) {
                final long friendsInGroups = friends.stream()
                        .map(FriendInfo::getUuid)
                        .map(groupManager::getPlayerGroup)
                        .filter(Objects::nonNull)
                        .count();
                replacements.put("%groups_friends%", String.valueOf(friendsInGroups));
            }
        }

        if (player == null || groupManager == null) {
            return replaceAll(text, replacements);
        }

        final GroupSettings groupSettings = groupManager.getGroupSettings(player.getUniqueId());
        replacements.put("%group_auto_accept%", groupSettings.isAutoAcceptInvites() ? "Automatique" : "Manuel");
        replacements.put("%group_visibility%", formatGroupVisibility(groupSettings.getVisibility()));

        final Group group = groupManager.getPlayerGroup(player.getUniqueId());
        if (group != null) {
            replacements.put("%group_name%", Objects.requireNonNullElse(group.getDisplayName(), "Groupe"));
            replacements.put("%group_leader%", resolveName(group.getLeaderUUID()));
            replacements.put("%group_members%", String.valueOf(group.getSize()));
            replacements.put("%group_max%", String.valueOf(group.getMaxSize()));
            replacements.put("%group_slots_free%", String.valueOf(Math.max(0, group.getMaxSize() - group.getSize())));
            replacements.put("%group_status%", group.isFull() ? "Complet" : "Ouvert");
            replacements.put("%group_preferred_mode%", "Toutes les files");
            String role = "Membre";
            if (group.isLeader(player.getUniqueId())) {
                role = "Leader";
            } else if (group.isModerator(player.getUniqueId())) {
                role = "Modérateur";
            }
            replacements.put("%group_role%", role);
        }

        replacements.put("%group_invitations%", String.valueOf(groupManager.countPendingInvitations(player.getUniqueId())));
        replacements.put("%group_invites_sent%", String.valueOf(groupManager.countSentInvitations(player.getUniqueId())));
        replacements.put("%groups_open%", String.valueOf(groupManager.countCachedOpenGroups()));

        return replaceAll(text, replacements);
    }

    private String applyClanPlaceholders(final Player player, final String text) {
        final Map<String, String> replacements = new HashMap<>();
        replacements.put("%clan_name%", "Aucun");
        replacements.put("%clan_tag%", "");
        replacements.put("%clan_rank%", "Aucun");
        replacements.put("%clan_members%", "0");
        replacements.put("%clan_max%", "0");
        replacements.put("%clan_points%", "0");
        replacements.put("%clan_level%", "0");
        replacements.put("%clan_online%", "0");
        replacements.put("%clan_last_activity%", "Aucune activité");
        replacements.put("%clan_permissions%", "Standard");
        replacements.put("%clan_player_level%", "Membre");
        replacements.put("%clan_available_perms%", String.valueOf(ClanPermission.values().length));
        replacements.put("%clan_ranks_count%", "0");
        replacements.put("%clan_coins%", "0");
        replacements.put("%clan_contributions%", "0");
        replacements.put("%clan_vault_access%", "Réservé");
        replacements.put("%clan_war_status%", "Inactif");
        replacements.put("%clan_war_wins%", "0");
        replacements.put("%clan_war_losses%", "0");
        replacements.put("%clan_war_ratio%", formatRatio(0, 0));
        replacements.put("%clan_status%", "Sans clan");
        replacements.put("%clans_open_count%", "0");
        replacements.put("%clans_invite_count%", "0");
        replacements.put("%clan_target_uuid%", "");
        replacements.put("%clan_target_name%", "Aucun membre");
        replacements.put("%clan_target_rank%", "Aucun");
        replacements.put("%clan_target_permissions%", "Aucune");
        replacements.put("%clan_permission_invite_status%", "§cDésactivée");
        replacements.put("%clan_permission_kick_status%", "§cDésactivée");
        replacements.put("%clan_permission_promote_status%", "§cDésactivée");
        replacements.put("%clan_permission_demote_status%", "§cDésactivée");
        replacements.put("%clan_permission_manage_ranks_status%", "§cDésactivée");
        replacements.put("%clan_permission_withdraw_status%", "§cDésactivée");
        replacements.put("%clan_permission_disband_status%", "§cDésactivée");

        final ClanManager clanManager = plugin.getClanManager();
        if (player == null || clanManager == null) {
            return replaceAll(text, replacements);
        }

        replacements.put("%clans_open_count%", String.valueOf(clanManager.countCachedOpenClans()));
        replacements.put("%clans_invite_count%", String.valueOf(clanManager.countPendingInvitations(player.getUniqueId())));

        final Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            return replaceAll(text, replacements);
        }

        replacements.put("%clan_name%", clan.getName());
        replacements.put("%clan_tag%", clan.getTag());
        replacements.put("%clan_members%", String.valueOf(clan.getMembers().size()));
        replacements.put("%clan_max%", String.valueOf(clan.getMaxMembers()));
        replacements.put("%clan_points%", String.valueOf(clan.getPoints()));
        replacements.put("%clan_level%", String.valueOf(clan.getLevel()));
        replacements.put("%clan_ranks_count%", String.valueOf(clan.getRanks().size()));
        replacements.put("%clan_coins%", String.valueOf(clan.getBankCoins()));

        final long onlineCount = clan.getMembers().keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .count();
        replacements.put("%clan_online%", String.valueOf(onlineCount));

        final ClanMember member = clan.getMember(player.getUniqueId());
        if (member != null) {
            replacements.put("%clan_rank%", member.getRankName());
            replacements.put("%clan_player_level%", member.getRankName());
            replacements.put("%clan_contributions%", String.valueOf(member.getTotalContributions()));
            replacements.put("%clan_last_activity%", formatRelativeTime(member.getJoinedAt()));
            replacements.put("%clan_status%", member.getRankName());
        } else {
            replacements.put("%clan_status%", "Membre");
        }

        final ClanRank rank = member != null ? clan.getRank(member.getRankName()) : null;
        if (rank != null) {
            final String permissions = rank.getPermissions().isEmpty()
                    ? "Standard"
                    : rank.getPermissions().stream()
                    .map(this::formatPermissionName)
                    .collect(Collectors.joining(", "));
            replacements.put("%clan_permissions%", permissions);
            replacements.put("%clan_available_perms%", String.valueOf(Math.max(0,
                    ClanPermission.values().length - rank.getPermissions().size())));
            replacements.put("%clan_player_level%", rank.getDisplayName());
        }

        final boolean vaultAccess = clan.hasPermission(player.getUniqueId(), ClanPermission.MANAGE_BANK);
        replacements.put("%clan_vault_access%", vaultAccess ? "Autorisé" : "Réservé");

        final UUID targetUuid = clanPermissionTargets.get(player.getUniqueId());
        if (targetUuid != null) {
            final ClanMember target = clan.getMember(targetUuid);
            if (target != null) {
                final String targetName = resolveName(targetUuid);
                replacements.put("%clan_target_uuid%", targetUuid.toString());
                replacements.put("%clan_target_name%", targetName);
                replacements.put("%clan_target_rank%", target.getRankName());

                final ClanRank targetRank = clan.getRank(target.getRankName());
                replacements.put("%clan_target_rank_display%", targetRank != null
                        ? targetRank.getDisplayName()
                        : target.getRankName());
                replacements.put("%clan_target_rank_priority%", String.valueOf(targetRank != null
                        ? targetRank.getPriority()
                        : 0));
                replacements.put("%clan_target_join_date%", formatJoinDate(target.getJoinedAt()));
                replacements.put("%clan_target_last_seen%", formatLastSeen(targetUuid));
                replacements.put("%clan_target_contribution%", String.valueOf(target.getTotalContributions()));

                final List<String> permissionList = clanManager.getMemberPermissions(targetUuid);
                replacements.put("%clan_target_permissions%", permissionList.isEmpty()
                        ? "Aucune"
                        : String.join(", ", permissionList));
                replacements.put("%clan_target_permissions_count%", String.valueOf(permissionList.size()));

                final int rankPriority = targetRank != null ? targetRank.getPriority() : 0;
                final ClanRank nextRank = targetRank != null ? clanManager.getNextRank(clan.getId(), rankPriority) : null;
                final ClanRank previousRank = targetRank != null ? clanManager.getPreviousRank(clan.getId(), rankPriority) : null;
                replacements.put("%clan_target_next_rank%", nextRank != null ? nextRank.getDisplayName() : "Aucun");
                replacements.put("%clan_target_previous_rank%", previousRank != null ? previousRank.getDisplayName() : "Aucun");

                replacements.put("%clan_permission_invite_status%", formatPermissionStatus(
                        clan.hasPermission(targetUuid, ClanPermission.INVITE)));
                replacements.put("%clan_permission_kick_status%", formatPermissionStatus(
                        clan.hasPermission(targetUuid, ClanPermission.KICK)));
                replacements.put("%clan_permission_promote_status%", formatPermissionStatus(
                        clan.hasPermission(targetUuid, ClanPermission.PROMOTE)));
                replacements.put("%clan_permission_demote_status%", formatPermissionStatus(
                        clan.hasPermission(targetUuid, ClanPermission.DEMOTE)));
                replacements.put("%clan_permission_manage_ranks_status%", formatPermissionStatus(
                        clan.hasPermission(targetUuid, ClanPermission.MANAGE_RANKS)));
                replacements.put("%clan_permission_withdraw_status%", formatPermissionStatus(
                        clan.hasPermission(targetUuid, ClanPermission.MANAGE_BANK)));
                replacements.put("%clan_permission_disband_status%", formatPermissionStatus(
                        clan.hasPermission(targetUuid, ClanPermission.DISBAND)));

                final boolean isSelf = player.getUniqueId().equals(targetUuid);
                final boolean targetIsLeader = clan.isLeader(targetUuid);
                final boolean hasNextRank = nextRank != null;
                final boolean hasPreviousRank = previousRank != null;

                final boolean canPromoteTarget = !isSelf && hasNextRank && !targetIsLeader
                        && clanManager.hasPermission(clan.getId(), player.getUniqueId(), "clan.promote");
                final boolean canDemoteTarget = !isSelf && hasPreviousRank && !targetIsLeader
                        && clanManager.hasPermission(clan.getId(), player.getUniqueId(), "clan.demote");
                final boolean canKickTarget = !isSelf && !targetIsLeader
                        && clanManager.hasPermission(clan.getId(), player.getUniqueId(), "clan.kick");
                final boolean canBanTarget = !isSelf && !targetIsLeader
                        && (clan.isLeader(player.getUniqueId())
                        || clan.hasPermission(player.getUniqueId(), ClanPermission.DISBAND));
                final boolean canTransfer = clan.isLeader(player.getUniqueId()) && !isSelf && !targetIsLeader;

                replacements.put("%clan_target_can_promote%", formatActionStatus(canPromoteTarget));
                replacements.put("%clan_target_can_demote%", formatActionStatus(canDemoteTarget));
                replacements.put("%clan_target_can_kick%", formatActionStatus(canKickTarget));
                replacements.put("%clan_target_can_ban%", formatActionStatus(canBanTarget));
                replacements.put("%clan_target_can_transfer%", formatActionStatus(canTransfer));
            }
        }

        return replaceAll(text, replacements);
    }

    public void setClanPermissionTarget(final UUID viewer, final UUID target) {
        if (viewer == null) {
            return;
        }
        if (target == null) {
            clanPermissionTargets.remove(viewer);
        } else {
            clanPermissionTargets.put(viewer, target);
        }
    }

    public UUID getClanPermissionTarget(final UUID viewer) {
        if (viewer == null) {
            return null;
        }
        return clanPermissionTargets.get(viewer);
    }

    public void clearClanPermissionTarget(final UUID viewer) {
        if (viewer != null) {
            clanPermissionTargets.remove(viewer);
        }
    }

    private String applyServerPlaceholders(final String text) {
        final VelocityManager velocityManager = plugin.getVelocityManager();
        if (velocityManager == null || !velocityManager.isEnabled()) {
            return text;
        }
        String replaced = text;
        replaced = replaced.replace("%server_online_bedwars%",
                String.valueOf(velocityManager.getServerPlayerCount("bedwars")));
        replaced = replaced.replace("%server_online_nexus%",
                String.valueOf(velocityManager.getServerPlayerCount("nexus")));
        replaced = replaced.replace("%server_online_zombie%",
                String.valueOf(velocityManager.getServerPlayerCount("zombie")));
        replaced = replaced.replace("%server_online_custom%",
                String.valueOf(velocityManager.getServerPlayerCount("custom")));
        return replaced;
    }

    private String replaceAll(final String text, final Map<String, String> replacements) {
        if (text == null || text.isEmpty() || replacements.isEmpty()) {
            return text;
        }
        String replaced = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            replaced = replaced.replace(entry.getKey(), entry.getValue());
        }
        return replaced;
    }

    private String formatRelativeTime(final long timestamp) {
        if (timestamp <= 0L) {
            return "Inconnu";
        }
        Duration duration = Duration.between(Instant.ofEpochMilli(timestamp), Instant.now());
        if (duration.isNegative()) {
            duration = Duration.ZERO;
        }
        final long days = duration.toDays();
        if (days > 0) {
            return "il y a " + days + "j";
        }
        final long hours = duration.toHours();
        if (hours > 0) {
            return "il y a " + hours + "h";
        }
        final long minutes = duration.toMinutes();
        if (minutes > 0) {
            return "il y a " + minutes + "m";
        }
        final long seconds = duration.getSeconds();
        if (seconds <= 5) {
            return "à l'instant";
        }
        return "il y a " + seconds + "s";
    }

    private String formatAcceptMode(final AcceptMode mode) {
        if (mode == null) {
            return "Tous";
        }
        return switch (mode) {
            case ALL -> "Tous";
            case FRIENDS_OF_FRIENDS -> "Amis d'amis";
            case NONE -> "Personne";
        };
    }

    private String formatDetailedAcceptMode(final AcceptMode mode) {
        if (mode == null) {
            return "Tous les joueurs";
        }
        return switch (mode) {
            case ALL -> "Tous les joueurs";
            case FRIENDS_OF_FRIENDS -> "Amis d'amis seulement";
            case NONE -> "Personne";
        };
    }

    private String formatVisibilityMode(final boolean showOnlineStatus) {
        return showOnlineStatus ? "Visible par tous" : "Invisible";
    }

    private String formatPermissionName(final ClanPermission permission) {
        final String formatted = permission.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
    }

    private String formatGroupVisibility(final GroupVisibility visibility) {
        if (visibility == null) {
            return "Public";
        }
        return switch (visibility) {
            case PUBLIC -> "Public";
            case FRIENDS_ONLY -> "Amis uniquement";
            case INVITE_ONLY -> "Sur invitation";
        };
    }

    private String formatRatio(final int wins, final int losses) {
        if (wins <= 0 && losses <= 0) {
            return "0.0";
        }
        if (losses <= 0) {
            return String.format(Locale.US, "%.2f", (double) wins);
        }
        return String.format(Locale.US, "%.2f", (double) wins / Math.max(1, losses));
    }

    private String formatPermissionStatus(final boolean enabled) {
        return enabled ? "§aActivée" : "§cDésactivée";
    }

    private String formatActionStatus(final boolean available) {
        return available ? "§aDisponible" : "§cIndisponible";
    }

    private String formatJoinDate(final long timestamp) {
        if (timestamp <= 0L) {
            return "Inconnue";
        }
        final Instant instant = Instant.ofEpochMilli(timestamp);
        return DATE_TIME_FORMATTER.format(instant.atZone(ZoneId.systemDefault()));
    }

    private String formatLastSeen(final UUID uuid) {
        if (uuid == null) {
            return "Inconnue";
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.isOnline()) {
            return "En ligne";
        }
        final long lastSeen = offlinePlayer.getLastSeen();
        if (lastSeen <= 0L) {
            return "Inconnue";
        }
        return formatRelativeTime(lastSeen);
    }

    private String resolveName(final UUID uuid) {
        if (uuid == null) {
            return "Inconnu";
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        final String name = offlinePlayer.getName();
        return name != null ? name : uuid.toString();
    }
}
