package com.lobby.friends.manager;

import com.lobby.LobbyPlugin;
import com.lobby.friends.data.FriendData;
import com.lobby.friends.data.FriendRequest;
import com.lobby.friends.data.FriendSettings;
import com.lobby.friends.database.FriendsDatabase;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central orchestrator for all friends-related operations. The manager handles
 * caching, delegates persistence to {@link FriendsDatabase} and ensures that
 * heavy operations never block the main thread.
 */
public class FriendsManager {

    private final Plugin plugin;
    private final FriendsDatabase database;
    private final Map<UUID, List<FriendData>> friendsCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<FriendRequest>> requestsCache = new ConcurrentHashMap<>();
    private final Map<UUID, FriendSettings> settingsCache = new ConcurrentHashMap<>();

    public FriendsManager(final Plugin plugin) {
        this.plugin = plugin;
        this.database = new FriendsDatabase(plugin);
        startCacheRefresh();
    }

    private void startCacheRefresh() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                refreshPlayerCache(online.getUniqueId());
            }
        }, 20L * 60, 20L * 60);
    }

    private void refreshPlayerCache(final UUID playerUuid) {
        database.getFriends(playerUuid.toString()).thenAccept(friends -> friendsCache.put(playerUuid, friends));
        database.getPendingRequests(playerUuid.toString()).thenAccept(requests -> requestsCache.put(playerUuid, requests));
        database.getFriendSettings(playerUuid.toString()).thenAccept(settings -> {
            if (settings != null) {
                settingsCache.put(playerUuid, settings);
            }
        });
    }

    // region Friends

    public CompletableFuture<List<FriendData>> getFriends(final Player player) {
        final UUID uuid = player.getUniqueId();
        final List<FriendData> cached = friendsCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return database.getFriends(uuid.toString()).thenApply(friends -> {
            friendsCache.put(uuid, friends);
            return friends;
        });
    }

    public CompletableFuture<Boolean> addFriend(final Player player, final Player friend) {
        final UUID playerUuid = player.getUniqueId();
        final UUID friendUuid = friend.getUniqueId();
        return database.addFriendship(playerUuid.toString(), friendUuid.toString()).thenApply(success -> {
            if (!success) {
                return false;
            }
            friendsCache.remove(playerUuid);
            friendsCache.remove(friendUuid);
            player.sendMessage("§a✓ Vous êtes maintenant ami avec " + friend.getName() + " !");
            friend.sendMessage("§a✓ Vous êtes maintenant ami avec " + player.getName() + " !");
            playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f);
            playSound(friend, Sound.ENTITY_PLAYER_LEVELUP, 1.0f);
            return true;
        });
    }

    public CompletableFuture<Boolean> removeFriend(final Player player, final String friendName) {
        final Player onlineFriend = Bukkit.getPlayerExact(friendName);
        if (onlineFriend != null) {
            return database.removeFriendship(player.getUniqueId().toString(), onlineFriend.getUniqueId().toString())
                    .thenApply(success -> {
                        if (!success) {
                            return false;
                        }
                        friendsCache.remove(player.getUniqueId());
                        friendsCache.remove(onlineFriend.getUniqueId());
                        player.sendMessage("§7Vous n'êtes plus ami avec " + onlineFriend.getName());
                        if (onlineFriend.isOnline()) {
                            onlineFriend.sendMessage("§7" + player.getName() + " vous a retiré de ses amis");
                        }
                        return true;
                    });
        }

        final UUID friendUuid = getUuidByName(friendName);
        if (friendUuid == null) {
            player.sendMessage("§cJoueur introuvable : " + friendName);
            return CompletableFuture.completedFuture(false);
        }

        return database.removeFriendship(player.getUniqueId().toString(), friendUuid.toString()).thenApply(success -> {
            if (!success) {
                return false;
            }
            friendsCache.remove(player.getUniqueId());
            friendsCache.remove(friendUuid);
            player.sendMessage("§7Vous n'êtes plus ami avec " + friendName);
            return true;
        });
    }

    /**
     * Removes a friendship relationship between two players using their UUIDs.
     * This helper is primarily used by systems such as the block manager which
     * operate outside the traditional command flow and therefore cannot rely on
     * {@link Player} instances being available on the main thread.
     *
     * @param playerUuid the UUID of the player initiating the removal
     * @param friendUuid the UUID of the friend to remove
     * @return a future completed with {@code true} if the friendship was removed
     */
    public CompletableFuture<Boolean> removeFriendship(final UUID playerUuid, final UUID friendUuid) {
        if (playerUuid == null || friendUuid == null) {
            return CompletableFuture.completedFuture(false);
        }

        return database.removeFriendship(playerUuid.toString(), friendUuid.toString()).thenApply(success -> {
            if (success) {
                friendsCache.remove(playerUuid);
                friendsCache.remove(friendUuid);
            }
            return success;
        });
    }

    public CompletableFuture<Boolean> toggleFavorite(final Player player, final String friendName) {
        return getFriends(player).thenCompose(friends -> {
            final FriendData friendData = friends.stream()
                    .filter(data -> data.getPlayerName().equalsIgnoreCase(friendName))
                    .findFirst()
                    .orElse(null);
            if (friendData == null) {
                player.sendMessage("§c" + friendName + " n'est pas dans votre liste d'amis");
                return CompletableFuture.completedFuture(false);
            }
            final boolean newStatus = !friendData.isFavorite();
            return database.setFavorite(player.getUniqueId().toString(), friendData.getUuid(), newStatus).thenApply(success -> {
                if (!success) {
                    return false;
                }
                friendsCache.remove(player.getUniqueId());
                if (newStatus) {
                    player.sendMessage("§e⭐ " + friendName + " a été ajouté à vos favoris !");
                    playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.5f);
                } else {
                    player.sendMessage("§7" + friendName + " a été retiré de vos favoris");
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f);
                }
                return true;
            });
        });
    }

    // region Settings

    public CompletableFuture<FriendSettings> getFriendSettings(final Player player) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        final UUID uuid = player.getUniqueId();
        final FriendSettings cached = settingsCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return database.getFriendSettings(uuid.toString()).thenApply(settings -> {
            final FriendSettings resolved = settings != null ? settings : FriendSettings.defaults(uuid.toString());
            settingsCache.put(uuid, resolved);
            return resolved;
        });
    }

    public CompletableFuture<Boolean> saveFriendSettings(final Player player, final FriendSettings settings) {
        if (player == null || settings == null) {
            return CompletableFuture.completedFuture(false);
        }
        final UUID uuid = player.getUniqueId();
        final String uuidString = uuid.toString();
        final FriendSettings payload = settings.getPlayerUuid().equals(uuidString)
                ? settings
                : new FriendSettings(uuidString, settings.getNotifications(), settings.getVisibility(),
                settings.getAutoRequests(), settings.isSoundsEnabled(), settings.getPrivateMessages(), settings.getTeleportation());
        return database.saveFriendSettings(payload).thenApply(success -> {
            if (success) {
                settingsCache.put(uuid, payload);
            }
            return success;
        });
    }

    public CompletableFuture<Boolean> resetFriendSettings(final Player player) {
        if (player == null) {
            return CompletableFuture.completedFuture(false);
        }
        final FriendSettings defaults = FriendSettings.defaults(player.getUniqueId().toString());
        return saveFriendSettings(player, defaults);
    }

    public int getCachedFriendsCount(final UUID playerUuid) {
        if (playerUuid == null) {
            return 0;
        }
        final List<FriendData> cached = friendsCache.get(playerUuid);
        return cached != null ? cached.size() : 0;
    }

    public int getCachedOnlineFriendsCount(final UUID playerUuid) {
        if (playerUuid == null) {
            return 0;
        }
        final List<FriendData> cached = friendsCache.get(playerUuid);
        if (cached == null || cached.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (FriendData data : cached) {
            if (data != null && data.isOnline()) {
                count++;
            }
        }
        return count;
    }

    public int getCachedPendingRequests(final UUID playerUuid) {
        if (playerUuid == null) {
            return 0;
        }
        final List<FriendRequest> cached = requestsCache.get(playerUuid);
        return cached != null ? cached.size() : 0;
    }

    // endregion

    // endregion

    // region Requests

    public CompletableFuture<List<FriendRequest>> getPendingRequests(final Player player) {
        final UUID uuid = player.getUniqueId();
        final List<FriendRequest> cached = requestsCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return database.getPendingRequests(uuid.toString()).thenApply(requests -> {
            requestsCache.put(uuid, requests);
            return requests;
        });
    }

    public CompletableFuture<Boolean> sendFriendRequest(final Player sender, final String targetName, final String message) {
        final Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable ou hors ligne : " + targetName);
            return CompletableFuture.completedFuture(false);
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage("§cVous ne pouvez pas vous ajouter vous-même !");
            return CompletableFuture.completedFuture(false);
        }
        return database.areFriends(sender.getUniqueId().toString(), target.getUniqueId().toString()).thenCompose(alreadyFriends -> {
            if (alreadyFriends) {
                sender.sendMessage("§6Vous êtes déjà ami avec " + targetName + " !");
                return CompletableFuture.completedFuture(false);
            }
            return database.sendFriendRequest(sender.getUniqueId().toString(), target.getUniqueId().toString(), message).thenApply(success -> {
                if (!success) {
                    sender.sendMessage("§cImpossible d'envoyer la demande (déjà envoyée ?)");
                    return false;
                }
                requestsCache.remove(target.getUniqueId());
                sender.sendMessage("§aDemande d'amitié envoyée à " + targetName + " !");
                playSound(sender, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f);
                target.sendMessage("§e📨 Nouvelle demande d'amitié de " + sender.getName() + " !");
                if (message != null && !message.trim().isEmpty()) {
                    target.sendMessage("§7Message: §f\"" + message + "\"");
                }
                playSound(target, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 2.0f);
                return true;
            });
        });
    }

    public CompletableFuture<Boolean> acceptFriendRequest(final Player player, final String senderName) {
        final UUID senderUuid = getUuidByName(senderName);
        if (senderUuid == null) {
            player.sendMessage("§cJoueur introuvable : " + senderName);
            return CompletableFuture.completedFuture(false);
        }
        return database.acceptFriendRequest(senderUuid.toString(), player.getUniqueId().toString()).thenApply(success -> {
            if (!success) {
                player.sendMessage("§cImpossible d'accepter la demande");
                return false;
            }
            requestsCache.remove(player.getUniqueId());
            friendsCache.remove(player.getUniqueId());
            friendsCache.remove(senderUuid);
            player.sendMessage("§a✓ Vous êtes maintenant ami avec " + senderName + " !");
            playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f);
            final Player sender = Bukkit.getPlayer(senderUuid);
            if (sender != null) {
                sender.sendMessage("§a✓ " + player.getName() + " a accepté votre demande d'amitié !");
                playSound(sender, Sound.ENTITY_PLAYER_LEVELUP, 1.0f);
            }
            if (plugin instanceof LobbyPlugin lobbyPlugin && lobbyPlugin.getMenuUpdateManager() != null) {
                Bukkit.getScheduler().runTask(lobbyPlugin, () -> {
                    lobbyPlugin.getMenuUpdateManager().forceUpdate(player);
                    if (sender != null) {
                        lobbyPlugin.getMenuUpdateManager().forceUpdate(sender);
                    }
                });
            }
            return true;
        });
    }

    public CompletableFuture<Boolean> rejectFriendRequest(final Player player, final String senderName) {
        final UUID senderUuid = getUuidByName(senderName);
        if (senderUuid == null) {
            player.sendMessage("§cJoueur introuvable : " + senderName);
            return CompletableFuture.completedFuture(false);
        }
        return database.rejectFriendRequest(senderUuid.toString(), player.getUniqueId().toString()).thenApply(success -> {
            if (!success) {
                player.sendMessage("§cImpossible de refuser la demande");
                return false;
            }
            requestsCache.remove(player.getUniqueId());
            player.sendMessage("§7Demande de " + senderName + " refusée");
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f);
            return true;
        });
    }

    // endregion

    public CompletableFuture<Boolean> areFriends(final Player playerOne, final Player playerTwo) {
        return database.areFriends(playerOne.getUniqueId().toString(), playerTwo.getUniqueId().toString());
    }

    public CompletableFuture<Integer> getFriendsCount(final Player player) {
        return getFriends(player).thenApply(List::size);
    }

    public CompletableFuture<Integer> getOnlineFriendsCount(final Player player) {
        return getFriends(player).thenApply(friends -> (int) friends.stream().filter(FriendData::isOnline).count());
    }

    public CompletableFuture<List<FriendData>> getFavorites(final Player player) {
        return getFriends(player).thenApply(friends -> friends.stream().filter(FriendData::isFavorite).collect(Collectors.toList()));
    }

    public void shutdown() {
        database.close();
        friendsCache.clear();
        requestsCache.clear();
    }

    private UUID getUuidByName(final String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        final Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return online.getUniqueId();
        }
        return Bukkit.getOfflinePlayer(playerName).getUniqueId();
    }

    private void playSound(final Player player, final Sound sound, final float pitch) {
        if (player == null || sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }

    private void playSound(final Player player, final Sound sound) {
        playSound(player, sound, 1.0f);
    }
}
