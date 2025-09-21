package com.lobby.social.clans;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ClanManager {

    private final LobbyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;
    private final Map<String, Clan> clanCache = new HashMap<>();
    private final Map<Integer, Clan> clanCacheById = new HashMap<>();
    private final Map<UUID, String> playerClanCache = new HashMap<>();
    private final Map<Integer, Map<UUID, ClanBanEntry>> clanBanCache = new ConcurrentHashMap<>();

    public ClanManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();
    }

    public void reload() {
        clanCache.clear();
        clanCacheById.clear();
        playerClanCache.clear();
        clanBanCache.clear();
    }

    public void createClan(final Player leader, final String name, final String tag) {
        if (hasPlayerClan(leader.getUniqueId())) {
            leader.sendMessage("§cVous êtes déjà dans un clan !");
            return;
        }
        if (name.length() < 3 || name.length() > 20) {
            leader.sendMessage("§cLe nom du clan doit faire entre 3 et 20 caractères !");
            return;
        }
        if (tag.length() < 2 || tag.length() > 6) {
            leader.sendMessage("§cLe tag du clan doit faire entre 2 et 6 caractères !");
            return;
        }
        if (clanExists(name)) {
            leader.sendMessage("§cCe nom de clan existe déjà !");
            return;
        }
        if (tagExists(tag)) {
            leader.sendMessage("§cCe tag de clan existe déjà !");
            return;
        }
        final long cost = 10_000L;
        if (!economyManager.hasCoins(leader.getUniqueId(), cost)) {
            leader.sendMessage("§cVous n'avez pas assez de coins ! (Requis: " + cost + ")");
            return;
        }
        economyManager.removeCoins(leader.getUniqueId(), cost, "Clan creation");
        final Clan clan = new Clan(name, tag, leader.getUniqueId());
        final int clanId = saveClanToDatabase(clan);
        if (clanId <= 0) {
            leader.sendMessage("§cImpossible de créer le clan pour le moment.");
            return;
        }
        clan.setId(clanId);
        setupDefaultRanks(clan);
        saveLeaderMember(clan, leader.getUniqueId());
        cacheClan(clan);
        leader.sendMessage("§aClan §6[" + tag + "] " + name + " §acréé avec succès !");
        leader.sendMessage("§7Vous êtes maintenant le leader du clan.");
    }

    public boolean inviteToClan(final Player inviter, final String targetName, final String message) {
        final Clan clan = getPlayerClan(inviter.getUniqueId());
        if (clan == null) {
            inviter.sendMessage("§cVous n'êtes dans aucun clan !");
            return false;
        }
        if (!clan.hasPermission(inviter.getUniqueId(), ClanPermission.INVITE_MEMBERS)) {
            inviter.sendMessage("§cVous n'avez pas la permission d'inviter des joueurs !");
            return false;
        }
        UUID targetUUID = null;
        Player targetPlayer = Bukkit.getPlayerExact(targetName);
        if (targetPlayer != null) {
            targetUUID = targetPlayer.getUniqueId();
        } else {
            targetUUID = getUUIDFromName(targetName);
        }
        if (targetUUID == null) {
            inviter.sendMessage("§cJoueur introuvable.");
            return false;
        }
        if (hasPlayerClan(targetUUID)) {
            inviter.sendMessage("§c" + targetName + " est déjà dans un clan !");
            return false;
        }
        if (isPlayerBanned(clan.getId(), targetUUID)) {
            inviter.sendMessage("§cCe joueur est banni du clan.");
            return false;
        }
        final ClanInvitation invitation = saveInvitation(clan.getId(), inviter.getUniqueId(), targetUUID, message);
        inviter.sendMessage("§aInvitation envoyée à §6" + targetName + "§a !");
        if (targetPlayer != null) {
            targetPlayer.sendMessage("§e" + inviter.getName() + " §avous a invité à rejoindre le clan §6[" + clan.getTag() + "] " + clan.getName() + "§a !");
            if (message != null && !message.isEmpty()) {
                targetPlayer.sendMessage("§7Message: §f" + message);
            }
            targetPlayer.sendMessage("§7Tapez §a/clan accept " + clan.getName() + " §7pour accepter");
            targetPlayer.playSound(targetPlayer.getLocation(), Sound.UI_TOAST_IN, 1.0f, 1.0f);
        }
        scheduleInvitationExpiration(invitation);
        return true;
    }

    public void acceptInvitation(final Player player, final String clanName) {
        if (hasPlayerClan(player.getUniqueId())) {
            player.sendMessage("§cVous êtes déjà dans un clan !");
            return;
        }
        final Clan clan = getClanByName(clanName);
        if (clan == null) {
            player.sendMessage("§cCe clan n'existe pas.");
            return;
        }
        final ClanInvitation invitation = getPendingInvitation(clan.getId(), player.getUniqueId());
        if (invitation == null) {
            player.sendMessage("§cVous n'avez pas d'invitation pour ce clan.");
            return;
        }
        if (clan.isFull()) {
            player.sendMessage("§cLe clan est complet.");
            return;
        }
        if (isPlayerBanned(clan.getId(), player.getUniqueId())) {
            player.sendMessage("§cVous êtes banni de ce clan.");
            return;
        }
        final String memberRankName = resolveRankName(clan, ClanRole.MEMBER);
        saveMember(clan.getId(), player.getUniqueId(), memberRankName);
        clan.addMember(new ClanMember(player.getUniqueId(), memberRankName, System.currentTimeMillis(), 0L));
        playerClanCache.put(player.getUniqueId(), clan.getName().toLowerCase(Locale.ROOT));
        updateInvitationStatus(invitation.getId(), "ACCEPTED");
        broadcastClanMessage(clan, "§a" + player.getName() + " a rejoint le clan !");
    }

    public void denyInvitation(final Player player, final String clanName) {
        final Clan clan = getClanByName(clanName);
        if (clan == null) {
            player.sendMessage("§cCe clan n'existe pas.");
            return;
        }
        final ClanInvitation invitation = getPendingInvitation(clan.getId(), player.getUniqueId());
        if (invitation == null) {
            player.sendMessage("§cVous n'avez pas d'invitation pour ce clan.");
            return;
        }
        updateInvitationStatus(invitation.getId(), "DECLINED");
        player.sendMessage("§cInvitation refusée.");
    }

    public Clan getPlayerClan(final UUID uuid) {
        final String cachedName = playerClanCache.get(uuid);
        if (cachedName != null) {
            return clanCache.get(cachedName);
        }
        final Clan clan = loadClanForPlayer(uuid);
        if (clan != null) {
            cacheClan(clan);
        }
        return clan;
    }

    public boolean hasPlayerClan(final UUID uuid) {
        if (playerClanCache.containsKey(uuid)) {
            return true;
        }
        return loadClanForPlayer(uuid) != null;
    }

    public int countPendingInvitations(final UUID playerUUID) {
        final String query = "SELECT COUNT(*) FROM clan_invitations WHERE invited_uuid = ? AND status = 'PENDING'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to count clan invitations for " + playerUUID, exception);
        }
        return 0;
    }

    public int countCachedOpenClans() {
        return (int) clanCache.values().stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(clan -> !clan.isFull())
                .count();
    }

    public Clan getClanById(final int clanId) {
        if (clanId <= 0) {
            return null;
        }
        final Clan cached = clanCacheById.get(clanId);
        if (cached != null) {
            return cached;
        }
        final Clan clan = loadClanById(clanId);
        if (clan != null) {
            cacheClan(clan);
        }
        return clan;
    }

    public List<ClanMember> getClanMembers(final int clanId) {
        final Clan clan = getClanById(clanId);
        if (clan == null) {
            return List.of();
        }
        return new ArrayList<>(clan.getMembers().values());
    }

    public boolean toggleMemberPermission(final UUID managerUuid, final UUID memberUuid, final String permissionKey) {
        if (managerUuid == null || memberUuid == null || permissionKey == null || permissionKey.isBlank()) {
            return false;
        }
        final Clan clan = getPlayerClan(managerUuid);
        final Clan targetClan = getPlayerClan(memberUuid);
        if (clan == null || targetClan == null || clan.getId() != targetClan.getId()) {
            return false;
        }
        if (!clan.hasPermission(managerUuid, ClanPermission.MANAGE_PERMISSIONS)) {
            return false;
        }
        if (clan.isLeader(memberUuid)) {
            return false;
        }
        final ClanMember member = clan.getMember(memberUuid);
        if (member == null) {
            return false;
        }
        final ClanPermission permission = resolvePermission(permissionKey);
        if (permission == null) {
            return false;
        }
        final boolean hadPermission = member.getPermissions().contains(permission);
        member.togglePermission(permission);
        final Set<ClanPermission> updated = member.getPermissions().isEmpty()
                ? EnumSet.noneOf(ClanPermission.class)
                : EnumSet.copyOf(member.getPermissions());
        final boolean stored = storeMemberPermissions(clan.getId(), memberUuid, updated);
        if (!stored) {
            if (member.getPermissions().contains(permission) != hadPermission) {
                member.togglePermission(permission);
            }
            return hadPermission;
        }
        return member.getPermissions().contains(permission);
    }

    public boolean applyPermissionPreset(final UUID managerUuid, final UUID memberUuid, final String presetKey) {
        if (managerUuid == null || memberUuid == null || presetKey == null || presetKey.isBlank()) {
            return false;
        }
        final Clan clan = getPlayerClan(managerUuid);
        final Clan targetClan = getPlayerClan(memberUuid);
        if (clan == null || targetClan == null || clan.getId() != targetClan.getId()) {
            return false;
        }
        if (!clan.hasPermission(managerUuid, ClanPermission.MANAGE_PERMISSIONS)) {
            return false;
        }
        if (clan.isLeader(memberUuid)) {
            return false;
        }
        final ClanMember member = clan.getMember(memberUuid);
        if (member == null) {
            return false;
        }
        final Set<ClanPermission> preset = resolvePreset(presetKey);
        member.setPermissions(preset);
        return storeMemberPermissions(clan.getId(), memberUuid, preset);
    }

    public List<String> getMemberPermissions(final UUID memberUuid) {
        if (memberUuid == null) {
            return List.of();
        }
        final Clan clan = getPlayerClan(memberUuid);
        if (clan == null) {
            return List.of();
        }
        final ClanMember member = clan.getMember(memberUuid);
        if (member == null) {
            return List.of();
        }
        final Set<ClanPermission> permissions;
        if (member.getPermissions() == null || member.getPermissions().isEmpty()) {
            final ClanRank rank = getPlayerRank(clan, memberUuid);
            if (rank == null) {
                permissions = EnumSet.noneOf(ClanPermission.class);
            } else {
                final Set<ClanPermission> rankPermissions = rank.getPermissions();
                permissions = (rankPermissions == null || rankPermissions.isEmpty())
                        ? EnumSet.noneOf(ClanPermission.class)
                        : EnumSet.copyOf(rankPermissions);
            }
        } else {
            permissions = EnumSet.copyOf(member.getPermissions());
        }
        return permissions.stream()
                .map(permission -> permission.name().toLowerCase(Locale.ROOT).replace('_', ' '))
                .map(value -> Character.toUpperCase(value.charAt(0)) + value.substring(1))
                .toList();
    }

    public boolean hasPermission(final int clanId, final UUID playerUuid, final String permissionKey) {
        final Clan clan = getClanById(clanId);
        if (clan == null || playerUuid == null || permissionKey == null || permissionKey.isBlank()) {
            return false;
        }
        final ClanPermission permission = resolvePermission(permissionKey);
        if (permission == null) {
            return false;
        }
        return clan.hasPermission(playerUuid, permission);
    }

    public ClanRank getPlayerRank(final UUID playerUuid) {
        final Clan clan = getPlayerClan(playerUuid);
        return getPlayerRank(clan, playerUuid);
    }

    public ClanRank getRank(final int clanId, final String rankName) {
        if (rankName == null || rankName.isBlank()) {
            return null;
        }
        final Clan clan = getClanById(clanId);
        if (clan == null) {
            return null;
        }
        return clan.getRank(rankName);
    }

    public ClanRank getNextRank(final int clanId, final int currentPriority) {
        final Clan clan = getClanById(clanId);
        if (clan == null) {
            return null;
        }
        return getNextRank(clan, currentPriority);
    }

    public ClanRank getPreviousRank(final int clanId, final int currentPriority) {
        final Clan clan = getClanById(clanId);
        if (clan == null) {
            return null;
        }
        return getPreviousRank(clan, currentPriority);
    }

    public boolean promoteMember(final UUID promoter, final UUID target) {
        if (promoter == null || target == null || promoter.equals(target)) {
            return false;
        }
        final Clan clan = getPlayerClan(promoter);
        final Clan targetClan = getPlayerClan(target);
        if (clan == null || targetClan == null || clan.getId() != targetClan.getId()) {
            return false;
        }
        if (!clan.hasPermission(promoter, ClanPermission.PROMOTE_MEMBERS)) {
            return false;
        }
        if (clan.isLeader(target)) {
            return false;
        }

        final ClanRank currentRank = getPlayerRank(clan, target);
        if (currentRank == null) {
            return false;
        }
        final ClanRank nextRank = getNextRank(clan, currentRank.getPriority());
        if (nextRank == null) {
            return false;
        }

        final ClanRank promoterRank = getPlayerRank(clan, promoter);
        final ClanRole promoterRole = getMemberRole(clan, promoter);
        final ClanRole targetRole = getMemberRole(clan, target);
        ClanRole nextRole = ClanRole.fromName(nextRank.getName());
        if (nextRole == null) {
            nextRole = ClanRole.fromName(nextRank.getDisplayName());
        }

        if (promoterRole != null && promoterRole != ClanRole.LEADER) {
            if (targetRole != null && targetRole.getLevel() >= promoterRole.getLevel()) {
                return false;
            }
            if (nextRole != null && nextRole.getLevel() >= promoterRole.getLevel()) {
                return false;
            }
        } else if (!clan.isLeader(promoter) && promoterRank != null
                && nextRank.getPriority() >= promoterRank.getPriority()) {
            return false;
        }

        if (setPlayerRank(clan, target, nextRank)) {
            final String targetName = getNameByUuid(target);
            broadcastRankChangeMessage(clan, targetName != null ? targetName : target.toString(),
                    nextRank.getDisplayName(), true);
            return true;
        }
        return false;
    }

    public boolean demoteMember(final UUID demoter, final UUID target) {
        if (demoter == null || target == null || demoter.equals(target)) {
            return false;
        }
        final Clan clan = getPlayerClan(demoter);
        final Clan targetClan = getPlayerClan(target);
        if (clan == null || targetClan == null || clan.getId() != targetClan.getId()) {
            return false;
        }
        if (!clan.hasPermission(demoter, ClanPermission.DEMOTE_MEMBERS)) {
            return false;
        }
        if (clan.isLeader(target)) {
            return false;
        }

        final ClanRank currentRank = getPlayerRank(clan, target);
        if (currentRank == null) {
            return false;
        }
        final ClanRank previousRank = getPreviousRank(clan, currentRank.getPriority());
        if (previousRank == null) {
            return false;
        }

        final ClanRank demoterRank = getPlayerRank(clan, demoter);
        final ClanRole demoterRole = getMemberRole(clan, demoter);
        final ClanRole targetRole = getMemberRole(clan, target);
        ClanRole previousRole = ClanRole.fromName(previousRank.getName());
        if (previousRole == null) {
            previousRole = ClanRole.fromName(previousRank.getDisplayName());
        }

        if (demoterRole != null && demoterRole != ClanRole.LEADER) {
            if (targetRole != null && targetRole.getLevel() >= demoterRole.getLevel()) {
                return false;
            }
            if (previousRole != null && previousRole.getLevel() >= demoterRole.getLevel()) {
                return false;
            }
        } else if (!clan.isLeader(demoter) && demoterRank != null
                && previousRank.getPriority() >= demoterRank.getPriority()) {
            return false;
        }

        if (setPlayerRank(clan, target, previousRank)) {
            final String targetName = getNameByUuid(target);
            broadcastRankChangeMessage(clan, targetName != null ? targetName : target.toString(),
                    previousRank.getDisplayName(), false);
            return true;
        }
        return false;
    }

    public boolean kickMember(final UUID kicker, final UUID target, final String reason) {
        if (kicker == null || target == null || kicker.equals(target)) {
            return false;
        }
        final Clan clan = getPlayerClan(kicker);
        final Clan targetClan = getPlayerClan(target);
        if (clan == null || targetClan == null || clan.getId() != targetClan.getId()) {
            return false;
        }
        if (!clan.hasPermission(kicker, ClanPermission.KICK_MEMBERS)) {
            return false;
        }
        if (clan.isLeader(target)) {
            return false;
        }

        final ClanRole kickerRole = getMemberRole(clan, kicker);
        final ClanRole targetRole = getMemberRole(clan, target);
        final ClanRank kickerRank = getPlayerRank(clan, kicker);
        final ClanRank targetRank = getPlayerRank(clan, target);

        if (kickerRole != null && targetRole != null && kickerRole != ClanRole.LEADER
                && targetRole.getLevel() >= kickerRole.getLevel()) {
            return false;
        }
        if ((kickerRole == null || targetRole == null) && kickerRank != null && targetRank != null
                && !clan.isLeader(kicker) && targetRank.getPriority() >= kickerRank.getPriority()) {
            return false;
        }

        if (!removeMemberFromClan(clan, target)) {
            return false;
        }

        final String targetName = getNameByUuid(target);
        final String finalReason = (reason != null && !reason.isBlank()) ? reason : "Expulsé";
        broadcastLeaveMessage(clan, targetName != null ? targetName : target.toString(), finalReason);

        final Player executor = Bukkit.getPlayer(kicker);
        if (executor != null) {
            executor.sendMessage("§aVous avez expulsé §6" + (targetName != null ? targetName : target)
                    + " §adu clan." + (reason != null && !reason.isBlank() ? " §7Raison: §f" + reason : ""));
        }
        final Player kicked = Bukkit.getPlayer(target);
        if (kicked != null) {
            kicked.sendMessage("§cVous avez été expulsé du clan §6" + clan.getName() + "§c." +
                    (reason != null && !reason.isBlank() ? " §7Raison: §f" + reason : ""));
            kicked.closeInventory();
        }
        return true;
    }

    public boolean banMember(final UUID banner, final UUID target, final String reason, final long durationMs) {
        if (banner == null || target == null || banner.equals(target)) {
            return false;
        }
        final Clan clan = getPlayerClan(banner);
        final Clan targetClan = getPlayerClan(target);
        if (clan == null || targetClan == null || clan.getId() != targetClan.getId()) {
            return false;
        }
        if (!clan.hasPermission(banner, ClanPermission.BAN_MEMBERS)) {
            return false;
        }
        if (clan.isLeader(target)) {
            return false;
        }

        final ClanRole bannerRole = getMemberRole(clan, banner);
        final ClanRole targetRole = getMemberRole(clan, target);
        final ClanRank bannerRank = getPlayerRank(clan, banner);
        final ClanRank targetRank = getPlayerRank(clan, target);

        if (bannerRole != null && targetRole != null && bannerRole != ClanRole.LEADER
                && targetRole.getLevel() >= bannerRole.getLevel()) {
            return false;
        }
        if ((bannerRole == null || targetRole == null) && bannerRank != null && targetRank != null
                && !clan.isLeader(banner) && targetRank.getPriority() >= bannerRank.getPriority()) {
            return false;
        }

        if (!removeMemberFromClan(clan, target)) {
            return false;
        }

        addClanBan(clan.getId(), target, reason, durationMs);
        final String targetName = getNameByUuid(target);
        final String finalReason = (reason != null && !reason.isBlank()) ? reason : "Banni";
        broadcastLeaveMessage(clan, targetName != null ? targetName : target.toString(), finalReason);

        final Player executor = Bukkit.getPlayer(banner);
        if (executor != null) {
            executor.sendMessage("§cVous avez banni §6" + (targetName != null ? targetName : target)
                    + " §cdu clan." + (reason != null && !reason.isBlank() ? " §7Raison: §f" + reason : ""));
        }
        final Player bannedPlayer = Bukkit.getPlayer(target);
        if (bannedPlayer != null) {
            final StringBuilder message = new StringBuilder("§cVous avez été banni du clan §6")
                    .append(clan.getName()).append("§c.");
            if (reason != null && !reason.isBlank()) {
                message.append(" §7Raison: §f").append(reason).append('.');
            }
            if (durationMs > 0L) {
                message.append(" §7Durée: §f").append(formatDuration(durationMs)).append('.');
            } else {
                message.append(" §7Durée: §fDéfinitive.");
            }
            bannedPlayer.sendMessage(message.toString());
            bannedPlayer.closeInventory();
        }
        return true;
    }

    @Deprecated
    public boolean promotePlayer(final UUID promoter, final UUID target) {
        return promoteMember(promoter, target);
    }

    @Deprecated
    public boolean demotePlayer(final UUID demoter, final UUID target) {
        return demoteMember(demoter, target);
    }

    @Deprecated
    public boolean kickMember(final UUID executorUuid, final UUID targetUuid) {
        return kickMember(executorUuid, targetUuid, null);
    }

    @Deprecated
    public boolean banMember(final UUID executorUuid, final UUID targetUuid) {
        return banMember(executorUuid, targetUuid, null, -1L);
    }

    public boolean transferLeadership(final UUID currentLeaderUuid, final UUID targetUuid) {
        if (currentLeaderUuid == null || targetUuid == null || currentLeaderUuid.equals(targetUuid)) {
            return false;
        }
        final Clan clan = getPlayerClan(currentLeaderUuid);
        final Clan targetClan = getPlayerClan(targetUuid);
        if (clan == null || targetClan == null || clan.getId() != targetClan.getId()) {
            return false;
        }
        if (!clan.isLeader(currentLeaderUuid)) {
            return false;
        }
        final ClanMember targetMember = clan.getMember(targetUuid);
        if (targetMember == null) {
            return false;
        }
        final ClanRank leaderRank = getRankForRole(clan, ClanRole.LEADER);
        ClanRole fallbackRole = ClanRole.CO_LEADER;
        if (getRankForRole(clan, fallbackRole) == null) {
            fallbackRole = ClanRole.MODERATOR;
            if (getRankForRole(clan, fallbackRole) == null) {
                fallbackRole = ClanRole.MEMBER;
            }
        }
        final ClanRank newLeaderRank = leaderRank != null ? leaderRank : getRankForRole(clan, ClanRole.MEMBER);
        if (newLeaderRank == null) {
            return false;
        }
        final String updateLeaderQuery = "UPDATE clans SET leader_uuid = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(updateLeaderQuery)) {
            statement.setString(1, targetUuid.toString());
            statement.setInt(2, clan.getId());
            if (statement.executeUpdate() <= 0) {
                return false;
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to transfer clan leadership for clan " + clan.getId(), exception);
            return false;
        }
        clan.setLeaderUUID(targetUuid);
        updateMemberRole(clan, targetUuid, ClanRole.LEADER);
        updateMemberRole(clan, currentLeaderUuid, fallbackRole);

        final String oldLeaderName = getNameByUuid(currentLeaderUuid);
        final String newLeaderName = getNameByUuid(targetUuid);
        broadcastClanMessage(clan, "§6" + (oldLeaderName != null ? oldLeaderName : currentLeaderUuid)
                + " §7a transféré le leadership à §e" + (newLeaderName != null ? newLeaderName : targetUuid) + "§7.");
        final Player newLeader = Bukkit.getPlayer(targetUuid);
        if (newLeader != null) {
            newLeader.sendMessage("§eVous êtes désormais le leader du clan §6" + clan.getName() + "§e !");
        }
        final Player formerLeader = Bukkit.getPlayer(currentLeaderUuid);
        if (formerLeader != null) {
            formerLeader.sendMessage("§aLeadership transféré avec succès.");
        }
        return true;
    }

    public boolean depositCoins(final Player player, final long amount) {
        if (player == null || amount <= 0) {
            return false;
        }
        final Clan clan = getPlayerClan(player.getUniqueId());
        if (clan == null) {
            return false;
        }
        if (!economyManager.hasCoins(player.getUniqueId(), amount)) {
            return false;
        }

        economyManager.removeCoins(player.getUniqueId(), amount, "Clan deposit");
        clan.deposit(amount);
        updateClanBank(clan);
        updateMemberContribution(clan, player.getUniqueId(), amount);
        logClanTransaction(clan.getId(), player.getUniqueId(), "DEPOSIT", amount, clan.getBankCoins());
        return true;
    }

    public boolean withdrawCoins(final Player player, final long amount) {
        if (player == null || amount <= 0) {
            return false;
        }
        final Clan clan = getPlayerClan(player.getUniqueId());
        if (clan == null) {
            return false;
        }
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.MANAGE_CLAN_INFO)) {
            return false;
        }
        if (clan.getBankCoins() < amount) {
            return false;
        }

        clan.withdraw(amount);
        updateClanBank(clan);
        economyManager.addCoins(player.getUniqueId(), amount, "Clan withdraw");
        logClanTransaction(clan.getId(), player.getUniqueId(), "WITHDRAW", amount, clan.getBankCoins());
        return true;
    }

    public boolean deleteClan(final UUID leaderUuid) {
        if (leaderUuid == null) {
            return false;
        }

        final Clan clan = getPlayerClan(leaderUuid);
        if (clan == null || !clan.isLeader(leaderUuid)) {
            return false;
        }

        Connection connection = null;
        try {
            connection = databaseManager.getConnection();
            connection.setAutoCommit(false);

            deleteEntries(connection, "DELETE FROM clan_members WHERE clan_id = ?", clan.getId());
            deleteEntries(connection, "DELETE FROM clan_member_permissions WHERE clan_id = ?", clan.getId());
            deleteEntries(connection, "DELETE FROM clan_ranks WHERE clan_id = ?", clan.getId());
            deleteEntries(connection, "DELETE FROM clan_invitations WHERE clan_id = ?", clan.getId());
            deleteEntries(connection, "DELETE FROM clan_transactions WHERE clan_id = ?", clan.getId());
            deleteEntries(connection, "DELETE FROM clans WHERE id = ?", clan.getId());

            connection.commit();

            removeClanFromCache(clan);
            notifyAllClanMembers(clan, "§cLe clan a été supprimé par le leader");
            return true;
        } catch (final SQLException exception) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (final SQLException rollbackException) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to rollback clan deletion", rollbackException);
                }
            }
            plugin.getLogger().log(Level.SEVERE, "Failed to delete clan", exception);
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                } catch (final SQLException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to reset auto-commit after clan deletion", exception);
                }
                try {
                    connection.close();
                } catch (final SQLException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to close connection after clan deletion", exception);
                }
            }
        }
    }

    private void deleteEntries(final Connection connection, final String query, final int clanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clanId);
            statement.executeUpdate();
        }
    }

    private void cacheClan(final Clan clan) {
        clanCache.put(clan.getName().toLowerCase(Locale.ROOT), clan);
        clanCache.put(clan.getTag().toLowerCase(Locale.ROOT), clan);
        clanCacheById.put(clan.getId(), clan);
        for (final UUID member : clan.getMembers().keySet()) {
            playerClanCache.put(member, clan.getName().toLowerCase(Locale.ROOT));
        }
    }

    private void removeClanFromCache(final Clan clan) {
        if (clan == null) {
            return;
        }
        clanCache.entrySet().removeIf(entry -> entry.getValue() == clan || (entry.getValue() != null
                && entry.getValue().getId() == clan.getId()));
        clanCacheById.remove(clan.getId());
        playerClanCache.entrySet().removeIf(entry -> entry.getValue() != null
                && entry.getValue().equalsIgnoreCase(clan.getName()));
    }

    private void updateClanBank(final Clan clan) {
        final String query = "UPDATE clans SET bank_coins = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, clan.getBankCoins());
            statement.setInt(2, clan.getId());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update clan bank", exception);
        }
    }

    private void updateMemberContribution(final Clan clan, final UUID memberUuid, final long amount) {
        if (clan == null || memberUuid == null || amount <= 0) {
            return;
        }
        final ClanMember member = clan.getMember(memberUuid);
        if (member != null) {
            member.addContribution(amount);
        }
        final String query = "UPDATE clan_members SET total_contributions = total_contributions + ? WHERE clan_id = ? AND player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, amount);
            statement.setInt(2, clan.getId());
            statement.setString(3, memberUuid.toString());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update clan member contributions", exception);
        }
    }

    private void logClanTransaction(final int clanId, final UUID playerUuid, final String type, final long amount,
                                    final long balanceAfter) {
        final String query = "INSERT INTO clan_transactions (clan_id, player_uuid, transaction_type, amount, balance_after, created_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clanId);
            statement.setString(2, playerUuid.toString());
            statement.setString(3, type);
            statement.setLong(4, amount);
            statement.setLong(5, balanceAfter);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to log clan transaction", exception);
        }
    }

    private void notifyAllClanMembers(final Clan clan, final String message) {
        if (clan == null || message == null || message.isBlank()) {
            return;
        }
        for (final ClanMember member : clan.getMembers().values()) {
            final Player player = Bukkit.getPlayer(member.getUuid());
            if (player != null) {
                player.sendMessage(message);
            }
        }
        final Player leader = Bukkit.getPlayer(clan.getLeaderUUID());
        if (leader != null && clan.getMembers().values().stream()
                .noneMatch(member -> member.getUuid().equals(leader.getUniqueId()))) {
            leader.sendMessage(message);
        }
    }

    private ClanPermission resolvePermission(final String permissionKey) {
        final String key = permissionKey.toLowerCase(Locale.ROOT);
        switch (key) {
            case "clan.invite":
                return ClanPermission.INVITE_MEMBERS;
            case "clan.manage_bank":
            case "clan.withdraw":
            case "clan.manage_info":
                return ClanPermission.MANAGE_CLAN_INFO;
            case "clan.kick":
                return ClanPermission.KICK_MEMBERS;
            case "clan.promote":
                return ClanPermission.PROMOTE_MEMBERS;
            case "clan.demote":
                return ClanPermission.DEMOTE_MEMBERS;
            case "clan.ban":
                return ClanPermission.BAN_MEMBERS;
            case "clan.manage_ranks":
            case "clan.manage_permissions":
                return ClanPermission.MANAGE_PERMISSIONS;
            case "clan.transfer":
                return ClanPermission.TRANSFER_LEADERSHIP;
            case "clan.disband":
                return ClanPermission.DISBAND_CLAN;
            default:
                return null;
        }
    }

    private boolean clanExists(final String name) {
        final String query = "SELECT 1 FROM clans WHERE LOWER(name) = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check clan existence", exception);
        }
        return false;
    }

    private boolean tagExists(final String tag) {
        final String query = "SELECT 1 FROM clans WHERE LOWER(tag) = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, tag.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check clan tag existence", exception);
        }
        return false;
    }

    private int saveClanToDatabase(final Clan clan) {
        final String query = "INSERT INTO clans (name, tag, description, leader_uuid, max_members, points, level, bank_coins) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, clan.getName());
            statement.setString(2, clan.getTag());
            statement.setString(3, clan.getDescription());
            statement.setString(4, clan.getLeaderUUID().toString());
            statement.setInt(5, clan.getMaxMembers());
            statement.setInt(6, clan.getPoints());
            statement.setInt(7, clan.getLevel());
            statement.setLong(8, clan.getBankCoins());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create clan", exception);
        }
        return -1;
    }

    private void setupDefaultRanks(final Clan clan) {
        for (final ClanRole role : ClanRole.values()) {
            final EnumSet<ClanPermission> permissions = role == ClanRole.LEADER
                    ? EnumSet.allOf(ClanPermission.class)
                    : role.getPermissions();
            final ClanRank rank = new ClanRank(role.name(), role.getDisplayName(), role.getLevel(), permissions);
            clan.addRank(rank);
            saveRank(clan.getId(), rank);
        }

        final ClanRank leaderRank = getRankForRole(clan, ClanRole.LEADER);
        final String rankName = leaderRank != null ? leaderRank.getName() : ClanRole.LEADER.name();
        clan.addMember(new ClanMember(clan.getLeaderUUID(), rankName, System.currentTimeMillis(), 0L));
    }

    private void saveRank(final int clanId, final ClanRank rank) {
        final String query = "INSERT INTO clan_ranks (clan_id, name, display_name, permissions, priority, can_promote, can_demote, can_manage_ranks) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clanId);
            statement.setString(2, rank.getName());
            statement.setString(3, rank.getDisplayName());
            statement.setString(4, serializePermissions(rank.getPermissions()));
            statement.setInt(5, rank.getPriority());
            statement.setBoolean(6, rank.hasPermission(ClanPermission.PROMOTE_MEMBERS));
            statement.setBoolean(7, rank.hasPermission(ClanPermission.DEMOTE_MEMBERS));
            statement.setBoolean(8, rank.hasPermission(ClanPermission.MANAGE_PERMISSIONS));
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save clan rank", exception);
        }
    }

    private void saveLeaderMember(final Clan clan, final UUID leaderUUID) {
        final String query = "INSERT INTO clan_members (clan_id, player_uuid, rank_name, joined_at, total_contributions) VALUES (?, ?, ?, CURRENT_TIMESTAMP, 0)";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clan.getId());
            statement.setString(2, leaderUUID.toString());
            statement.setString(3, resolveRankName(clan, ClanRole.LEADER));
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add clan leader", exception);
        }
        playerClanCache.put(leaderUUID, clan.getName().toLowerCase(Locale.ROOT));
    }

    private void saveMember(final int clanId, final UUID uuid, final String rankName) {
        final String query = "INSERT INTO clan_members (clan_id, player_uuid, rank_name, joined_at, total_contributions) VALUES (?, ?, ?, CURRENT_TIMESTAMP, 0)";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clanId);
            statement.setString(2, uuid.toString());
            statement.setString(3, rankName);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add clan member", exception);
        }
    }

    private boolean removeMemberFromClan(final Clan clan, final UUID memberUuid) {
        if (clan == null || memberUuid == null) {
            return false;
        }
        deleteMemberPermissions(clan.getId(), memberUuid);
        final String query = "DELETE FROM clan_members WHERE clan_id = ? AND player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clan.getId());
            statement.setString(2, memberUuid.toString());
            final boolean removed = statement.executeUpdate() > 0;
            clan.removeMember(memberUuid);
            playerClanCache.remove(memberUuid);
            return removed;
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to remove clan member " + memberUuid + " from clan " + clan.getId(), exception);
            return false;
        }
    }

    private void addClanBan(final int clanId, final UUID memberUuid, final String reason, final long durationMs) {
        if (memberUuid == null) {
            return;
        }
        final long expiresAt = durationMs <= 0L ? -1L : System.currentTimeMillis() + durationMs;
        clanBanCache.computeIfAbsent(clanId, key -> new ConcurrentHashMap<>())
                .put(memberUuid, new ClanBanEntry(memberUuid, reason, expiresAt));
    }

    private boolean isPlayerBanned(final int clanId, final UUID memberUuid) {
        if (memberUuid == null) {
            return false;
        }
        final Map<UUID, ClanBanEntry> bans = clanBanCache.get(clanId);
        if (bans == null) {
            return false;
        }
        final ClanBanEntry entry = bans.get(memberUuid);
        if (entry == null) {
            return false;
        }
        if (!entry.isActive()) {
            bans.remove(memberUuid);
            return false;
        }
        return true;
    }

    private ClanInvitation saveInvitation(final int clanId, final UUID inviter, final UUID invited, final String message) {
        final String query = "INSERT INTO clan_invitations (clan_id, inviter_uuid, invited_uuid, message, status, created_at, expires_at) VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, ?)";
        final long expires = System.currentTimeMillis() + (7L * 24L * 60L * 60L * 1000L);
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, clanId);
            statement.setString(2, inviter.toString());
            statement.setString(3, invited.toString());
            statement.setString(4, message);
            statement.setTimestamp(5, new Timestamp(expires));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    final int id = keys.getInt(1);
                    return new ClanInvitation(id, clanId, inviter, invited, message, System.currentTimeMillis(), expires);
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create clan invitation", exception);
        }
        return new ClanInvitation(-1, clanId, inviter, invited, message, System.currentTimeMillis(), expires);
    }

    private void scheduleInvitationExpiration(final ClanInvitation invitation) {
        if (invitation.getId() <= 0) {
            return;
        }
        final long delay = Math.max(0L, invitation.getExpiresAt() - System.currentTimeMillis());
        final long ticks = Math.max(1L, delay / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> updateInvitationStatus(invitation.getId(), "EXPIRED"), ticks);
    }

    /**
     * Diffuse un message à tous les membres en ligne d'un clan.
     *
     * @param clan    Le clan concerné
     * @param message Le message à diffuser
     */
    public void broadcastClanMessage(final Clan clan, final String message) {
        if (clan == null || message == null || message.trim().isEmpty()) {
            return;
        }

        final Map<UUID, ClanMember> members = clan.getMembers();
        if (members.isEmpty()) {
            return;
        }

        int onlineCount = 0;
        for (final UUID memberUuid : members.keySet()) {
            final Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage("§6[Clan] §r" + message);
                onlineCount++;
            }
        }

        plugin.getLogger().info("Broadcast clan message to " + onlineCount + " online members of clan " + clan.getName());
    }

    /**
     * Diffuse un message à tous les membres en ligne d'un clan avec un préfixe personnalisé.
     *
     * @param clan    Le clan concerné
     * @param message Le message à diffuser
     * @param prefix  Le préfixe personnalisé (sans les couleurs)
     */
    public void broadcastClanMessage(final Clan clan, final String message, final String prefix) {
        if (clan == null || message == null || message.trim().isEmpty()) {
            return;
        }

        final Map<UUID, ClanMember> members = clan.getMembers();
        if (members.isEmpty()) {
            return;
        }

        final String formattedPrefix = prefix != null && !prefix.trim().isEmpty() ? "§6[" + prefix + "] §r" : "§6[Clan] §r";
        for (final UUID memberUuid : members.keySet()) {
            final Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(formattedPrefix + message);
            }
        }
    }

    /**
     * Diffuse un message d'annonce importante à tous les membres du clan.
     *
     * @param clan         Le clan concerné
     * @param announcement L'annonce à diffuser
     */
    public void broadcastClanAnnouncement(final Clan clan, final String announcement) {
        if (clan == null || announcement == null || announcement.trim().isEmpty()) {
            return;
        }

        final Map<UUID, ClanMember> members = clan.getMembers();
        if (members.isEmpty()) {
            return;
        }

        for (final UUID memberUuid : members.keySet()) {
            final Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage("§6§l[" + clan.getTag() + "] ANNONCE §r§e" + announcement);
                member.playSound(member.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            }
        }
    }

    /**
     * Diffuse un message de bienvenue à un nouveau membre du clan.
     *
     * @param clan      Le clan concerné
     * @param newMember Le nouveau membre
     */
    public void broadcastWelcomeMessage(final Clan clan, final Player newMember) {
        if (clan == null || newMember == null) {
            return;
        }

        final String welcomeMessage = "§a" + newMember.getName() + " §7a rejoint le clan ! Souhaitez-lui la bienvenue ! 🎉";
        broadcastClanMessage(clan, welcomeMessage);
    }

    /**
     * Diffuse un message de départ d'un membre du clan.
     *
     * @param clan               Le clan concerné
     * @param leavingMemberName  Le nom du membre qui part
     * @param reason             La raison du départ (optionnel)
     */
    public void broadcastLeaveMessage(final Clan clan, final String leavingMemberName, final String reason) {
        if (clan == null || leavingMemberName == null) {
            return;
        }

        String leaveMessage = "§c" + leavingMemberName + " §7a quitté le clan.";
        if (reason != null && !reason.trim().isEmpty()) {
            leaveMessage += " §8(" + reason + ")";
        }

        broadcastClanMessage(clan, leaveMessage);
    }

    /**
     * Diffuse un message de promotion ou de rétrogradation dans le clan.
     *
     * @param clan       Le clan concerné
     * @param targetName Le nom du membre concerné
     * @param newRank    Le nouveau rang
     * @param promotion  true si c'est une promotion, false si c'est une rétrogradation
     */
    public void broadcastRankChangeMessage(final Clan clan, final String targetName, final String newRank, final boolean promotion) {
        if (clan == null || targetName == null || newRank == null) {
            return;
        }

        final String action = promotion ? "promu" : "rétrogradé";
        final String color = promotion ? "§a" : "§c";
        final String rankMessage = color + targetName + " §7a été " + action + " au rang §e" + newRank + "§7 !";
        broadcastClanMessage(clan, rankMessage);
    }

    /**
     * Envoie un message privé à tous les membres disposant d'un rang avec une priorité minimale.
     *
     * @param clan                 Le clan concerné
     * @param message              Le message à envoyer
     * @param minimumRankPriority  La priorité minimale du rang requis
     */
    public void broadcastToRank(final Clan clan, final String message, final int minimumRankPriority) {
        if (clan == null || message == null || message.trim().isEmpty()) {
            return;
        }

        final Map<UUID, ClanMember> members = clan.getMembers();
        if (members.isEmpty()) {
            return;
        }

        for (final UUID memberUuid : members.keySet()) {
            final Player member = Bukkit.getPlayer(memberUuid);
            if (member == null || !member.isOnline()) {
                continue;
            }

            final ClanMember clanMember = clan.getMember(memberUuid);
            if (clanMember == null) {
                continue;
            }

            final ClanRank rank = clan.getRank(clanMember.getRankName());
            if (rank != null && rank.getPriority() >= minimumRankPriority) {
                member.sendMessage("§6[Clan Staff] §r" + message);
            }
        }
    }

    private void updateInvitationStatus(final int invitationId, final String status) {
        final String query = "UPDATE clan_invitations SET status = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, status);
            statement.setInt(2, invitationId);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update clan invitation status", exception);
        }
    }

    private ClanInvitation getPendingInvitation(final int clanId, final UUID invitedUuid) {
        final String query = "SELECT id, inviter_uuid, message, created_at, expires_at FROM clan_invitations WHERE clan_id = ? AND invited_uuid = ? AND status = 'PENDING'";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clanId);
            statement.setString(2, invitedUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final int id = resultSet.getInt("id");
                    final UUID inviterUuid = UUID.fromString(resultSet.getString("inviter_uuid"));
                    final String message = resultSet.getString("message");
                    final Timestamp created = resultSet.getTimestamp("created_at");
                    final Timestamp expires = resultSet.getTimestamp("expires_at");
                    return new ClanInvitation(id, clanId, inviterUuid, invitedUuid, message,
                            created != null ? created.getTime() : System.currentTimeMillis(),
                            expires != null ? expires.getTime() : System.currentTimeMillis());
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch clan invitation", exception);
        }
        return null;
    }

    private Clan loadClanById(final int clanId) {
        final String query = "SELECT id, name, tag, leader_uuid, description, max_members, points, level, bank_coins FROM clans WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clanId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final String clanName = resultSet.getString("name");
                    final String tag = resultSet.getString("tag");
                    final UUID leaderUUID = UUID.fromString(resultSet.getString("leader_uuid"));
                    final Clan clan = new Clan(clanId, clanName, tag, leaderUUID);
                    clan.setDescription(resultSet.getString("description"));
                    clan.setMaxMembers(resultSet.getInt("max_members"));
                    clan.addPoints(resultSet.getInt("points"));
                    clan.setLevel(resultSet.getInt("level"));
                    clan.setBankCoins(resultSet.getLong("bank_coins"));
                    loadClanRanks(clan, connection);
                    loadClanMembers(clan, connection);
                    return clan;
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load clan by id", exception);
        }
        return null;
    }

    private Clan getClanByName(final String name) {
        if (name == null) {
            return null;
        }
        final Clan cached = clanCache.get(name.toLowerCase(Locale.ROOT));
        if (cached != null) {
            return cached;
        }
        final String query = "SELECT id, name, tag, leader_uuid, description, max_members, points, level, bank_coins FROM clans WHERE LOWER(name) = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final int id = resultSet.getInt("id");
                    final String clanName = resultSet.getString("name");
                    final String tag = resultSet.getString("tag");
                    final UUID leaderUUID = UUID.fromString(resultSet.getString("leader_uuid"));
                    final Clan clan = new Clan(id, clanName, tag, leaderUUID);
                    clan.setDescription(resultSet.getString("description"));
                    clan.setMaxMembers(resultSet.getInt("max_members"));
                    clan.addPoints(resultSet.getInt("points"));
                    clan.setLevel(resultSet.getInt("level"));
                    clan.setBankCoins(resultSet.getLong("bank_coins"));
                    loadClanRanks(clan, connection);
                    loadClanMembers(clan, connection);
                    cacheClan(clan);
                    return clan;
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load clan by name", exception);
        }
        return null;
    }

    private Clan loadClanForPlayer(final UUID uuid) {
        final String query = "SELECT c.id, c.name FROM clans c JOIN clan_members cm ON c.id = cm.clan_id WHERE cm.player_uuid = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final String clanName = resultSet.getString("name");
                    return getClanByName(clanName);
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load clan for player", exception);
        }
        return null;
    }

    private void loadClanRanks(final Clan clan, final Connection connection) throws SQLException {
        final String query = "SELECT name, display_name, permissions, priority, can_promote, can_demote, can_manage_ranks FROM clan_ranks WHERE clan_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clan.getId());
            try (ResultSet resultSet = statement.executeQuery()) {
                final ResultSetMetaData metaData = resultSet.getMetaData();
                while (resultSet.next()) {
                    final String name = resultSet.getString("name");
                    final String displayName = resultSet.getString("display_name");
                    final String permissionsString = resultSet.getString("permissions");
                    final int priority = resultSet.getInt("priority");
                    final Set<ClanPermission> permissions = deserializePermissions(permissionsString);
                    if (hasColumn(metaData, "can_promote") && resultSet.getBoolean("can_promote")) {
                        permissions.add(ClanPermission.PROMOTE_MEMBERS);
                    }
                    if (hasColumn(metaData, "can_demote") && resultSet.getBoolean("can_demote")) {
                        permissions.add(ClanPermission.DEMOTE_MEMBERS);
                    }
                    if (hasColumn(metaData, "can_manage_ranks") && resultSet.getBoolean("can_manage_ranks")) {
                        permissions.add(ClanPermission.MANAGE_PERMISSIONS);
                    }
                    clan.addRank(new ClanRank(name, displayName, priority, permissions));
                }
            }
        }
    }

    private void loadClanMembers(final Clan clan, final Connection connection) throws SQLException {
        final String query = "SELECT player_uuid, rank_name, joined_at, total_contributions FROM clan_members WHERE clan_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clan.getId());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID uuid = UUID.fromString(resultSet.getString("player_uuid"));
                    final String rankName = resultSet.getString("rank_name");
                    final Timestamp joinedAt = resultSet.getTimestamp("joined_at");
                    final long totalContributions = resultSet.getLong("total_contributions");
                    final ClanMember member = new ClanMember(uuid, rankName,
                            joinedAt != null ? joinedAt.getTime() : System.currentTimeMillis(), totalContributions);
                    member.setPermissions(loadMemberPermissions(clan.getId(), uuid, connection));
                    clan.addMember(member);
                }
            }
        }
    }

    private Set<ClanPermission> loadMemberPermissions(final int clanId, final UUID memberUuid,
                                                      final Connection connection) {
        final String query = "SELECT permissions FROM clan_member_permissions WHERE clan_id = ? AND player_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clanId);
            statement.setString(2, memberUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final String data = resultSet.getString("permissions");
                    return deserializePermissions(data);
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.FINE,
                    "Failed to load clan member permissions for " + memberUuid, exception);
        }
        return EnumSet.noneOf(ClanPermission.class);
    }

    private ClanRank getPlayerRank(final Clan clan, final UUID playerUuid) {
        if (clan == null || playerUuid == null) {
            return null;
        }
        if (clan.isLeader(playerUuid)) {
            return getRankForRole(clan, ClanRole.LEADER);
        }
        final ClanMember member = clan.getMember(playerUuid);
        if (member == null) {
            return null;
        }
        return clan.getRank(member.getRankName());
    }

    private ClanRank getRankForRole(final Clan clan, final ClanRole role) {
        if (clan == null || role == null) {
            return null;
        }
        ClanRank rank = clan.getRank(role.name());
        if (rank == null) {
            rank = clan.getRank(role.getDisplayName());
        }
        return rank;
    }

    private String resolveRankName(final Clan clan, final ClanRole role) {
        final ClanRank rank = getRankForRole(clan, role);
        return rank != null ? rank.getName() : role.name();
    }

    private ClanRole getMemberRole(final Clan clan, final UUID memberUuid) {
        if (clan == null || memberUuid == null) {
            return null;
        }
        if (clan.isLeader(memberUuid)) {
            return ClanRole.LEADER;
        }
        final ClanMember member = clan.getMember(memberUuid);
        if (member == null) {
            return null;
        }
        final String rankName = member.getRankName();
        ClanRole role = ClanRole.fromName(rankName);
        if (role != null) {
            return role;
        }
        final ClanRank rank = clan.getRank(rankName);
        if (rank != null) {
            role = ClanRole.fromName(rank.getName());
            if (role != null) {
                return role;
            }
            role = ClanRole.fromName(rank.getDisplayName());
            if (role != null) {
                return role;
            }
        }
        return null;
    }

    private boolean updateMemberRole(final Clan clan, final UUID memberUuid, final ClanRole newRole) {
        if (clan == null || memberUuid == null || newRole == null) {
            return false;
        }
        final ClanRank targetRank = getRankForRole(clan, newRole);
        return targetRank != null && setPlayerRank(clan, memberUuid, targetRank);
    }

    private ClanRank getNextRank(final Clan clan, final int currentPriority) {
        return clan.getRanks().stream()
                .filter(rank -> rank.getPriority() > currentPriority)
                .min(Comparator.comparingInt(ClanRank::getPriority))
                .orElse(null);
    }

    private ClanRank getPreviousRank(final Clan clan, final int currentPriority) {
        return clan.getRanks().stream()
                .filter(rank -> rank.getPriority() < currentPriority)
                .max(Comparator.comparingInt(ClanRank::getPriority))
                .orElse(null);
    }

    private boolean setPlayerRank(final Clan clan, final UUID playerUuid, final ClanRank targetRank) {
        if (clan == null || playerUuid == null || targetRank == null) {
            return false;
        }
        final String query = "UPDATE clan_members SET rank_name = ? WHERE clan_id = ? AND player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, targetRank.getName());
            statement.setInt(2, clan.getId());
            statement.setString(3, playerUuid.toString());
            final boolean updated = statement.executeUpdate() > 0;
            if (updated) {
                final ClanMember member = clan.getMember(playerUuid);
                if (member != null) {
                    member.setRankName(targetRank.getName());
                }
                final Player player = Bukkit.getPlayer(playerUuid);
                if (player != null) {
                    player.sendMessage("§aVotre rang a été modifié: " + targetRank.getDisplayName());
                }
            }
            return updated;
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update clan member rank", exception);
            return false;
        }
    }

    private boolean storeMemberPermissions(final int clanId, final UUID memberUuid,
                                           final Set<ClanPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return deleteMemberPermissions(clanId, memberUuid);
        }
        final String query;
        if (databaseManager.getDatabaseType() == DatabaseManager.DatabaseType.MYSQL) {
            query = "INSERT INTO clan_member_permissions (clan_id, player_uuid, permissions) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE permissions = VALUES(permissions)";
        } else {
            query = "INSERT INTO clan_member_permissions (clan_id, player_uuid, permissions) VALUES (?, ?, ?) "
                    + "ON CONFLICT(clan_id, player_uuid) DO UPDATE SET permissions = excluded.permissions";
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clanId);
            statement.setString(2, memberUuid.toString());
            statement.setString(3, serializePermissions(permissions));
            statement.executeUpdate();
            return true;
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to store clan member permissions for " + memberUuid, exception);
            return false;
        }
    }

    private boolean deleteMemberPermissions(final int clanId, final UUID memberUuid) {
        final String query = "DELETE FROM clan_member_permissions WHERE clan_id = ? AND player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clanId);
            statement.setString(2, memberUuid.toString());
            statement.executeUpdate();
            return true;
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to delete clan member permissions for " + memberUuid, exception);
            return false;
        }
    }

    private Set<ClanPermission> deserializePermissions(final String data) {
        if (data == null || data.isEmpty()) {
            return EnumSet.noneOf(ClanPermission.class);
        }
        final String[] parts = data.replace("[", "").replace("]", "").replace("\"", "").split(",");
        final EnumSet<ClanPermission> permissions = EnumSet.noneOf(ClanPermission.class);
        for (final String part : parts) {
            final String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                permissions.add(ClanPermission.valueOf(trimmed));
            } catch (final IllegalArgumentException ignored) {
                // Ignore invalid entries
            }
        }
        return permissions;
    }

    private String serializePermissions(final Set<ClanPermission> permissions) {
        if (permissions.isEmpty()) {
            return "[]";
        }
        return permissions.stream().map(Enum::name).collect(Collectors.joining(",", "[", "]"));
    }

    private Set<ClanPermission> resolvePreset(final String presetKey) {
        final String normalized = presetKey.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "default", "aucun", "none" -> EnumSet.noneOf(ClanPermission.class);
            case "moderateur", "moderator", "mod" -> EnumSet.of(
                    ClanPermission.INVITE_MEMBERS, ClanPermission.KICK_MEMBERS);
            case "officier", "officer" -> EnumSet.of(
                    ClanPermission.INVITE_MEMBERS, ClanPermission.KICK_MEMBERS,
                    ClanPermission.PROMOTE_MEMBERS, ClanPermission.DEMOTE_MEMBERS);
            case "gestion", "manager" -> EnumSet.of(
                    ClanPermission.INVITE_MEMBERS, ClanPermission.KICK_MEMBERS,
                    ClanPermission.PROMOTE_MEMBERS, ClanPermission.DEMOTE_MEMBERS,
                    ClanPermission.BAN_MEMBERS, ClanPermission.MANAGE_CLAN_INFO);
            case "banquier", "banker" -> EnumSet.of(ClanPermission.MANAGE_CLAN_INFO);
            case "admin", "toutes", "all" -> EnumSet.allOf(ClanPermission.class);
            default -> EnumSet.noneOf(ClanPermission.class);
        };
    }

    private boolean hasColumn(final ResultSetMetaData metaData, final String column) throws SQLException {
        final int count = metaData.getColumnCount();
        for (int index = 1; index <= count; index++) {
            if (column.equalsIgnoreCase(metaData.getColumnName(index))) {
                return true;
            }
        }
        return false;
    }

    private String formatDuration(final long durationMs) {
        if (durationMs <= 0L) {
            return "Permanent";
        }
        long remainingSeconds = durationMs / 1000L;
        final long days = remainingSeconds / 86400L;
        remainingSeconds %= 86400L;
        final long hours = remainingSeconds / 3600L;
        remainingSeconds %= 3600L;
        final long minutes = remainingSeconds / 60L;
        final long seconds = remainingSeconds % 60L;

        final List<String> parts = new ArrayList<>();
        if (days > 0L) {
            parts.add(days + "j");
        }
        if (hours > 0L) {
            parts.add(hours + "h");
        }
        if (minutes > 0L) {
            parts.add(minutes + "m");
        }
        if (seconds > 0L || parts.isEmpty()) {
            parts.add(seconds + "s");
        }
        return String.join(" ", parts);
    }

    private String getNameByUuid(final UUID uuid) {
        if (uuid == null) {
            return null;
        }
        final Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }
        final org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        return offlinePlayer.getName();
    }

    private UUID getUUIDFromName(final String name) {
        if (name == null) {
            return null;
        }
        final String query = "SELECT uuid FROM players WHERE LOWER(username) = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return UUID.fromString(resultSet.getString("uuid"));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to resolve player name", exception);
        }
        return null;
    }

    private static final class ClanBanEntry {

        private final UUID memberUuid;
        private final String reason;
        private final long expiresAt;

        private ClanBanEntry(final UUID memberUuid, final String reason, final long expiresAt) {
            this.memberUuid = memberUuid;
            this.reason = reason;
            this.expiresAt = expiresAt;
        }

        private boolean isActive() {
            return expiresAt < 0L || expiresAt > System.currentTimeMillis();
        }

        private String getReason() {
            return reason;
        }

        private long getExpiresAt() {
            return expiresAt;
        }
    }
}
