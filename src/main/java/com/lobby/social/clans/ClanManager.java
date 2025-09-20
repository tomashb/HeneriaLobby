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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ClanManager {

    private final LobbyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;
    private final Map<String, Clan> clanCache = new HashMap<>();
    private final Map<UUID, String> playerClanCache = new HashMap<>();

    public ClanManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();
    }

    public void reload() {
        clanCache.clear();
        playerClanCache.clear();
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

    public void inviteToClan(final Player inviter, final String targetName, final String message) {
        final Clan clan = getPlayerClan(inviter.getUniqueId());
        if (clan == null) {
            inviter.sendMessage("§cVous n'êtes dans aucun clan !");
            return;
        }
        if (!clan.hasPermission(inviter.getUniqueId(), ClanPermission.INVITE)) {
            inviter.sendMessage("§cVous n'avez pas la permission d'inviter des joueurs !");
            return;
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
            return;
        }
        if (hasPlayerClan(targetUUID)) {
            inviter.sendMessage("§c" + targetName + " est déjà dans un clan !");
            return;
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
        saveMember(clan.getId(), player.getUniqueId(), "Membre");
        clan.addMember(new ClanMember(player.getUniqueId(), "Membre", System.currentTimeMillis(), 0L));
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

    private void cacheClan(final Clan clan) {
        clanCache.put(clan.getName().toLowerCase(Locale.ROOT), clan);
        clanCache.put(clan.getTag().toLowerCase(Locale.ROOT), clan);
        for (final UUID member : clan.getMembers().keySet()) {
            playerClanCache.put(member, clan.getName().toLowerCase(Locale.ROOT));
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
        final EnumSet<ClanPermission> leaderPermissions = EnumSet.allOf(ClanPermission.class);
        final ClanRank leaderRank = new ClanRank("Leader", Integer.MAX_VALUE, leaderPermissions);
        clan.addRank(leaderRank);
        saveRank(clan.getId(), leaderRank);

        final EnumSet<ClanPermission> memberPermissions = EnumSet.noneOf(ClanPermission.class);
        final ClanRank memberRank = new ClanRank("Membre", 0, memberPermissions);
        clan.addRank(memberRank);
        saveRank(clan.getId(), memberRank);

        clan.addMember(new ClanMember(clan.getLeaderUUID(), leaderRank.getName(), System.currentTimeMillis(), 0L));
    }

    private void saveRank(final int clanId, final ClanRank rank) {
        final String query = "INSERT INTO clan_ranks (clan_id, name, permissions, priority) VALUES (?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clanId);
            statement.setString(2, rank.getName());
            statement.setString(3, serializePermissions(rank.getPermissions()));
            statement.setInt(4, rank.getPriority());
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
            statement.setString(3, "Leader");
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
        final String query = "SELECT name, permissions, priority FROM clan_ranks WHERE clan_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, clan.getId());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final String name = resultSet.getString("name");
                    final String permissionsString = resultSet.getString("permissions");
                    final int priority = resultSet.getInt("priority");
                    final Set<ClanPermission> permissions = deserializePermissions(permissionsString);
                    clan.addRank(new ClanRank(name, priority, permissions));
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
                    clan.addMember(new ClanMember(uuid, rankName,
                            joinedAt != null ? joinedAt.getTime() : System.currentTimeMillis(), totalContributions));
                }
            }
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
}
