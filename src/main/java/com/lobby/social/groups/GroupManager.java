package com.lobby.social.groups;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class GroupManager {

    private final LobbyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Group> playerGroups = new HashMap<>();
    private final Map<Integer, Group> groupCache = new HashMap<>();

    public GroupManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public void reload() {
        playerGroups.clear();
        groupCache.clear();
    }

    public void createGroup(final Player leader) {
        if (hasGroup(leader.getUniqueId())) {
            leader.sendMessage("§cVous êtes déjà dans un groupe !");
            return;
        }
        final Group group = new Group(leader.getUniqueId());
        final int groupId = saveGroupToDatabase(group);
        if (groupId <= 0) {
            leader.sendMessage("§cImpossible de créer le groupe pour le moment.");
            return;
        }
        group.setId(groupId);
        saveMember(groupId, leader.getUniqueId(), "LEADER");
        group.addMember(leader.getUniqueId());
        cacheGroup(group);
        playerGroups.put(leader.getUniqueId(), group);
        leader.sendMessage("§aGroupe créé avec succès !");
        leader.sendMessage("§7Utilisez §e/group invite <joueur> §7pour inviter des amis.");
    }

    public void inviteToGroup(final Player inviter, final String targetName) {
        final Group group = getPlayerGroup(inviter.getUniqueId());
        if (group == null) {
            inviter.sendMessage("§cVous n'êtes dans aucun groupe !");
            return;
        }
        if (!group.canInvite(inviter.getUniqueId())) {
            inviter.sendMessage("§cVous n'avez pas la permission d'inviter des joueurs !");
            return;
        }
        final Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            inviter.sendMessage("§cJoueur introuvable ou hors ligne.");
            return;
        }
        if (hasGroup(target.getUniqueId())) {
            inviter.sendMessage("§c" + target.getName() + " est déjà dans un groupe !");
            return;
        }
        if (group.isFull()) {
            inviter.sendMessage("§cVotre groupe est plein ! (" + group.getSize() + "/" + group.getMaxSize() + ")");
            return;
        }
        final GroupInvitation invitation = saveInvitation(group.getId(), inviter.getUniqueId(), target.getUniqueId());
        inviter.sendMessage("§aInvitation envoyée à §6" + target.getName() + "§a !");
        target.sendMessage("§e" + inviter.getName() + " §avous a invité à rejoindre son groupe !");
        target.sendMessage("§7Groupe: §f" + group.getDisplayName() + " §7(" + group.getSize() + "/" + group.getMaxSize() + ")");
        target.sendMessage("§7Tapez §a/group accept " + inviter.getName() + " §7pour accepter");
        target.sendMessage("§7ou §c/group deny " + inviter.getName() + " §7pour refuser");
        target.playSound(target.getLocation(), Sound.UI_TOAST_IN, 1.0f, 1.0f);
        scheduleExpiration(invitation);
    }

    public void acceptInvitation(final Player player, final String inviterName) {
        if (hasGroup(player.getUniqueId())) {
            player.sendMessage("§cVous êtes déjà dans un groupe !");
            return;
        }
        final Player inviter = Bukkit.getPlayerExact(inviterName);
        final UUID inviterUUID = inviter != null ? inviter.getUniqueId() : getUuidByName(inviterName);
        if (inviterUUID == null) {
            player.sendMessage("§cInvitation introuvable.");
            return;
        }
        final GroupInvitation invitation = getPendingInvitation(inviterUUID, player.getUniqueId());
        if (invitation == null) {
            player.sendMessage("§cAucune invitation de " + inviterName + " trouvée.");
            return;
        }
        final Group group = getGroupById(invitation.getGroupId());
        if (group == null) {
            player.sendMessage("§cCe groupe n'existe plus.");
            markInvitationStatus(invitation.getId(), "EXPIRED");
            return;
        }
        if (group.isFull()) {
            player.sendMessage("§cLe groupe est complet.");
            return;
        }
        saveMember(group.getId(), player.getUniqueId(), "MEMBER");
        group.addMember(player.getUniqueId());
        playerGroups.put(player.getUniqueId(), group);
        markInvitationStatus(invitation.getId(), "ACCEPTED");
        broadcastGroupMessage(group, "§a" + player.getName() + " a rejoint le groupe !");
    }

    public void denyInvitation(final Player player, final String inviterName) {
        final Player inviter = Bukkit.getPlayerExact(inviterName);
        final UUID inviterUUID = inviter != null ? inviter.getUniqueId() : getUuidByName(inviterName);
        if (inviterUUID == null) {
            player.sendMessage("§cInvitation introuvable.");
            return;
        }
        final GroupInvitation invitation = getPendingInvitation(inviterUUID, player.getUniqueId());
        if (invitation == null) {
            player.sendMessage("§cAucune invitation de " + inviterName + " trouvée.");
            return;
        }
        markInvitationStatus(invitation.getId(), "DECLINED");
        player.sendMessage("§cInvitation refusée.");
    }

    public void leaveGroup(final Player player) {
        final Group group = getPlayerGroup(player.getUniqueId());
        if (group == null) {
            player.sendMessage("§cVous n'êtes dans aucun groupe.");
            return;
        }
        if (group.isLeader(player.getUniqueId())) {
            disbandGroup(player);
            return;
        }
        removeMember(group.getId(), player.getUniqueId());
        group.removeMember(player.getUniqueId());
        playerGroups.remove(player.getUniqueId());
        broadcastGroupMessage(group, "§c" + player.getName() + " a quitté le groupe.");
    }

    public void disbandGroup(final Player leader) {
        final Group group = getPlayerGroup(leader.getUniqueId());
        if (group == null || !group.isLeader(leader.getUniqueId())) {
            leader.sendMessage("§cVous n'êtes pas le leader d'un groupe !");
            return;
        }
        disbandGroup(group);
        leader.sendMessage("§aGroupe dissous avec succès.");
    }

    public Group getPlayerGroup(final UUID playerUUID) {
        final Group cached = playerGroups.get(playerUUID);
        if (cached != null) {
            return cached;
        }
        final Group group = loadGroupForPlayer(playerUUID);
        if (group != null) {
            playerGroups.put(playerUUID, group);
        }
        return group;
    }

    public boolean hasGroup(final UUID uuid) {
        return getPlayerGroup(uuid) != null;
    }

    public int countPendingInvitations(final UUID playerUUID) {
        final String query = "SELECT COUNT(*) FROM group_invitations WHERE invited_uuid = ? AND status = 'PENDING'";
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
                    "Failed to count pending group invitations for " + playerUUID, exception);
        }
        return 0;
    }

    public int countSentInvitations(final UUID inviterUUID) {
        final String query = "SELECT COUNT(*) FROM group_invitations WHERE inviter_uuid = ? AND status = 'PENDING'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, inviterUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to count sent group invitations for " + inviterUUID, exception);
        }
        return 0;
    }

    public int countCachedOpenGroups() {
        return (int) groupCache.values().stream()
                .filter(group -> group != null && !group.isFull())
                .count();
    }

    private Group getGroupById(final int id) {
        final Group cached = groupCache.get(id);
        if (cached != null) {
            return cached;
        }
        final Group group = loadGroupById(id);
        if (group != null) {
            cacheGroup(group);
        }
        return group;
    }

    private void cacheGroup(final Group group) {
        groupCache.put(group.getId(), group);
        for (final UUID member : group.getMembers()) {
            playerGroups.put(member, group);
        }
    }

    private int saveGroupToDatabase(final Group group) {
        final String query = "INSERT INTO groups_table (leader_uuid, name, max_members, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, group.getLeaderUUID().toString());
            statement.setString(2, group.getName());
            statement.setInt(3, group.getMaxSize());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create group", exception);
        }
        return -1;
    }

    private void saveMember(final int groupId, final UUID uuid, final String role) {
        final String query = "INSERT INTO group_members (group_id, player_uuid, role, joined_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, groupId);
            statement.setString(2, uuid.toString());
            statement.setString(3, role);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add member to group", exception);
        }
    }

    private void removeMember(final int groupId, final UUID uuid) {
        final String query = "DELETE FROM group_members WHERE group_id = ? AND player_uuid = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, groupId);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove member from group", exception);
        }
    }

    private void disbandGroup(final Group group) {
        final String query = "UPDATE groups_table SET disbanded_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, group.getId());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to disband group", exception);
        }
        for (final UUID member : group.getMembers()) {
            final Player player = Bukkit.getPlayer(member);
            if (player != null) {
                player.sendMessage("§cLe groupe a été dissous par le leader.");
            }
            playerGroups.remove(member);
        }
        groupCache.remove(group.getId());
    }

    private Group loadGroupForPlayer(final UUID playerUUID) {
        final String query = "SELECT g.id, g.leader_uuid, g.name, g.max_members, g.created_at FROM groups_table g JOIN group_members gm ON g.id = gm.group_id WHERE gm.player_uuid = ? AND g.disbanded_at IS NULL";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final int id = resultSet.getInt("id");
                    final UUID leaderUUID = UUID.fromString(resultSet.getString("leader_uuid"));
                    final String name = resultSet.getString("name");
                    final int maxMembers = resultSet.getInt("max_members");
                    final Timestamp createdAt = resultSet.getTimestamp("created_at");
                    final Group group = new Group(id, leaderUUID, name, maxMembers, createdAt != null ? createdAt.getTime() : System.currentTimeMillis());
                    populateMembers(group, connection);
                    cacheGroup(group);
                    return group;
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load group for player " + playerUUID, exception);
        }
        return null;
    }

    private Group loadGroupById(final int id) {
        final String query = "SELECT id, leader_uuid, name, max_members, created_at FROM groups_table WHERE id = ? AND disbanded_at IS NULL";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final UUID leaderUUID = UUID.fromString(resultSet.getString("leader_uuid"));
                    final String name = resultSet.getString("name");
                    final int maxMembers = resultSet.getInt("max_members");
                    final Timestamp createdAt = resultSet.getTimestamp("created_at");
                    final Group group = new Group(id, leaderUUID, name, maxMembers, createdAt != null ? createdAt.getTime() : System.currentTimeMillis());
                    populateMembers(group, connection);
                    return group;
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load group " + id, exception);
        }
        return null;
    }

    private void populateMembers(final Group group, final Connection connection) throws SQLException {
        final String query = "SELECT player_uuid, role FROM group_members WHERE group_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, group.getId());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID uuid = UUID.fromString(resultSet.getString("player_uuid"));
                    final String role = resultSet.getString("role");
                    group.addMember(uuid);
                    if ("MODERATOR".equalsIgnoreCase(role)) {
                        group.addModerator(uuid);
                    }
                }
            }
        }
    }

    private GroupInvitation saveInvitation(final int groupId, final UUID inviter, final UUID invited) {
        final String query = "INSERT INTO group_invitations (group_id, inviter_uuid, invited_uuid, status, created_at, expires_at) VALUES (?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, ?)";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, groupId);
            statement.setString(2, inviter.toString());
            statement.setString(3, invited.toString());
            statement.setTimestamp(4, new Timestamp(System.currentTimeMillis() + (5 * 60 * 1000L)));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    final int id = keys.getInt(1);
                    final long created = System.currentTimeMillis();
                    final long expires = created + (5 * 60 * 1000L);
                    return new GroupInvitation(id, groupId, inviter, invited, created, expires);
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save group invitation", exception);
        }
        return new GroupInvitation(-1, groupId, inviter, invited, System.currentTimeMillis(), System.currentTimeMillis());
    }

    private GroupInvitation getPendingInvitation(final UUID inviter, final UUID invited) {
        final String query = "SELECT id, group_id, created_at, expires_at FROM group_invitations WHERE inviter_uuid = ? AND invited_uuid = ? AND status = 'PENDING' ORDER BY created_at DESC";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, inviter.toString());
            statement.setString(2, invited.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final int id = resultSet.getInt("id");
                    final int groupId = resultSet.getInt("group_id");
                    final Timestamp created = resultSet.getTimestamp("created_at");
                    final Timestamp expires = resultSet.getTimestamp("expires_at");
                    return new GroupInvitation(id, groupId, inviter, invited,
                            created != null ? created.getTime() : System.currentTimeMillis(),
                            expires != null ? expires.getTime() : System.currentTimeMillis());
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load group invitation", exception);
        }
        return null;
    }

    private void markInvitationStatus(final int invitationId, final String status) {
        final String query = "UPDATE group_invitations SET status = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, status);
            statement.setInt(2, invitationId);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update invitation status", exception);
        }
    }

    private void scheduleExpiration(final GroupInvitation invitation) {
        if (invitation.getId() <= 0) {
            return;
        }
        final long delay = Math.max(0L, invitation.getExpiresAt() - System.currentTimeMillis());
        final long ticks = Math.max(1L, delay / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> markInvitationStatus(invitation.getId(), "EXPIRED"), ticks);
    }

    private void broadcastGroupMessage(final Group group, final String message) {
        for (final UUID member : group.getMembers()) {
            final Player player = Bukkit.getPlayer(member);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private UUID getUuidByName(final String name) {
        if (name == null) {
            return null;
        }
        final Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            return player.getUniqueId();
        }
        final String query = "SELECT uuid FROM players WHERE LOWER(username) = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name.toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return UUID.fromString(resultSet.getString("uuid"));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to resolve player " + name, exception);
        }
        return null;
    }
}
