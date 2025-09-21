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
import java.sql.ResultSetMetaData;
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
    private final Map<UUID, Set<UUID>> favoritesCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, FriendSettings> settingsCache = new ConcurrentHashMap<>();

    public FriendManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public void reload() {
        friendsCache.clear();
        favoritesCache.clear();
        pendingRequests.clear();
        settingsCache.clear();
    }

    public boolean sendFriendRequest(final Player sender, final String targetName) {
        final Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable ou hors ligne.");
            return false;
        }

        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage("§cVous ne pouvez pas vous ajouter vous-même !");
            return false;
        }

        if (areFriends(sender.getUniqueId(), target.getUniqueId())) {
            sender.sendMessage("§cVous êtes déjà amis avec " + target.getName() + " !");
            return false;
        }

        if (isBlocked(sender.getUniqueId(), target.getUniqueId())
                || isBlocked(target.getUniqueId(), sender.getUniqueId())) {
            sender.sendMessage("§cImpossible d'envoyer une demande : relation bloquée.");
            return false;
        }

        if (hasPendingRequest(sender.getUniqueId(), target.getUniqueId())) {
            sender.sendMessage("§cVous avez déjà envoyé une demande d'ami à " + target.getName() + " !");
            return false;
        }

        final FriendSettings settings = getFriendSettings(target.getUniqueId());
        if (settings.getAcceptRequests() == AcceptMode.NONE) {
            sender.sendMessage("§c" + target.getName() + " n'accepte pas les demandes d'amis.");
            return false;
        }

        if (settings.getAcceptRequests() == AcceptMode.FRIENDS_OF_FRIENDS && !hasMutualFriend(sender.getUniqueId(), target.getUniqueId())) {
            sender.sendMessage("§cVous devez avoir des amis en commun avec " + target.getName() + " pour envoyer une demande.");
            return false;
        }

        saveFriendRequest(sender.getUniqueId(), target.getUniqueId());
        target.sendMessage("§e" + sender.getName() + " §avous a envoyé une demande d'ami !");
        target.sendMessage("§7Tapez §a/friend accept " + sender.getName() + " §7pour accepter");
        target.sendMessage("§7ou §c/friend deny " + sender.getName() + " §7pour refuser");
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        return true;
    }

    public void onPlayerJoin(final UUID player) {
        getFriendSettings(player);
    }

    public void onPlayerQuit(final UUID player) {
        // Reserved for future cleanup hooks
    }

    public void acceptFriendRequest(final Player player, final String senderName) {
        final UUID senderUUID = getUuidByName(senderName);
        acceptFriendRequest(player, senderUUID, senderName);
    }

    public void acceptFriendRequest(final Player player, final UUID senderUuid) {
        final String senderName = resolveName(senderUuid);
        acceptFriendRequest(player, senderUuid, senderName);
    }

    public void denyFriendRequest(final Player player, final String senderName) {
        final UUID senderUUID = getUuidByName(senderName);
        denyFriendRequest(player, senderUUID, senderName);
    }

    public void denyFriendRequest(final Player player, final UUID senderUuid) {
        final String senderName = resolveName(senderUuid);
        denyFriendRequest(player, senderUuid, senderName);
    }

    private void acceptFriendRequest(final Player player, final UUID senderUuid, final String senderName) {
        if (senderUuid == null) {
            player.sendMessage("§cJoueur introuvable.");
            return;
        }
        if (!hasPendingRequest(senderUuid, player.getUniqueId())) {
            player.sendMessage("§cAucune demande d'ami de " + senderName + " trouvée.");
            return;
        }

        acceptFriendship(senderUuid, player.getUniqueId());

        addToFriendsCache(senderUuid, player.getUniqueId());
        addToFriendsCache(player.getUniqueId(), senderUuid);

        removePendingRequest(senderUuid, player.getUniqueId());

        player.sendMessage("§aVous êtes maintenant ami avec §6" + senderName + "§a !");

        final Player senderPlayer = Bukkit.getPlayer(senderUuid);
        if (senderPlayer != null) {
            senderPlayer.sendMessage("§6" + player.getName() + " §aa accepté votre demande d'ami !");
            senderPlayer.playSound(senderPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        final VelocityManager velocityManager = plugin.getVelocityManager();
        if (velocityManager != null) {
            velocityManager.broadcastFriendUpdate(player.getUniqueId(), "ACCEPT", senderUuid);
            velocityManager.broadcastFriendUpdate(senderUuid, "ACCEPT", player.getUniqueId());
        }
    }

    private void denyFriendRequest(final Player player, final UUID senderUuid, final String senderName) {
        if (senderUuid == null) {
            player.sendMessage("§cJoueur introuvable.");
            return;
        }
        if (!hasPendingRequest(senderUuid, player.getUniqueId())) {
            player.sendMessage("§cAucune demande d'ami de " + senderName + " trouvée.");
            return;
        }

        removeFriendship(senderUuid, player.getUniqueId());
        removePendingRequest(senderUuid, player.getUniqueId());

        player.sendMessage("§cVous avez refusé la demande d'ami de §6" + senderName + "§c.");

        final Player senderPlayer = Bukkit.getPlayer(senderUuid);
        if (senderPlayer != null) {
            senderPlayer.sendMessage("§c" + player.getName() + " a refusé votre demande d'ami.");
        }
    }

    private String resolveName(final UUID uuid) {
        if (uuid == null) {
            return "";
        }
        final Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        final String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
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
        removeFromCaches(player.getUniqueId(), targetUUID);
        removeFromCaches(targetUUID, player.getUniqueId());
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
        final String query = "SELECT friend_uuid, accepted_at, is_favorite FROM friends WHERE player_uuid = ? AND status = 'ACCEPTED'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID friendUUID = UUID.fromString(resultSet.getString("friend_uuid"));
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
                    final boolean favorite = resultSet.getBoolean("is_favorite");
                    friends.add(new FriendInfo(friendUUID, name, online, serverName, acceptedAt, lastSeen, favorite));
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
        final String query;
        if (databaseManager.getDatabaseType() == DatabaseManager.DatabaseType.MYSQL) {
            query = "INSERT INTO friends (player_uuid, friend_uuid, status, created_at, accepted_at, blocked_at, is_favorite) VALUES (?, ?, 'PENDING', CURRENT_TIMESTAMP, NULL, NULL, FALSE) ON DUPLICATE KEY UPDATE status = 'PENDING', created_at = CURRENT_TIMESTAMP, accepted_at = NULL, blocked_at = NULL, is_favorite = FALSE";
        } else {
            query = "INSERT INTO friends (player_uuid, friend_uuid, status, created_at, accepted_at, blocked_at, is_favorite) VALUES (?, ?, 'PENDING', CURRENT_TIMESTAMP, NULL, NULL, 0) ON CONFLICT(player_uuid, friend_uuid) DO UPDATE SET status = 'PENDING', created_at = CURRENT_TIMESTAMP, accepted_at = NULL, blocked_at = NULL, is_favorite = 0";
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, senderUUID.toString());
            statement.setString(2, targetUUID.toString());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save friend request", exception);
        }
        pendingRequests.computeIfAbsent(targetUUID, uuid -> new HashSet<>()).add(senderUUID);
    }

    private void acceptFriendship(final UUID senderUUID, final UUID targetUUID) {
        final String updateQuery = "UPDATE friends SET status = 'ACCEPTED', accepted_at = CURRENT_TIMESTAMP, blocked_at = NULL WHERE player_uuid = ? AND friend_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(updateQuery)) {
            statement.setString(1, senderUUID.toString());
            statement.setString(2, targetUUID.toString());
            statement.executeUpdate();
            ensureReciprocalFriendship(connection, targetUUID, senderUUID);
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to accept friendship", exception);
        }
    }

    private void ensureReciprocalFriendship(final Connection connection,
                                            final UUID ownerUUID,
                                            final UUID friendUUID) throws SQLException {
        final String query;
        if (databaseManager.getDatabaseType() == DatabaseManager.DatabaseType.MYSQL) {
            query = "INSERT INTO friends (player_uuid, friend_uuid, status, created_at, accepted_at, blocked_at, is_favorite) VALUES (?, ?, 'ACCEPTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, FALSE) ON DUPLICATE KEY UPDATE status = 'ACCEPTED', accepted_at = CURRENT_TIMESTAMP, blocked_at = NULL";
        } else {
            query = "INSERT INTO friends (player_uuid, friend_uuid, status, created_at, accepted_at, blocked_at, is_favorite) VALUES (?, ?, 'ACCEPTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, 0) ON CONFLICT(player_uuid, friend_uuid) DO UPDATE SET status = 'ACCEPTED', accepted_at = CURRENT_TIMESTAMP, blocked_at = NULL";
        }
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, ownerUUID.toString());
            statement.setString(2, friendUUID.toString());
            statement.executeUpdate();
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
        final Set<UUID> favorites = new HashSet<>();
        final String query = "SELECT friend_uuid, is_favorite FROM friends WHERE player_uuid = ? AND status = 'ACCEPTED'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID friendUUID = UUID.fromString(resultSet.getString("friend_uuid"));
                    friends.add(friendUUID);
                    if (resultSet.getBoolean("is_favorite")) {
                        favorites.add(friendUUID);
                    }
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load friends for " + playerUUID, exception);
        }
        favoritesCache.put(playerUUID, favorites);
        return friends;
    }

    private Set<UUID> loadFavoritesFromDatabase(final UUID playerUUID) {
        final Set<UUID> favorites = new HashSet<>();
        final String query = "SELECT friend_uuid FROM friends WHERE player_uuid = ? AND status = 'ACCEPTED' AND is_favorite = TRUE";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    favorites.add(UUID.fromString(resultSet.getString("friend_uuid")));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load favorite friends for " + playerUUID, exception);
        }
        return favorites;
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

    public boolean toggleRequestAcceptance(final UUID playerUuid) {
        if (playerUuid == null) {
            return true;
        }
        final FriendSettings current = getFriendSettings(playerUuid);
        final AcceptMode nextMode = current.getAcceptRequests() == AcceptMode.NONE ? AcceptMode.ALL : AcceptMode.NONE;
        final FriendSettings updated = new FriendSettings(nextMode, current.isShowOnlineStatus(),
                current.isAllowNotifications(), current.isAutoAcceptFavorites(),
                current.isAllowPrivateMessages(), current.getMaxFriends());
        updateSettings(playerUuid, updated);
        return nextMode != AcceptMode.NONE;
    }

    public boolean toggleNotifications(final UUID playerUuid) {
        if (playerUuid == null) {
            return true;
        }
        final FriendSettings current = getFriendSettings(playerUuid);
        final boolean allowNotifications = !current.isAllowNotifications();
        final FriendSettings updated = new FriendSettings(current.getAcceptRequests(), current.isShowOnlineStatus(),
                allowNotifications, current.isAutoAcceptFavorites(), current.isAllowPrivateMessages(),
                current.getMaxFriends());
        updateSettings(playerUuid, updated);
        return allowNotifications;
    }

    public boolean toggleVisibility(final UUID playerUuid) {
        if (playerUuid == null) {
            return true;
        }
        final FriendSettings current = getFriendSettings(playerUuid);
        final boolean showStatus = !current.isShowOnlineStatus();
        final FriendSettings updated = new FriendSettings(current.getAcceptRequests(), showStatus,
                current.isAllowNotifications(), current.isAutoAcceptFavorites(), current.isAllowPrivateMessages(),
                current.getMaxFriends());
        updateSettings(playerUuid, updated);
        return showStatus;
    }

    public boolean toggleAutoAcceptFavorites(final UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        final FriendSettings current = getFriendSettings(playerUuid);
        final boolean autoFavorites = !current.isAutoAcceptFavorites();
        final FriendSettings updated = new FriendSettings(current.getAcceptRequests(), current.isShowOnlineStatus(),
                current.isAllowNotifications(), autoFavorites, current.isAllowPrivateMessages(),
                current.getMaxFriends());
        updateSettings(playerUuid, updated);
        return autoFavorites;
    }

    public boolean togglePrivateMessages(final UUID playerUuid) {
        if (playerUuid == null) {
            return true;
        }
        final FriendSettings current = getFriendSettings(playerUuid);
        final boolean allowPrivateMessages = !current.isAllowPrivateMessages();
        final FriendSettings updated = new FriendSettings(current.getAcceptRequests(), current.isShowOnlineStatus(),
                current.isAllowNotifications(), current.isAutoAcceptFavorites(), allowPrivateMessages,
                current.getMaxFriends());
        updateSettings(playerUuid, updated);
        return allowPrivateMessages;
    }

    public void updateSettings(final UUID uuid, final FriendSettings settings) {
        final String query;
        if (databaseManager.getDatabaseType() == DatabaseManager.DatabaseType.MYSQL) {
            query = "INSERT INTO friend_settings (player_uuid, accept_requests, show_online_status, allow_notifications, receive_notifications, auto_accept_favorites, auto_accept_friends, allow_private_messages, max_friends) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE accept_requests = VALUES(accept_requests), show_online_status = VALUES(show_online_status), allow_notifications = VALUES(allow_notifications), receive_notifications = VALUES(receive_notifications), auto_accept_favorites = VALUES(auto_accept_favorites), auto_accept_friends = VALUES(auto_accept_friends), allow_private_messages = VALUES(allow_private_messages), max_friends = VALUES(max_friends)";
        } else {
            query = "INSERT INTO friend_settings (player_uuid, accept_requests, show_online_status, allow_notifications, receive_notifications, auto_accept_favorites, auto_accept_friends, allow_private_messages, max_friends) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET accept_requests = excluded.accept_requests, show_online_status = excluded.show_online_status, allow_notifications = excluded.allow_notifications, receive_notifications = excluded.receive_notifications, auto_accept_favorites = excluded.auto_accept_favorites, auto_accept_friends = excluded.auto_accept_friends, allow_private_messages = excluded.allow_private_messages, max_friends = excluded.max_friends";
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, settings.getAcceptRequests().toDatabase());
            statement.setBoolean(3, settings.isShowOnlineStatus());
            statement.setBoolean(4, settings.isAllowNotifications());
            statement.setBoolean(5, settings.isAllowNotifications());
            statement.setBoolean(6, settings.isAutoAcceptFavorites());
            statement.setBoolean(7, settings.isAutoAcceptFavorites());
            statement.setBoolean(8, settings.isAllowPrivateMessages());
            statement.setInt(9, settings.getMaxFriends());
            statement.executeUpdate();
            settingsCache.put(uuid, settings);
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update friend settings for " + uuid, exception);
        }
    }

    private FriendSettings loadSettings(final UUID uuid) {
        final String query = "SELECT * FROM friend_settings WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final ResultSetMetaData metaData = resultSet.getMetaData();
                    final AcceptMode acceptMode = AcceptMode.fromDatabase(resultSet.getString("accept_requests"));
                    final boolean showOnline = getBoolean(resultSet, metaData, "show_online_status", true);
                    final boolean allowNotifications = getBooleanWithFallback(resultSet, metaData,
                            "allow_notifications", "receive_notifications", true);
                    final boolean autoAcceptFavorites = getBooleanWithFallback(resultSet, metaData,
                            "auto_accept_favorites", "auto_accept_friends", false);
                    final boolean allowPrivateMessages = getBoolean(resultSet, metaData,
                            "allow_private_messages", true);
                    final int maxFriends = getIntWithDefault(resultSet, metaData, "max_friends", 100);
                    return new FriendSettings(acceptMode, showOnline, allowNotifications, autoAcceptFavorites,
                            allowPrivateMessages, maxFriends);
                }
            }
            insertDefaultSettings(uuid, connection);
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load friend settings for " + uuid, exception);
        }
        return new FriendSettings(AcceptMode.ALL, true, true, false, true, 100);
    }

    private void insertDefaultSettings(final UUID uuid, final Connection connection) throws SQLException {
        final String insertQuery = "INSERT INTO friend_settings (player_uuid, accept_requests, show_online_status, allow_notifications, receive_notifications, auto_accept_favorites, auto_accept_friends, allow_private_messages, max_friends) VALUES (?, 'ALL', 1, 1, 1, 0, 0, 1, 100)";
        try (PreparedStatement statement = connection.prepareStatement(insertQuery)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    private void addToFriendsCache(final UUID source, final UUID friend) {
        friendsCache.computeIfAbsent(source, this::loadFriendsFromDatabase).add(friend);
    }

    public List<UUID> getAllFriends(final UUID uuid) {
        return new ArrayList<>(friendsCache.computeIfAbsent(uuid, this::loadFriendsFromDatabase));
    }

    public List<UUID> getOnlineFriends(final UUID uuid) {
        final List<UUID> online = new ArrayList<>();
        for (final UUID friendUUID : friendsCache.computeIfAbsent(uuid, this::loadFriendsFromDatabase)) {
            final Player player = Bukkit.getPlayer(friendUUID);
            if (player != null && player.isOnline()) {
                online.add(friendUUID);
            }
        }
        return online;
    }

    public void refreshOnlineFriends(final UUID uuid) {
        if (uuid == null) {
            return;
        }
        final Set<UUID> refreshed = loadFriendsFromDatabase(uuid);
        friendsCache.put(uuid, refreshed);
    }

    public List<UUID> getFavoriteFriends(final UUID uuid) {
        return new ArrayList<>(favoritesCache.computeIfAbsent(uuid, this::loadFavoritesFromDatabase));
    }

    public boolean addToFavorites(final UUID playerUUID, final UUID friendUUID) {
        if (!areFriends(playerUUID, friendUUID)) {
            return false;
        }
        final String query = "UPDATE friends SET is_favorite = TRUE WHERE player_uuid = ? AND friend_uuid = ? AND status = 'ACCEPTED'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            statement.setString(2, friendUUID.toString());
            if (statement.executeUpdate() > 0) {
                favoritesCache.computeIfAbsent(playerUUID, this::loadFavoritesFromDatabase).add(friendUUID);
                return true;
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to add favorite friend for " + playerUUID, exception);
        }
        return false;
    }

    public boolean removeFromFavorites(final UUID playerUUID, final UUID friendUUID) {
        final String query = "UPDATE friends SET is_favorite = FALSE WHERE player_uuid = ? AND friend_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            statement.setString(2, friendUUID.toString());
            if (statement.executeUpdate() > 0) {
                favoritesCache.computeIfPresent(playerUUID, (uuid, set) -> {
                    set.remove(friendUUID);
                    return set;
                });
                return true;
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to remove favorite friend for " + playerUUID, exception);
        }
        return false;
    }

    public boolean blockPlayer(final UUID playerUUID, final UUID targetUUID) {
        if (playerUUID.equals(targetUUID)) {
            return false;
        }
        removeFriendship(playerUUID, targetUUID);
        removeFromCaches(playerUUID, targetUUID);
        removeFromCaches(targetUUID, playerUUID);
        removePendingRequest(playerUUID, targetUUID);
        removePendingRequest(targetUUID, playerUUID);

        final String query;
        if (databaseManager.getDatabaseType() == DatabaseManager.DatabaseType.MYSQL) {
            query = "INSERT INTO friends (player_uuid, friend_uuid, status, created_at, blocked_at, accepted_at, is_favorite) VALUES (?, ?, 'BLOCKED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, FALSE) ON DUPLICATE KEY UPDATE status = 'BLOCKED', blocked_at = CURRENT_TIMESTAMP, accepted_at = NULL, is_favorite = FALSE";
        } else {
            query = "INSERT INTO friends (player_uuid, friend_uuid, status, created_at, blocked_at, accepted_at, is_favorite) VALUES (?, ?, 'BLOCKED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, 0) ON CONFLICT(player_uuid, friend_uuid) DO UPDATE SET status = 'BLOCKED', blocked_at = CURRENT_TIMESTAMP, accepted_at = NULL, is_favorite = 0";
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            statement.setString(2, targetUUID.toString());
            statement.executeUpdate();
            return true;
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to block player " + targetUUID + " for " + playerUUID, exception);
        }
        return false;
    }

    public boolean unblockPlayer(final UUID playerUUID, final UUID targetUUID) {
        final String query = "DELETE FROM friends WHERE player_uuid = ? AND friend_uuid = ? AND status = 'BLOCKED'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            statement.setString(2, targetUUID.toString());
            return statement.executeUpdate() > 0;
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to unblock player " + targetUUID + " for " + playerUUID, exception);
        }
        return false;
    }

    public boolean isBlocked(final UUID playerUUID, final UUID targetUUID) {
        final String query = "SELECT 1 FROM friends WHERE player_uuid = ? AND friend_uuid = ? AND status = 'BLOCKED'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            statement.setString(2, targetUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to check block status between " + playerUUID + " and " + targetUUID, exception);
        }
        return false;
    }

    public List<FriendRequest> getPendingRequestsDetailed(final UUID playerUUID) {
        final List<FriendRequest> requests = new ArrayList<>();
        final String query = "SELECT player_uuid, friend_uuid, status, created_at FROM friends WHERE friend_uuid = ? AND status = 'PENDING'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID sender = UUID.fromString(resultSet.getString("player_uuid"));
                    final UUID target = UUID.fromString(resultSet.getString("friend_uuid"));
                    final Timestamp timestamp = resultSet.getTimestamp("created_at");
                    final FriendRequestStatus status = FriendRequestStatus.fromDatabase(resultSet.getString("status"));
                    requests.add(new FriendRequest(sender, target,
                            timestamp != null ? timestamp.getTime() : System.currentTimeMillis(), status));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to load detailed pending requests for " + playerUUID, exception);
        }
        return requests;
    }

    public List<FriendRequest> getSentRequestsDetailed(final UUID playerUUID) {
        final List<FriendRequest> requests = new ArrayList<>();
        final String query = "SELECT player_uuid, friend_uuid, status, created_at FROM friends WHERE player_uuid = ? AND status = 'PENDING'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID sender = UUID.fromString(resultSet.getString("player_uuid"));
                    final UUID target = UUID.fromString(resultSet.getString("friend_uuid"));
                    final Timestamp timestamp = resultSet.getTimestamp("created_at");
                    final FriendRequestStatus status = FriendRequestStatus.fromDatabase(resultSet.getString("status"));
                    requests.add(new FriendRequest(sender, target,
                            timestamp != null ? timestamp.getTime() : System.currentTimeMillis(), status));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to load detailed sent requests for " + playerUUID, exception);
        }
        return requests;
    }

    public List<UUID> getSentRequests(final UUID playerUUID) {
        final List<UUID> requests = new ArrayList<>();
        final String query = "SELECT friend_uuid FROM friends WHERE player_uuid = ? AND status = 'PENDING'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requests.add(UUID.fromString(resultSet.getString("friend_uuid")));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to load sent requests for " + playerUUID, exception);
        }
        return requests;
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

    private boolean getBoolean(final ResultSet resultSet, final ResultSetMetaData metaData,
                               final String column, final boolean defaultValue) throws SQLException {
        if (!hasColumn(metaData, column)) {
            return defaultValue;
        }
        final boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? defaultValue : value;
    }

    private boolean getBooleanWithFallback(final ResultSet resultSet, final ResultSetMetaData metaData,
                                           final String primaryColumn, final String fallbackColumn,
                                           final boolean defaultValue) throws SQLException {
        if (primaryColumn != null && hasColumn(metaData, primaryColumn)) {
            return getBoolean(resultSet, metaData, primaryColumn, defaultValue);
        }
        if (fallbackColumn != null && hasColumn(metaData, fallbackColumn)) {
            return getBoolean(resultSet, metaData, fallbackColumn, defaultValue);
        }
        return defaultValue;
    }

    private int getIntWithDefault(final ResultSet resultSet, final ResultSetMetaData metaData,
                                  final String column, final int defaultValue) throws SQLException {
        if (!hasColumn(metaData, column)) {
            return defaultValue;
        }
        final int value = resultSet.getInt(column);
        return resultSet.wasNull() ? defaultValue : value;
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

    private void removeFromCaches(final UUID owner, final UUID target) {
        friendsCache.computeIfPresent(owner, (uuid, set) -> {
            set.remove(target);
            return set;
        });
        favoritesCache.computeIfPresent(owner, (uuid, set) -> {
            set.remove(target);
            return set;
        });
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
