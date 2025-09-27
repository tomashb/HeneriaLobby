package com.lobby.friends.listeners;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.friends.data.FriendData;
import com.lobby.friends.data.FriendRequest;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class FriendAddChatListener implements Listener {

    private static final String MODE_SEARCH_PLAYER_NAME = "search_player_name";

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Map<UUID, String> pendingRequests = new ConcurrentHashMap<>();

    public FriendAddChatListener(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.friendsManager = plugin.getFriendsManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final UUID playerUUID = player.getUniqueId();

        if (!pendingRequests.containsKey(playerUUID)) {
            return;
        }

        event.setCancelled(true);

        final String mode = pendingRequests.get(playerUUID);
        final String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("annuler") || input.equalsIgnoreCase("cancel")) {
            pendingRequests.remove(playerUUID);
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§c❌ Ajout d'ami annulé"));
            return;
        }

        pendingRequests.remove(playerUUID);

        if (MODE_SEARCH_PLAYER_NAME.equals(mode)) {
            handleAddByName(player, input);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§c❌ Mode d'ajout non reconnu !"));
    }

    private void handleAddByName(final Player player, final String targetName) {
        if (targetName.length() < 3 || targetName.length() > 16) {
            player.sendMessage("§c❌ Nom d'utilisateur invalide ! (3-16 caractères requis)");
            return;
        }

        if (!targetName.matches("[a-zA-Z0-9_]+")) {
            player.sendMessage("§c❌ Le nom ne peut contenir que des lettres, chiffres et _ !");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final UUID targetUUID = getPlayerUUID(targetName);

            if (targetUUID == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c❌ Joueur '" + targetName + "' introuvable !");
                    player.sendMessage("§7Vérifiez l'orthographe du nom d'utilisateur");
                });
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> processFriendRequest(player, targetUUID, targetName));
        });
    }

    private void processFriendRequest(final Player sender, final UUID targetUUID, final String targetName) {
        final UUID senderUUID = sender.getUniqueId();

        if (targetUUID.equals(senderUUID)) {
            sender.sendMessage("§c❌ Vous ne pouvez pas vous ajouter vous-même !");
            return;
        }

        friendsManager.getFriends(sender)
                .thenCompose(friends -> {
                    if (isAlreadyFriend(friends, targetUUID)) {
                        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c❌ Vous êtes déjà amis avec §e" + targetName + "§c !"));
                        return CompletableFuture.completedFuture(null);
                    }

                    return friendsManager.getPendingRequests(sender).thenCompose(requests -> {
                        if (hasIncomingRequest(requests, targetUUID)) {
                            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c❌ Une demande de §e" + targetName + "§c est déjà en attente !"));
                            return CompletableFuture.completedFuture(null);
                        }

                        return hasOutgoingRequest(sender, targetUUID).thenCompose(hasOutgoing -> {
                            if (hasOutgoing) {
                                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c❌ Vous avez déjà envoyé une demande à §e" + targetName + "§c !"));
                                return CompletableFuture.completedFuture(null);
                            }

                            return friendsManager.sendFriendRequest(sender, targetName, "Demande d'ami");
                        });
                    });
                })
                .thenAccept(success -> {
                    if (success == null) {
                        return;
                    }

                    if (success) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage("§a✅ Demande d'ami envoyée à §2" + targetName + "§a !");
                            sender.sendMessage("§7La demande expire dans §a7 jours");

                            final Player target = Bukkit.getPlayer(targetUUID);
                            if (target != null) {
                                target.sendMessage("§e📬 §6" + sender.getName() + "§e vous a envoyé une demande d'ami !");
                                target.sendMessage("§7Tapez §a/friend accept " + sender.getName() + "§7 pour accepter");
                                target.sendMessage("§7Ou ouvrez le menu amis §a/friends");
                                target.playSound(target.getLocation(), "entity.experience_orb.pickup", 1.0f, 1.2f);
                            }
                        });
                        return;
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§c❌ Erreur lors de l'envoi de la demande !");
                        sender.sendMessage("§7Veuillez réessayer plus tard");
                    });
                })
                .exceptionally(exception -> {
                    plugin.getLogger().severe("Erreur lors de l'envoi de demande d'ami: " + exception.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c❌ Erreur lors de l'envoi de la demande !"));
                    return null;
                });
    }

    public void enableAddMode(final Player player, final String mode) {
        final UUID playerUUID = player.getUniqueId();
        pendingRequests.put(playerUUID, mode);

        if (MODE_SEARCH_PLAYER_NAME.equals(mode)) {
            player.sendMessage("§e🔍 §6Tapez le nom exact du joueur à ajouter:");
            player.sendMessage("§7Exemple: §aSteve §7ou §aPlayer123");
            player.sendMessage("§7Tapez §c'annuler'§7 pour annuler");
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequests.containsKey(playerUUID)) {
                pendingRequests.remove(playerUUID);
                player.sendMessage("§c⏰ Temps d'attente écoulé - Ajout d'ami annulé");
            }
        }, 20L * 30);
    }

    private UUID getPlayerUUID(final String playerName) {
        final Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        final DatabaseManager databaseManager = plugin.getDatabaseManager();
        if (databaseManager != null) {
            final UUID databaseUuid = databaseManager.getPlayerUUID(playerName);
            if (databaseUuid != null) {
                return databaseUuid;
            }
        }

        try {
            return Bukkit.getOfflinePlayer(playerName).getUniqueId();
        } catch (final Exception exception) {
            plugin.getLogger().warning("Impossible de trouver l'UUID pour " + playerName + ": " + exception.getMessage());
            return null;
        }
    }

    public boolean isInAddMode(final Player player) {
        return pendingRequests.containsKey(player.getUniqueId());
    }

    public void cancelAddMode(final Player player) {
        if (pendingRequests.remove(player.getUniqueId()) != null) {
            player.sendMessage("§c❌ Ajout d'ami annulé");
        }
    }

    private boolean isAlreadyFriend(final List<FriendData> friends, final UUID targetUUID) {
        if (friends == null) {
            return false;
        }
        final String target = targetUUID.toString();
        return friends.stream().anyMatch(data -> target.equalsIgnoreCase(data.getUuid()));
    }

    private boolean hasIncomingRequest(final List<FriendRequest> requests, final UUID targetUUID) {
        if (requests == null) {
            return false;
        }
        final String target = targetUUID.toString();
        return requests.stream().anyMatch(request -> target.equalsIgnoreCase(request.getSenderUuid()));
    }

    private CompletableFuture<Boolean> hasOutgoingRequest(final Player sender, final UUID targetUUID) {
        final Player target = Bukkit.getPlayer(targetUUID);
        if (target == null) {
            return CompletableFuture.completedFuture(false);
        }

        return friendsManager.getPendingRequests(target).thenApply(requests -> {
            if (requests == null) {
                return false;
            }
            final String senderId = sender.getUniqueId().toString();
            return requests.stream().anyMatch(request -> senderId.equalsIgnoreCase(request.getSenderUuid()));
        });
    }
}
