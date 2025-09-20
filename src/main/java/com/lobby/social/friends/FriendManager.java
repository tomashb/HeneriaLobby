package com.lobby.social.friends;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.servers.ServerInfo;
import com.lobby.servers.ServerManager;
import com.lobby.velocity.VelocityManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class FriendManager {

    private final LobbyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Set<UUID>> friendsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, FriendSettings> settingsCache = new ConcurrentHashMap<>();

    public FriendManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public void reload() {
        friendsCache.clear();
        pendingRequests.clear();
        settingsCache.clear();
    }

    public void sendFriendRequest(final Player sender, final String targetName) {
        final Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable ou hors ligne.");
            return;
        }

        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage("§cVous ne pouvez pas vous ajouter vous-même !");
            return;
        }

        if (areFriends(sender.getUniqueId(), target.getUniqueId())) {
            sender.sendMessage("§cVous êtes déjà amis avec " + target.getName() + " !");
            return;
        }

        if (hasPendingRequest(sender.getUniqueId(), target.getUniqueId())) {
            sender.sendMessage("§cVous avez déjà envoyé une demande d'ami à " + target.getName() + " !");
            return;
        }

        final FriendSettings settings = getFriendSettings(target.getUniqueId());
        if (settings.getAcceptRequests() == AcceptMode.NONE) {
            sender.sendMessage("§c" + target.getName() + " n'accepte pas les demandes d'amis.");
            return;
        }

        if (settings.getAcceptRequests() == AcceptMode.FRIENDS_OF_FRIENDS && !hasMutualFriend(sender.getUniqueId(), target.getUniqueId())) {
            sender.sendMessage("§cVous devez avoir des amis en commun avec " + target.getName() + " pour envoyer une demande.");
            return;
        }

        saveFriendRequest(sender.getUniqueId(), target.getUniqueId());

        sender.sendMessage("§aDemande d'ami envoyée à §6" + target.getName() + "§a !");
        target.sendMessage("§e" + sender.getName() + " §avous a envoyé une demande d'ami !");
        target.sendMessage("§7Tapez §a/friend accept " + sender.getName() + " §7pour accepter");
        target.sendMessage("§7ou §c/friend deny " + sender.getName() + " §7pour refuser");
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    public void acceptFriendRequest(final Player player, final String senderName) {
        final UUID senderUUID = getUuidByName(senderName);
        if (senderUUID == null) {
            player.sendMessage("§cJoueur introuvable.");
            return;
        }

        if (!hasPendingRequest(senderUUID, player.getUniqueId())) {
            player.sendMessage("§cAucune demande d'ami de " + senderName + " trouvée.");
            return;
        }

        acceptFriendship(senderUUID, player.getUniqueId());

        addToFriendsCache(senderUUID, player.getUniqueId());
        addToFriendsCache(player.getUniqueId(), senderUUID);

        removePendingRequest(senderUUID, player.getUniqueId());

        player.sendMessage("§aVous êtes maintenant ami avec §6" + senderName + "§a !");

        final Player senderPlayer = Bukkit.getPlayer(senderUUID);
        if (senderPlayer != null) {
            senderPlayer.sendMessage("§6" + player.getName() + " §aa accepté votre demande d'ami !");
            senderPlayer.playSound(senderPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        final VelocityManager velocityManager = plugin.getVelocityManager();
        if (velocityManager != null) {
            velocityManager.broadcastFriendUpdate(player.getUniqueId(), "ACCEPT", senderUUID);
            velocityManager.broadcastFriendUpdate(senderUUID, "ACCEPT", player.getUniqueId());
        }
    }

    public void denyFriendRequest(final Player player, final String senderName) {
        final UUID senderUUID = getUuidByName(senderName);
        if (senderUUID == null) {
            player.sendMessage("§cJoueur introuvable.");
            return;
        }

        if (!hasPendingRequest(senderUUID, player.getUniqueId())) {
            player.sendMessage("§cAucune demande d'ami de " + senderName + " trouvée.");
            return;
        }

        removeFriendship(senderUUID, player.getUniqueId());
        removePendingRequest(senderUUID, player.getUniqueId());

        player.sendMessage("§cVous avez refusé la demande d'ami de §6" + senderName + "§c.");

        final Player senderPlayer = Bukkit.getPlayer(senderUUID);
        if (senderPlayer != null) {
            senderPlayer.sendMessage("§c" + player.getName() + " a refusé votre demande d'ami.");
        }
    }

    public void removeFriend(final Player player, final String targetName) {
        final UUID targetUUID = getUuidByName(targetName);
        if (targetUUID == null) {
            player.sendMessage("§cJoueur introuvable.");
            return;
        }
        if (!areFriends(player.getUniqueId(), targetUUID)) {
            player.sendMessage("§cVous n'êtes pas ami avec " + targetName + ".");
            return;
        }
        removeFriendship(player.getUniqueId(), targetUUID);
        friendsCache.computeIfPresent(player.getUniqueId(), (uuid, uuids) -> {
            uuids.remove(targetUUID);
            return uuids;
        });
        friendsCache.computeIfPresent(targetUUID, (uuid, uuids) -> {
            uuids.remove(player.getUniqueId());
            return uuids;
        });
        player.sendMessage("§cVous n'êtes plus ami avec §6" + targetName + "§c.");
        final Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer != null) {
            targetPlayer.sendMessage("§c" + player.getName() + " vous a retiré de sa liste d'amis.");
        }
        final VelocityManager velocityManager = plugin.getVelocityManager();
        if (velocityManager != null) {
            velocityManager.broadcastFriendUpdate(player.getUniqueId(), "REMOVE", targetUUID);
            velocityManager.broadcastFriendUpdate(targetUUID, "REMOVE", player.getUniqueId());
        }
    }

    public List<FriendInfo> getFriendsList(final UUID playerUUID) {
        final List<FriendInfo> friends = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT player_uuid, friend_uuid, status, created_at, accepted_at FROM friends WHERE (player_uuid = ? OR friend_uuid = ?) AND status = 'ACCEPTED'")) {
            statement.setString(1, playerUUID.toString());
            statement.setString(2, playerUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID playerOne = UUID.fromString(resultSet.getString("player_uuid"));
                    final UUID playerTwo = UUID.fromString(resultSet.getString("friend_uuid"));
                    final UUID friendUUID = playerOne.equals(playerUUID) ? playerTwo : playerOne;
                    final String name = getNameByUuid(friendUUID);
                    final Player friendPlayer = Bukkit.getPlayer(friendUUID);
                    final boolean online = friendPlayer != null && friendPlayer.isOnline();
                    String serverName = null;
                    if (online) {
                        final ServerManager serverManager = plugin.getServerManager();
                        if (serverManager != null) {
                            final ServerInfo info = serverManager.getServer(friendPlayer.getWorld().getName());
                            serverName = info != null ? info.getDisplayName() : friendPlayer.getWorld().getName();
                        }
                    }
                    final Timestamp acceptedTimestamp = resultSet.getTimestamp("accepted_at");
                    final long acceptedAt = acceptedTimestamp != null ? acceptedTimestamp.getTime() : System.currentTimeMillis();
                    final long lastSeen = getLastSeen(friendUUID);
                    friends.add(new FriendInfo(friendUUID, name, online, serverName, acceptedAt, lastSeen));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load friend list for " + playerUUID, exception);
        }
        return friends;
    }

    public List<UUID> getPendingRequests(final UUID playerUUID) {
        final Set<UUID> requests = pendingRequests.computeIfAbsent(playerUUID, this::loadPendingRequests);
        return new ArrayList<>(requests);
    }

    public int countSentRequests(final UUID playerUUID) {
        final String query = "SELECT COUNT(*) FROM friends WHERE player_uuid = ? AND status = 'PENDING'";
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
                    "Failed to count sent friend requests for " + playerUUID, exception);
        }
        return 0;
    }

    public long getOldestPendingRequestTimestamp(final UUID playerUUID) {
        final String query = "SELECT MIN(created_at) AS oldest FROM friends WHERE friend_uuid = ? AND status = 'PENDING'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final Timestamp timestamp = resultSet.getTimestamp("oldest");
                    if (timestamp != null) {
                        return timestamp.getTime();
                    }
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to fetch oldest pending friend request for " + playerUUID, exception);
        }
        return 0L;
    }

    public boolean areFriends(final UUID player1, final UUID player2) {
        final Set<UUID> friends = friendsCache.computeIfAbsent(player1, this::loadFriendsFromDatabase);
        return friends.contains(player2);
    }

    private boolean hasPendingRequest(final UUID sender, final UUID target) {
        final Set<UUID> requests = pendingRequests.computeIfAbsent(target, this::loadPendingRequests);
        return requests.contains(sender);
    }

    private void removePendingRequest(final UUID sender, final UUID target) {
        pendingRequests.computeIfPresent(target, (uuid, set) -> {
            set.remove(sender);
            return set;
        });
    }

    private void saveFriendRequest(final UUID senderUUID, final UUID targetUUID) {
        final String selectQuery = "SELECT status FROM friends WHERE player_uuid = ? AND friend_uuid = ?";
        final String insertQuery = "INSERT INTO friends (player_uuid, friend_uuid, status, created_at) VALUES (?, ?, 'PENDING', CURRENT_TIMESTAMP)";
        final String updateQuery = "UPDATE friends SET status = 'PENDING', created_at = CURRENT_TIMESTAMP WHERE player_uuid = ? AND friend_uuid = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement selectStatement = connection.prepareStatement(selectQuery)) {
            selectStatement.setString(1, senderUUID.toString());
            selectStatement.setString(2, targetUUID.toString());
            try (ResultSet resultSet = selectStatement.executeQuery()) {
                if (resultSet.next()) {
                    try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
                        updateStatement.setString(1, senderUUID.toString());
                        updateStatement.setString(2, targetUUID.toString());
                        updateStatement.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insertStatement = connection.prepareStatement(insertQuery)) {
                        insertStatement.setString(1, senderUUID.toString());
                        insertStatement.setString(2, targetUUID.toString());
                        insertStatement.executeUpdate();
                    }
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save friend request", exception);
        }
        pendingRequests.computeIfAbsent(targetUUID, uuid -> new HashSet<>()).add(senderUUID);
    }

    private void acceptFriendship(final UUID senderUUID, final UUID targetUUID) {
        final String updateQuery = "UPDATE friends SET status = 'ACCEPTED', accepted_at = CURRENT_TIMESTAMP WHERE player_uuid = ? AND friend_uuid = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(updateQuery)) {
            statement.setString(1, senderUUID.toString());
            statement.setString(2, targetUUID.toString());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to accept friendship", exception);
        }
    }

    private void removeFriendship(final UUID playerOne, final UUID playerTwo) {
        final String deleteQuery = "DELETE FROM friends WHERE (player_uuid = ? AND friend_uuid = ?) OR (player_uuid = ? AND friend_uuid = ?)";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(deleteQuery)) {
            statement.setString(1, playerOne.toString());
            statement.setString(2, playerTwo.toString());
            statement.setString(3, playerTwo.toString());
            statement.setString(4, playerOne.toString());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove friendship", exception);
        }
    }

    private Set<UUID> loadFriendsFromDatabase(final UUID playerUUID) {
        final Set<UUID> friends = new HashSet<>();
        final String query = "SELECT player_uuid, friend_uuid FROM friends WHERE (player_uuid = ? OR friend_uuid = ?) AND status = 'ACCEPTED'";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            statement.setString(2, playerUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID playerOne = UUID.fromString(resultSet.getString("player_uuid"));
                    final UUID playerTwo = UUID.fromString(resultSet.getString("friend_uuid"));
                    final UUID friendUUID = playerOne.equals(playerUUID) ? playerTwo : playerOne;
                    friends.add(friendUUID);
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load friends for " + playerUUID, exception);
        }
        return friends;
    }

    private Set<UUID> loadPendingRequests(final UUID targetUUID) {
        final Set<UUID> requests = new HashSet<>();
        final String query = "SELECT player_uuid FROM friends WHERE friend_uuid = ? AND status = 'PENDING'";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, targetUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requests.add(UUID.fromString(resultSet.getString("player_uuid")));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load pending requests for " + targetUUID, exception);
        }
        return requests;
    }

    private boolean hasMutualFriend(final UUID sender, final UUID target) {
        final Set<UUID> senderFriends = friendsCache.computeIfAbsent(sender, this::loadFriendsFromDatabase);
        final Set<UUID> targetFriends = friendsCache.computeIfAbsent(target, this::loadFriendsFromDatabase);
        for (final UUID friend : senderFriends) {
            if (targetFriends.contains(friend)) {
                return true;
            }
        }
        return false;
    }

    public FriendSettings getFriendSettings(final UUID uuid) {
        return settingsCache.computeIfAbsent(uuid, this::loadSettings);
    }

    private FriendSettings loadSettings(final UUID uuid) {
        final String query = "SELECT accept_requests, show_online_status, receive_notifications FROM friend_settings WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final AcceptMode acceptMode = AcceptMode.fromDatabase(resultSet.getString("accept_requests"));
                    final boolean showOnline = resultSet.getBoolean("show_online_status");
                    final boolean receiveNotifications = resultSet.getBoolean("receive_notifications");
                    return new FriendSettings(acceptMode, showOnline, receiveNotifications);
                }
            }
            insertDefaultSettings(uuid, connection);
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load friend settings for " + uuid, exception);
        }
        return new FriendSettings(AcceptMode.ALL, true, true);
    }

    private void insertDefaultSettings(final UUID uuid, final Connection connection) throws SQLException {
        final String insertQuery = "INSERT INTO friend_settings (player_uuid, accept_requests, show_online_status, receive_notifications) VALUES (?, 'ALL', 1, 1)";
        try (PreparedStatement statement = connection.prepareStatement(insertQuery)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    private void addToFriendsCache(final UUID source, final UUID friend) {
        friendsCache.computeIfAbsent(source, this::loadFriendsFromDatabase).add(friend);
    }

    private UUID getUuidByName(final String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        final Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            return player.getUniqueId();
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
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch UUID for name " + name, exception);
        }
        return null;
    }

    private String getNameByUuid(final UUID uuid) {
        final Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }
        final String query = "SELECT username FROM players WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("username");
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch username for UUID " + uuid, exception);
        }
        return uuid.toString();
    }

    private long getLastSeen(final UUID uuid) {
        final DatabaseManager.DatabaseType databaseType = databaseManager.getDatabaseType();
        final String query;
        if (databaseType == DatabaseManager.DatabaseType.MYSQL) {
            query = "SELECT UNIX_TIMESTAMP(last_join) AS last_seen FROM players WHERE uuid = ?";
        } else {
            query = "SELECT strftime('%s', last_join) AS last_seen FROM players WHERE uuid = ?";
        }
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("last_seen") * 1000L;
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get last seen for " + uuid, exception);
        }
        return System.currentTimeMillis();
    }
}
