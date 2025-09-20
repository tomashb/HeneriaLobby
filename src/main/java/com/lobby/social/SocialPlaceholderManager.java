package com.lobby.social;

import com.lobby.LobbyPlugin;
import com.lobby.economy.EconomyManager;
import com.lobby.social.clans.Clan;
import com.lobby.social.clans.ClanManager;
import com.lobby.social.clans.ClanMember;
import com.lobby.social.friends.FriendInfo;
import com.lobby.social.friends.FriendManager;
import com.lobby.social.groups.Group;
import com.lobby.social.groups.GroupManager;
import com.lobby.velocity.VelocityManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SocialPlaceholderManager {

    private final LobbyPlugin plugin;

    public SocialPlaceholderManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public String replacePlaceholders(final Player player, final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String replaced = text;
        replaced = applyEconomyPlaceholders(player, replaced);
        replaced = applyFriendPlaceholders(player, replaced);
        replaced = applyGroupPlaceholders(player, replaced);
        replaced = applyClanPlaceholders(player, replaced);
        replaced = applyServerPlaceholders(replaced);
        return replaced;
    }

    private String applyEconomyPlaceholders(final Player player, final String text) {
        if (player == null) {
            return text;
        }
        final EconomyManager economyManager = plugin.getEconomyManager();
        if (economyManager == null) {
            return text;
        }
        String replaced = text;
        replaced = replaced.replace("%player_coins%", String.valueOf(economyManager.getCoins(player.getUniqueId())));
        replaced = replaced.replace("%player_tokens%", String.valueOf(economyManager.getTokens(player.getUniqueId())));
        return replaced;
    }

    private String applyFriendPlaceholders(final Player player, final String text) {
        if (player == null) {
            return text;
        }
        final FriendManager friendManager = plugin.getFriendManager();
        if (friendManager == null) {
            return text;
        }
        final List<FriendInfo> friends = friendManager.getFriendsList(player.getUniqueId());
        final long online = friends.stream().filter(FriendInfo::isOnline).count();
        final int requests = friendManager.getPendingRequests(player.getUniqueId()).size();
        String replaced = text;
        replaced = replaced.replace("%friends_total%", String.valueOf(friends.size()));
        replaced = replaced.replace("%friends_online%", String.valueOf(online));
        replaced = replaced.replace("%friend_requests%", String.valueOf(requests));
        return replaced;
    }

    private String applyGroupPlaceholders(final Player player, final String text) {
        if (player == null) {
            return text;
        }
        final GroupManager groupManager = plugin.getGroupManager();
        if (groupManager == null) {
            return text;
        }
        final Group group = groupManager.getPlayerGroup(player.getUniqueId());
        if (group == null) {
            return text.replace("%group_name%", "Aucun")
                    .replace("%group_members%", "0")
                    .replace("%group_max%", "0")
                    .replace("%group_leader%", "Aucun");
        }
        final String leaderName = resolveName(group.getLeaderUUID());
        final int memberCount = group.getMembers().size();
        final int max = group.getMaxSize();
        String replaced = text.replace("%group_name%", Objects.requireNonNullElse(group.getDisplayName(), "Aucun"))
                .replace("%group_members%", String.valueOf(memberCount))
                .replace("%group_max%", String.valueOf(max))
                .replace("%group_leader%", leaderName);
        return replaced;
    }

    private String applyClanPlaceholders(final Player player, final String text) {
        if (player == null) {
            return text;
        }
        final ClanManager clanManager = plugin.getClanManager();
        if (clanManager == null) {
            return text;
        }
        final Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            return text.replace("%clan_name%", "Aucun")
                    .replace("%clan_tag%", "")
                    .replace("%clan_rank%", "Aucun")
                    .replace("%clan_members%", "0")
                    .replace("%clan_max%", "0")
                    .replace("%clan_points%", "0")
                    .replace("%clan_level%", "0");
        }
        final ClanMember member = clan.getMember(player.getUniqueId());
        String replaced = text;
        replaced = replaced.replace("%clan_name%", clan.getName());
        replaced = replaced.replace("%clan_tag%", clan.getTag());
        replaced = replaced.replace("%clan_rank%", member != null ? member.getRankName() : "Membre");
        replaced = replaced.replace("%clan_members%", String.valueOf(clan.getMembers().size()));
        replaced = replaced.replace("%clan_max%", String.valueOf(clan.getMaxMembers()));
        replaced = replaced.replace("%clan_points%", String.valueOf(clan.getPoints()));
        replaced = replaced.replace("%clan_level%", String.valueOf(clan.getLevel()));
        return replaced;
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

    private String resolveName(final UUID uuid) {
        if (uuid == null) {
            return "Inconnu";
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        final String name = offlinePlayer.getName();
        return name != null ? name : uuid.toString();
    }
}
